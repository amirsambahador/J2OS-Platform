package org.j2os.platform.resicord;

import org.j2os.platform.resicord.block.Block;
import org.j2os.platform.resicord.block.ErrorBlock;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicies;
import org.j2os.platform.resicord.exception.BlockNotInitializedException;
import org.j2os.platform.resicord.exception.NotFoundException;
import org.j2os.platform.resicord.exception.TimeoutExecutionException;
import org.j2os.platform.resicord.retry.RetryPolicies;
import org.j2os.platform.resicord.retry.RetryPolicy;
import org.j2os.platform.resicord.timelimit.TimeLimitPolicies;
import org.j2os.platform.resicord.timelimit.TimeLimitPolicy;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fluent, chainable API for running a unit of work with an optional combination of retry,
 * time-limited execution, and bulkhead (concurrency-isolated) execution, falling back to a
 * caller-supplied error handler on failure.
 * <p>
 * Typical usage:
 * <pre>{@code
 * T result = new Try<T>()
 *         .doWork(() -> someRiskyCall())
 *         .retry(3, 500)
 *         .timeLimit(2000)
 *         .bulkhead("my-pool")
 *         .onError(e -> fallbackValue)
 *         .get();
 * }</pre>
 * <p>
 * Retry, time-limit, and bulkhead policies can each be supplied either inline (a fresh policy
 * created just for this {@code Try}, via {@link #retry(int, long)} / {@link #timeLimit(long)})
 * or by name (via {@link #retry(String)} / {@link #timeLimit(String)} / {@link #bulkhead(String)},
 * resolved at {@link #get()} time from {@link RetryPolicies} / {@link TimeLimitPolicies} /
 * {@link BulkheadPolicies} — so pool/policy tuning can be changed centrally without touching call
 * sites). Any policy left unset behaves as disabled ({@link RetryPolicy#NONE} /
 * {@link TimeLimitPolicy#NONE} / no bulkhead).
 * <p>
 * A single {@code Try} instance is not thread-safe and is not meant to be reused concurrently
 * from multiple threads; build and call {@link #get()} on it from one thread at a time.
 *
 * @param <T> the type of value produced by the work block
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class Try<T> {

    /**
     * Upper bound on the number of threads the shared {@link #timeLimitWatcherPool} may create,
     * across every {@code Try} instance using {@link #timeLimit}.
     */
    private static final int MAX_TIME_LIMIT_WATCHER_THREADS = 2000;

    /**
     * Shared, class-wide executor used to run the work block whenever a time limit is in effect
     * (see {@link #withTimeLimit(long)}), so the calling thread can wait on a {@link Future} with
     * a timeout instead of blocking indefinitely. Backed by a {@link SynchronousQueue} with
     * {@link ThreadPoolExecutor.AbortPolicy}, so once {@link #MAX_TIME_LIMIT_WATCHER_THREADS}
     * threads are all busy, a further submission fails fast (throwing
     * {@link RejectedExecutionException}) rather than queuing unboundedly.
     */
    private static final ExecutorService timeLimitWatcherPool = new ThreadPoolExecutor(
            0, MAX_TIME_LIMIT_WATCHER_THREADS,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            daemonThreadFactory("resicord-timelimit-watcher"),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * The unit of work to run, set via the constructor or {@link #doWork(Block)}.
     */
    private Block<T> block;

    /**
     * Fallback invoked with the final failure instead of propagating it, if set via {@link #onError(ErrorBlock)}.
     */
    private ErrorBlock<T> catchHandler;

    /**
     * A retry policy created just for this instance via {@link #retry(int, long)}, or null if a named policy is used instead.
     */
    private RetryPolicy inlineRetryPolicy;

    /**
     * The name of a retry policy to resolve from {@link RetryPolicies} at {@link #get()} time, set via {@link #retry(String)}.
     */
    private String retryPolicyName;

    /**
     * A time-limit policy created just for this instance via {@link #timeLimit(long)}, or null if a named policy is used instead.
     */
    private TimeLimitPolicy inlineTimeLimitPolicy;

    /**
     * The name of a time-limit policy to resolve from {@link TimeLimitPolicies} at {@link #get()} time, set via {@link #timeLimit(String)}.
     */
    private String timeLimitPolicyName;

    /**
     * The name of a bulkhead pool to run the work through, set via {@link #bulkhead(String)}; null means no bulkhead.
     */
    private String bulkheadName;

    /**
     * Creates an empty {@code Try} with no work block set. {@link #doWork(Block)} must be called
     * before {@link #get()}.
     */
    public Try() {
    }

    /**
     * Creates a {@code Try} wrapping the given unit of work.
     *
     * @param block the work to run
     */
    public Try(Block<T> block) {
        this.block = block;
    }

    /**
     * Sleeps for the given duration.
     *
     * @param millis the duration to sleep, in milliseconds; non-positive returns immediately
     * @return true if the full sleep completed; false if interrupted partway through (in which
     *         case the current thread's interrupt flag has been restored, and the caller should
     *         not begin another retry attempt)
     */
    private static boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Builds a {@link ThreadFactory} that names its threads {@code prefix-N} (N incrementing from
     * 1) and marks them as daemon threads, so they never prevent JVM shutdown.
     *
     * @param prefix the thread name prefix
     * @return the thread factory
     */
    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Sets (or replaces) the unit of work to run.
     *
     * @param block the work to run
     * @return this instance, for chaining
     */
    public Try<T> doWork(Block<T> block) {
        this.block = block;
        return this;
    }

    /**
     * Sets a fallback to invoke with the final failure instead of propagating it.
     *
     * @param catchHandler the error handler
     * @return this instance, for chaining
     */
    public Try<T> onError(ErrorBlock<T> catchHandler) {
        this.catchHandler = catchHandler;
        return this;
    }

    /**
     * Enables retry with an inline policy created just for this instance.
     *
     * @param maxAttempts  the maximum number of attempts (including the first), must be >= 1
     * @param delayMillis  the delay between attempts, in milliseconds, must be >= 0
     * @return this instance, for chaining
     */
    public Try<T> retry(int maxAttempts, long delayMillis) {
        this.inlineRetryPolicy = new RetryPolicy(maxAttempts, delayMillis);
        this.retryPolicyName = null;
        return this;
    }

    /**
     * Enables retry using a named policy, resolved from {@link RetryPolicies} at {@link #get()} time.
     *
     * @param policyName the name of a policy previously registered with {@link RetryPolicies#define}
     * @return this instance, for chaining
     * @throws NullPointerException if {@code policyName} is null
     */
    public Try<T> retry(String policyName) {
        this.retryPolicyName = Objects.requireNonNull(policyName, "policyName");
        this.inlineRetryPolicy = null;
        return this;
    }

    /**
     * Enables a time limit with an inline policy created just for this instance.
     *
     * @param millis the time limit, in milliseconds; must be >= 0 (0 disables the limit, per {@link TimeLimitPolicy#isEnabled()})
     * @return this instance, for chaining
     */
    public Try<T> timeLimit(long millis) {
        this.inlineTimeLimitPolicy = new TimeLimitPolicy(millis);
        this.timeLimitPolicyName = null;
        return this;
    }

    /**
     * Enables a time limit using a named policy, resolved from {@link TimeLimitPolicies} at {@link #get()} time.
     *
     * @param policyName the name of a policy previously registered with {@link TimeLimitPolicies#define}
     * @return this instance, for chaining
     * @throws NullPointerException if {@code policyName} is null
     */
    public Try<T> timeLimit(String policyName) {
        this.timeLimitPolicyName = Objects.requireNonNull(policyName, "policyName");
        this.inlineTimeLimitPolicy = null;
        return this;
    }

    /**
     * Routes execution through a named bulkhead pool, resolved from {@link BulkheadPolicies} at
     * {@link #get()} time. Unlike retry/time-limit, there is no inline overload — bulkhead pools
     * are always named, since they represent a shared, long-lived resource (a thread pool +
     * semaphore) rather than a per-call setting.
     *
     * @param policyName the name of a bulkhead previously registered with {@link BulkheadPolicies#define}
     * @return this instance, for chaining
     * @throws NullPointerException if {@code policyName} is null
     */
    public Try<T> bulkhead(String policyName) {
        this.bulkheadName = Objects.requireNonNull(policyName, "policyName");
        return this;
    }

    /**
     * Runs the configured work block, applying retry, time-limit, and bulkhead behavior as
     * configured, and returns its result.
     * <p>
     * On failure, the outcome depends on how many attempts remain and on {@link #catchHandler}:
     * <ul>
     *   <li>{@link NotFoundException} (a named retry/time-limit/bulkhead policy that isn't
     *       registered) always propagates immediately, without retry and without going through
     *       {@link #catchHandler} — it signals a configuration mistake, not a transient failure.</li>
     *   <li>An {@link InterruptedException} (this thread itself was interrupted, not the work it
     *       delegates to a watcher thread) is treated as non-transient: it is never retried, the
     *       thread's interrupt flag is restored, and the fallback (or a rethrow) happens immediately.</li>
     *   <li>Any other exception is retried until {@link RetryPolicy#maxAttempts()} is reached (or
     *       until a retry-delay sleep is itself interrupted), after which the fallback (or a
     *       rethrow) happens.</li>
     * </ul>
     * If {@link #catchHandler} is set, its result is returned instead of throwing. Otherwise the
     * failure is rethrown as-is if it's already a {@link RuntimeException}, or wrapped in one
     * (see {@link #throwRuntime(Throwable)}) if not.
     *
     * @return the result of the work block, or of {@link #catchHandler} if the work ultimately failed
     * @throws BlockNotInitializedException if no work block has been set via the constructor or {@link #doWork(Block)}
     * @throws NotFoundException            if a named retry/time-limit/bulkhead policy isn't registered
     * @throws RuntimeException             the final failure (or {@link #catchHandler}'s own exception), if {@link #catchHandler} isn't set or doesn't handle it
     */
    public T get() {
        if (block == null) {
            throw new BlockNotInitializedException();
        }

        RetryPolicy retry = resolveRetryPolicy();
        int attempts = 0;
        while (true) {
            try {
                attempts++;
                return executeOnce();
            } catch (NotFoundException e) {
                throw e;
            } catch (InterruptedException e) {
                // Cancellation/interruption is not a transient failure - must not be retried.
                Thread.currentThread().interrupt();
                return catchHandler != null ? catchHandler.body(e) : throwRuntime(e);
            } catch (Exception e) {
                if (attempts >= retry.maxAttempts()) {
                    return catchHandler != null ? catchHandler.body(e) : throwRuntime(e);
                }
                if (!sleep(retry.delayMillis())) {
                    // The sleep between attempts was itself interrupted - stop here.
                    return catchHandler != null ? catchHandler.body(e) : throwRuntime(e);
                }
            }
        }
    }

    /**
     * Runs the work block exactly once, applying the resolved time-limit and bulkhead policies
     * (but not retry — retry is handled by the loop in {@link #get()}).
     *
     * @return the result of the work block
     * @throws Exception whatever the work block itself throws, or a {@link TimeoutExecutionException}
     *                    / {@link org.j2os.platform.resicord.exception.BulkheadRejectedExecutionException}
     *                    if the time limit or bulkhead is exceeded
     */
    private T executeOnce() throws Exception {
        TimeLimitPolicy timeLimit = resolveTimeLimitPolicy();
        Callable<T> task = timeLimit.isEnabled() ? withTimeLimit(timeLimit.millis()) : block::body;

        return bulkheadName != null ? BulkheadPolicies.execute(bulkheadName, task) : task.call();
    }

    /**
     * Wraps the work block so it runs on {@link #timeLimitWatcherPool} and is bounded by the
     * given time limit: the calling thread waits on a {@link Future} with a timeout instead of
     * running the work directly, so a hung work block can't block the caller forever.
     *
     * @param millis the time limit, in milliseconds
     * @return a {@link Callable} that runs the work block under the given time limit
     */
    private Callable<T> withTimeLimit(long millis) {
        return () -> {
            Future<T> future = timeLimitWatcherPool.submit(block::body);
            try {
                return future.get(millis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutExecutionException(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                throw (cause instanceof Exception ex) ? ex : e;
            } catch (InterruptedException e) {
                // This thread (the one waiting for the result) was interrupted - also cancel the
                // task still running on timeLimitWatcherPool, otherwise it keeps running in the background.
                future.cancel(true);
                throw e;
            }
        };
    }

    /**
     * Resolves the retry policy to use for this call, preferring a named policy (resolved from
     * {@link RetryPolicies}) over an inline one, and falling back to {@link RetryPolicy#NONE} if
     * neither was configured.
     *
     * @return the resolved retry policy
     * @throws NotFoundException if a named policy was configured but isn't registered
     */
    private RetryPolicy resolveRetryPolicy() {
        if (retryPolicyName != null) {
            return RetryPolicies.get(retryPolicyName);
        }
        return inlineRetryPolicy != null ? inlineRetryPolicy : RetryPolicy.NONE;
    }

    /**
     * Resolves the time-limit policy to use for this call, preferring a named policy (resolved
     * from {@link TimeLimitPolicies}) over an inline one, and falling back to
     * {@link TimeLimitPolicy#NONE} if neither was configured.
     *
     * @return the resolved time-limit policy
     * @throws NotFoundException if a named policy was configured but isn't registered
     */
    private TimeLimitPolicy resolveTimeLimitPolicy() {
        if (timeLimitPolicyName != null) {
            return TimeLimitPolicies.get(timeLimitPolicyName);
        }
        return inlineTimeLimitPolicy != null ? inlineTimeLimitPolicy : TimeLimitPolicy.NONE;
    }

    /**
     * Rethrows a failure as an unchecked exception: as-is if it's already a
     * {@link RuntimeException}, or wrapped in a new {@link RuntimeException} otherwise.
     *
     * @param e the failure to rethrow
     * @return never returns normally (the {@code T} return type only lets this method be used
     *         directly in a {@code return} statement at the call sites above)
     * @throws RuntimeException always
     */
    private T throwRuntime(Throwable e) {
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(e);
    }
}