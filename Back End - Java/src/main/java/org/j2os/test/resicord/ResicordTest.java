package org.j2os.test.resicord;

import org.j2os.platform.resicord.Try;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicies;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicy;
import org.j2os.platform.resicord.exception.BlockNotInitializedException;
import org.j2os.platform.resicord.exception.BulkheadRejectedExecutionException;
import org.j2os.platform.resicord.exception.NotFoundException;
import org.j2os.platform.resicord.exception.TimeoutExecutionException;
import org.j2os.platform.resicord.retry.RetryPolicies;
import org.j2os.platform.resicord.retry.RetryPolicy;
import org.j2os.platform.resicord.timelimit.TimeLimitPolicies;
import org.j2os.platform.resicord.timelimit.TimeLimitPolicy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone, dependency-free test suite for the {@code org.j2os.platform.resicord} library
 * ({@link Try}, {@link RetryPolicies}, {@link TimeLimitPolicies}, {@link BulkheadPolicies}).
 * <p>
 * Like {@code ValidatorTest} in the jvalidation project, this class intentionally does <b>not</b>
 * use JUnit or any other testing framework: it is a plain Java class with a {@code main} method
 * that runs every test case sequentially, prints a PASS/FAIL line for each one, and prints a
 * final summary. Run it directly:
 * <pre>{@code
 * javac -d out $(find src/org/j2os/platform -name "*.java") src/org/j2os/test/resicord/ResicordTest.java
 * java -cp out org.j2os.test.resicord.ResicordTest
 * }</pre>
 * A non-zero process exit code indicates at least one failed test.
 * <p>
 * The suite is organized in two halves: single-threaded behavioral tests (retry counting,
 * fallback wiring, error classification, policy resolution) followed by a heavier concurrency
 * section that drives real thread pools and shared static registries under contention -
 * matching the depth of stress-testing already done on this library's earlier v2.x iterations
 * (see the project's prior ExampleXxxChecks suite), but rewritten for the v2.3 registry-based
 * architecture (BulkheadPolicies/RetryPolicies/TimeLimitPolicies + BulkheadExecutor).
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ResicordTest {

    private static int passedCount = 0;
    private static int failedCount = 0;

    public static void main(String[] args) {
        System.out.println("=== Resicord test suite ===");

        // Basic retry behavior
        testRetrySucceedsOnFirstAttempt();
        testRetryExhaustsAllAttemptsThenFallsBackToOnError();
        testRetryStopsAsSoonAsATrySucceeds();
        testRetryWithoutOnErrorRethrowsFinalFailure();
        testNamedRetryPolicyIsResolvedAtGetTime();
        testNamedRetryPolicyChangeIsPickedUpByFutureCalls();

        // Time limit behavior
        testTimeLimitPassesFastTask();
        testTimeLimitTimesOutSlowTaskAndCancelsIt();
        testTimeLimitDisabledWhenMillisIsZero();
        testNamedTimeLimitPolicyIsResolvedAtGetTime();

        // Bulkhead behavior (registry-level, single-threaded)
        testBulkheadDefineAndCurrentConfig();
        testBulkheadReconfigureAppliesInPlace();
        testBulkheadListAllReflectsDefinedPools();
        testBulkheadRemoveMakesNameUnusable();
        testBulkheadExecuteRunsTaskAndReturnsResult();

        // Error classification / edge cases
        testGetWithoutDoWorkThrowsBlockNotInitialized();
        testNamedRetryPolicyNotFoundPropagatesImmediatelyWithoutRetryOrOnError();
        testNamedTimeLimitPolicyNotFoundPropagatesImmediately();
        testNamedBulkheadNotFoundPropagatesImmediately();
        testErrorIsNeverRetriedAndPropagatesUnwrapped();
        testErrorThroughBulkheadPropagatesUnwrapped();
        testErrorThroughTimeLimitPropagatesUnwrapped();
        testInterruptedExceptionDuringRetryDelayIsNotRetriedAndRestoresInterruptFlag();
        testOnErrorReceivesTheActualFailureType();
        testCombinedRetryTimeLimitBulkheadFallsBackOnRepeatedTimeout();

        // Input validation
        testInvalidBulkheadPolicyConstructorArgumentsThrow();
        testInvalidRetryPolicyConstructorArgumentsThrow();
        testInvalidTimeLimitPolicyConstructorArgumentsThrow();
        testTryRejectsNullPolicyNames();

        // Pool lifecycle
        testBulkheadRemoveDuringInFlightExecutionLetsInFlightWorkFinish();
        testRepeatedDefineRemoveCyclesDoNotLeakBulkheadThreads();
        testTimeLimitWatcherPoolHandlesHeavyConcurrentUsageWithoutFailures();

        // Heavy concurrency
        testBulkheadEnforcesConcurrencyLimitUnderRealContention();
        testBulkheadRejectsBeyondTotalCapacityAfterMaxWait();
        testBulkheadReconfigureUnderLoadNeverLosesOrDuplicatesWork();
        testConcurrentRetryPoliciesRegistryNeverCorruptsUnderHeavyReadWriteContention();
        testConcurrentTimeLimitPoliciesRegistryNeverCorruptsUnderHeavyReadWriteContention();
        testConcurrentBulkheadDefineIsIdempotentUnderRace();
        testSoakRepeatedDefineRaceAcrossManyRoundsNeverCorruptsFinalState();
        testManyIndependentBulkheadsRunFullyInParallelNotSerialized();
        testHighConcurrencyMixedWorkloadAcrossAllThreeConcerns();
        testBulkheadSemaphoreNeverLeaksPermitsAcrossMixedSuccessFailureAndRejection();
        testConcurrentInterruptionOfWaitingCallersDoesNotWedgeTheBulkhead();

        System.out.println();
        System.out.println("=== Summary: " + passedCount + " passed, " + failedCount + " failed ===");
        if (failedCount > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Basic retry behavior
    // ------------------------------------------------------------------

    private static void testRetrySucceedsOnFirstAttempt() {
        String testName = "testRetrySucceedsOnFirstAttempt";
        try {
            AtomicInteger calls = new AtomicInteger();
            String result = new Try<String>()
                    .doWork(() -> {
                        calls.incrementAndGet();
                        return "ok";
                    })
                    .retry(3, 10)
                    .get();
            assertEquals(testName, "ok", result);
            assertEquals(testName, 1, calls.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRetryExhaustsAllAttemptsThenFallsBackToOnError() {
        String testName = "testRetryExhaustsAllAttemptsThenFallsBackToOnError";
        try {
            AtomicInteger calls = new AtomicInteger();
            String result = new Try<String>()
                    .doWork(() -> {
                        calls.incrementAndGet();
                        throw new RuntimeException("always fails");
                    })
                    .retry(4, 5)
                    .onError(e -> "fallback")
                    .get();
            assertEquals(testName, "fallback", result);
            assertEquals(testName, 4, calls.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRetryStopsAsSoonAsATrySucceeds() {
        String testName = "testRetryStopsAsSoonAsATrySucceeds";
        try {
            AtomicInteger calls = new AtomicInteger();
            String result = new Try<String>()
                    .doWork(() -> {
                        if (calls.incrementAndGet() < 3) {
                            throw new RuntimeException("not yet");
                        }
                        return "third time's the charm";
                    })
                    .retry(10, 1)
                    .get();
            assertEquals(testName, "third time's the charm", result);
            assertEquals(testName, 3, calls.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testRetryWithoutOnErrorRethrowsFinalFailure() {
        String testName = "testRetryWithoutOnErrorRethrowsFinalFailure";
        try {
            new Try<String>()
                    .doWork(() -> {
                        throw new IllegalStateException("boom");
                    })
                    .retry(2, 1)
                    .get();
            fail(testName, "Expected RuntimeException wrapping IllegalStateException but none was thrown");
        } catch (RuntimeException expected) {
            assertTrue(testName, expected.getCause() instanceof IllegalStateException || expected instanceof IllegalStateException,
                    "Expected the failure to be (or wrap) IllegalStateException, got " + expected);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception type: " + unexpectedException);
        }
    }

    private static void testNamedRetryPolicyIsResolvedAtGetTime() {
        String testName = "testNamedRetryPolicyIsResolvedAtGetTime";
        try {
            RetryPolicies.define("test-retry-basic", new RetryPolicy(5, 1));
            AtomicInteger calls = new AtomicInteger();
            String result = new Try<String>()
                    .doWork(() -> {
                        if (calls.incrementAndGet() < 5) {
                            throw new RuntimeException("not yet");
                        }
                        return "done";
                    })
                    .retry("test-retry-basic")
                    .get();
            assertEquals(testName, "done", result);
            assertEquals(testName, 5, calls.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            RetryPolicies.remove("test-retry-basic");
        }
    }

    private static void testNamedRetryPolicyChangeIsPickedUpByFutureCalls() {
        String testName = "testNamedRetryPolicyChangeIsPickedUpByFutureCalls";
        try {
            RetryPolicies.define("test-retry-mutable", new RetryPolicy(1, 0));
            AtomicInteger callsWithOneAttempt = new AtomicInteger();
            String firstResult = new Try<String>()
                    .doWork(() -> {
                        callsWithOneAttempt.incrementAndGet();
                        throw new RuntimeException("always fails");
                    })
                    .retry("test-retry-mutable")
                    .onError(e -> "fallback-1")
                    .get();
            assertEquals(testName, "fallback-1", firstResult);
            assertEquals(testName, 1, callsWithOneAttempt.get());

            // Redefine the same name with a higher attempt count - a call made *after* this
            // change should pick up the new policy, proving resolution happens at get() time.
            RetryPolicies.define("test-retry-mutable", new RetryPolicy(3, 0));
            AtomicInteger callsWithThreeAttempts = new AtomicInteger();
            String secondResult = new Try<String>()
                    .doWork(() -> {
                        callsWithThreeAttempts.incrementAndGet();
                        throw new RuntimeException("always fails");
                    })
                    .retry("test-retry-mutable")
                    .onError(e -> "fallback-2")
                    .get();
            assertEquals(testName, "fallback-2", secondResult);
            assertEquals(testName, 3, callsWithThreeAttempts.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            RetryPolicies.remove("test-retry-mutable");
        }
    }

    // ------------------------------------------------------------------
    // Time limit behavior
    // ------------------------------------------------------------------

    private static void testTimeLimitPassesFastTask() {
        String testName = "testTimeLimitPassesFastTask";
        try {
            String result = new Try<String>()
                    .doWork(() -> "fast")
                    .timeLimit(2000)
                    .get();
            assertEquals(testName, "fast", result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testTimeLimitTimesOutSlowTaskAndCancelsIt() {
        String testName = "testTimeLimitTimesOutSlowTaskAndCancelsIt";
        try {
            AtomicBoolean sleepCompletedNormally = new AtomicBoolean(false);
            String result = new Try<String>()
                    .doWork(() -> {
                        Thread.sleep(3000);
                        sleepCompletedNormally.set(true);
                        return "too slow";
                    })
                    .timeLimit(200)
                    .onError(e -> e instanceof TimeoutExecutionException ? "timed-out" : "wrong-error")
                    .get();
            assertEquals(testName, "timed-out", result);
            // Give the cancelled background task a moment; it must never have completed normally.
            Thread.sleep(3200);
            assertFalse(testName, sleepCompletedNormally.get(),
                    "Expected the timed-out task to be cancelled, not run to completion");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testTimeLimitDisabledWhenMillisIsZero() {
        String testName = "testTimeLimitDisabledWhenMillisIsZero";
        try {
            // timeLimit(0) means "disabled", so a task slower than a typical test timeout
            // should still simply run to completion rather than time out.
            String result = new Try<String>()
                    .doWork(() -> {
                        Thread.sleep(50);
                        return "ran fine";
                    })
                    .timeLimit(0)
                    .get();
            assertEquals(testName, "ran fine", result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testNamedTimeLimitPolicyIsResolvedAtGetTime() {
        String testName = "testNamedTimeLimitPolicyIsResolvedAtGetTime";
        try {
            TimeLimitPolicies.define("test-timelimit-basic", new TimeLimitPolicy(200));
            String result = new Try<String>()
                    .doWork(() -> {
                        Thread.sleep(500);
                        return "too slow";
                    })
                    .timeLimit("test-timelimit-basic")
                    .onError(e -> "timed-out")
                    .get();
            assertEquals(testName, "timed-out", result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            TimeLimitPolicies.remove("test-timelimit-basic");
        }
    }

    // ------------------------------------------------------------------
    // Bulkhead behavior (registry-level, single-threaded)
    // ------------------------------------------------------------------

    private static void testBulkheadDefineAndCurrentConfig() {
        String testName = "testBulkheadDefineAndCurrentConfig";
        try {
            BulkheadPolicies.define("test-bh-basic", new BulkheadPolicy(2, 3, 500));
            BulkheadPolicy config = BulkheadPolicies.currentConfig("test-bh-basic");
            assertEquals(testName, 2, config.maxConcurrentThreads());
            assertEquals(testName, 3, config.maxQueueSize());
            assertEquals(testName, 500L, config.maxWaitMillis());
            assertEquals(testName, 5, config.totalCapacity());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove("test-bh-basic");
        }
    }

    private static void testBulkheadReconfigureAppliesInPlace() {
        String testName = "testBulkheadReconfigureAppliesInPlace";
        try {
            BulkheadPolicies.define("test-bh-reconfig", new BulkheadPolicy(1, 1, 100));
            BulkheadPolicies.reconfigure("test-bh-reconfig", new BulkheadPolicy(4, 4, 999));
            BulkheadPolicy config = BulkheadPolicies.currentConfig("test-bh-reconfig");
            assertEquals(testName, 4, config.maxConcurrentThreads());
            assertEquals(testName, 4, config.maxQueueSize());
            assertEquals(testName, 999L, config.maxWaitMillis());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove("test-bh-reconfig");
        }
    }

    private static void testBulkheadListAllReflectsDefinedPools() {
        String testName = "testBulkheadListAllReflectsDefinedPools";
        try {
            BulkheadPolicies.define("test-bh-list-a", new BulkheadPolicy(1, 0, 100));
            BulkheadPolicies.define("test-bh-list-b", new BulkheadPolicy(1, 0, 100));
            Map<String, BulkheadPolicy> all = BulkheadPolicies.listAll();
            assertTrue(testName, all.containsKey("test-bh-list-a") && all.containsKey("test-bh-list-b"),
                    "Expected listAll() to contain both defined pool names, got keys: " + all.keySet());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove("test-bh-list-a");
            BulkheadPolicies.remove("test-bh-list-b");
        }
    }

    private static void testBulkheadRemoveMakesNameUnusable() {
        String testName = "testBulkheadRemoveMakesNameUnusable";
        try {
            BulkheadPolicies.define("test-bh-remove", new BulkheadPolicy(1, 0, 100));
            BulkheadPolicies.remove("test-bh-remove");
            try {
                BulkheadPolicies.currentConfig("test-bh-remove");
                fail(testName, "Expected NotFoundException after remove() but none was thrown");
            } catch (NotFoundException expected) {
                pass(testName);
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testBulkheadExecuteRunsTaskAndReturnsResult() {
        String testName = "testBulkheadExecuteRunsTaskAndReturnsResult";
        try {
            BulkheadPolicies.define("test-bh-execute", new BulkheadPolicy(2, 2, 500));
            String result = BulkheadPolicies.execute("test-bh-execute", () -> "via-bulkhead");
            assertEquals(testName, "via-bulkhead", result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove("test-bh-execute");
        }
    }

    // ------------------------------------------------------------------
    // Error classification / edge cases
    // ------------------------------------------------------------------

    private static void testGetWithoutDoWorkThrowsBlockNotInitialized() {
        String testName = "testGetWithoutDoWorkThrowsBlockNotInitialized";
        try {
            new Try<String>().get();
            fail(testName, "Expected BlockNotInitializedException but none was thrown");
        } catch (BlockNotInitializedException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception type: " + unexpectedException);
        }
    }

    private static void testNamedRetryPolicyNotFoundPropagatesImmediatelyWithoutRetryOrOnError() {
        String testName = "testNamedRetryPolicyNotFoundPropagatesImmediatelyWithoutRetryOrOnError";
        try {
            AtomicBoolean onErrorCalled = new AtomicBoolean(false);
            AtomicInteger calls = new AtomicInteger();
            try {
                new Try<String>()
                        .doWork(() -> {
                            calls.incrementAndGet();
                            return "should never run";
                        })
                        .retry("this-retry-policy-was-never-defined")
                        .onError(e -> {
                            onErrorCalled.set(true);
                            return "should never be reached";
                        })
                        .get();
                fail(testName, "Expected NotFoundException but none was thrown");
            } catch (NotFoundException expected) {
                assertEquals(testName, 0, calls.get());
                assertFalse(testName, onErrorCalled.get(), "onError must not run for a NotFoundException");
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testNamedTimeLimitPolicyNotFoundPropagatesImmediately() {
        String testName = "testNamedTimeLimitPolicyNotFoundPropagatesImmediately";
        try {
            new Try<String>()
                    .doWork(() -> "irrelevant")
                    .timeLimit("this-timelimit-policy-was-never-defined")
                    .onError(e -> "should never be reached")
                    .get();
            fail(testName, "Expected NotFoundException but none was thrown");
        } catch (NotFoundException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception type: " + unexpectedException);
        }
    }

    private static void testNamedBulkheadNotFoundPropagatesImmediately() {
        String testName = "testNamedBulkheadNotFoundPropagatesImmediately";
        try {
            new Try<String>()
                    .doWork(() -> "irrelevant")
                    .bulkhead("this-bulkhead-was-never-defined")
                    .onError(e -> "should never be reached")
                    .get();
            fail(testName, "Expected NotFoundException but none was thrown");
        } catch (NotFoundException expected) {
            pass(testName);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception type: " + unexpectedException);
        }
    }

    /**
     * {@code Try.get()} only catches {@link Exception}, never {@link Error}, so a thrown
     * {@link Error} must propagate immediately - uncaught, unretried, and never routed through
     * {@code onError} - in the plain (no bulkhead, no time limit) path.
     */
    private static void testErrorIsNeverRetriedAndPropagatesUnwrapped() {
        String testName = "testErrorIsNeverRetriedAndPropagatesUnwrapped";
        try {
            AtomicInteger calls = new AtomicInteger();
            AtomicBoolean onErrorCalled = new AtomicBoolean(false);
            try {
                new Try<String>()
                        .doWork(() -> {
                            calls.incrementAndGet();
                            throw new StackOverflowError("simulated");
                        })
                        .retry(5, 1)
                        .onError(e -> {
                            onErrorCalled.set(true);
                            return "should never be reached";
                        })
                        .get();
                fail(testName, "Expected StackOverflowError to propagate but none was thrown");
            } catch (StackOverflowError expected) {
                assertEquals(testName, 1, calls.get());
                assertFalse(testName, onErrorCalled.get(), "onError must not run for an Error");
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Same as above, but the failing work runs through a bulkhead's executor thread. */
    private static void testErrorThroughBulkheadPropagatesUnwrapped() {
        String testName = "testErrorThroughBulkheadPropagatesUnwrapped";
        try {
            BulkheadPolicies.define("test-bh-error", new BulkheadPolicy(2, 2, 500));
            AtomicInteger calls = new AtomicInteger();
            try {
                new Try<String>()
                        .doWork(() -> {
                            calls.incrementAndGet();
                            throw new OutOfMemoryError("simulated");
                        })
                        .retry(3, 1)
                        .bulkhead("test-bh-error")
                        .onError(e -> "should never be reached")
                        .get();
                fail(testName, "Expected OutOfMemoryError to propagate but none was thrown");
            } catch (OutOfMemoryError expected) {
                assertEquals(testName, 1, calls.get());
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove("test-bh-error");
        }
    }

    /** Same again, but the failing work runs through the shared time-limit watcher pool. */
    private static void testErrorThroughTimeLimitPropagatesUnwrapped() {
        String testName = "testErrorThroughTimeLimitPropagatesUnwrapped";
        try {
            AtomicInteger calls = new AtomicInteger();
            try {
                new Try<String>()
                        .doWork(() -> {
                            calls.incrementAndGet();
                            throw new AssertionError("simulated");
                        })
                        .retry(3, 1)
                        .timeLimit(1000)
                        .onError(e -> "should never be reached")
                        .get();
                fail(testName, "Expected AssertionError to propagate but none was thrown");
            } catch (AssertionError expected) {
                assertEquals(testName, 1, calls.get());
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * If the caller's own thread is interrupted while sleeping between retry attempts, that
     * interruption must not itself trigger another retry attempt, and the thread's interrupt
     * flag must be restored afterward.
     */
    private static void testInterruptedExceptionDuringRetryDelayIsNotRetriedAndRestoresInterruptFlag() {
        String testName = "testInterruptedExceptionDuringRetryDelayIsNotRetriedAndRestoresInterruptFlag";
        try {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<String> result = new AtomicReference<>();
            AtomicBoolean interruptFlagAfterGet = new AtomicBoolean();

            Thread worker = new Thread(() -> {
                result.set(new Try<String>()
                        .doWork(() -> {
                            calls.incrementAndGet();
                            throw new RuntimeException("always fails");
                        })
                        .retry(10, 5000) // long delay: interrupt should land here on attempt 1
                        .onError(e -> "fallback-after-interrupt")
                        .get());
                interruptFlagAfterGet.set(Thread.currentThread().isInterrupted());
            });
            worker.start();
            Thread.sleep(100); // let the worker enter its first attempt and start the long sleep
            worker.interrupt();
            worker.join(5000);

            assertFalse(testName, worker.isAlive(), "Expected the worker thread to finish promptly after interruption");
            assertEquals(testName, "fallback-after-interrupt", result.get());
            assertEquals(testName, 1, calls.get());
            assertTrue(testName, interruptFlagAfterGet.get(), "Expected the interrupt flag to be restored on the worker thread");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testOnErrorReceivesTheActualFailureType() {
        String testName = "testOnErrorReceivesTheActualFailureType";
        try {
            AtomicReference<Throwable> seen = new AtomicReference<>();
            new Try<String>()
                    .doWork(() -> {
                        throw new IllegalArgumentException("bad input");
                    })
                    .retry(1, 0)
                    .onError(e -> {
                        seen.set(e);
                        return "handled";
                    })
                    .get();
            assertTrue(testName, seen.get() instanceof IllegalArgumentException,
                    "Expected onError to receive the original IllegalArgumentException, got " + seen.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testCombinedRetryTimeLimitBulkheadFallsBackOnRepeatedTimeout() {
        String testName = "testCombinedRetryTimeLimitBulkheadFallsBackOnRepeatedTimeout";
        try {
            RetryPolicies.define("test-combo-retry", new RetryPolicy(3, 10));
            TimeLimitPolicies.define("test-combo-timelimit", new TimeLimitPolicy(100));
            BulkheadPolicies.define("test-combo-bulkhead", new BulkheadPolicy(3, 3, 1000));

            AtomicInteger attempts = new AtomicInteger();
            Object result = new Try<>()
                    .doWork(() -> {
                        attempts.incrementAndGet();
                        Thread.sleep(500); // always exceeds the 100ms time limit
                        return "unreachable";
                    })
                    .retry("test-combo-retry")
                    .timeLimit("test-combo-timelimit")
                    .bulkhead("test-combo-bulkhead")
                    .onError(error -> {
                        if (error instanceof TimeoutExecutionException) {
                            return "took too long every time";
                        }
                        return "unknown error: " + error;
                    })
                    .get();

            assertEquals(testName, "took too long every time", result);
            assertEquals(testName, 3, attempts.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            RetryPolicies.remove("test-combo-retry");
            TimeLimitPolicies.remove("test-combo-timelimit");
            BulkheadPolicies.remove("test-combo-bulkhead");
        }
    }

    // ------------------------------------------------------------------
    // Input validation
    // ------------------------------------------------------------------

    private static void testInvalidBulkheadPolicyConstructorArgumentsThrow() {
        String testName = "testInvalidBulkheadPolicyConstructorArgumentsThrow";
        try {
            try {
                new BulkheadPolicy(0, 1, 100);
                fail(testName, "Expected IllegalArgumentException for maxConcurrentThreads=0");
            } catch (IllegalArgumentException expected) {
                pass(testName);
            }
            try {
                new BulkheadPolicy(1, -1, 100);
                fail(testName, "Expected IllegalArgumentException for maxQueueSize=-1");
            } catch (IllegalArgumentException expected) {
                pass(testName);
            }
            try {
                new BulkheadPolicy(1, 1, -1);
                fail(testName, "Expected IllegalArgumentException for maxWaitMillis=-1");
            } catch (IllegalArgumentException expected) {
                pass(testName);
            }
            // Boundary values that must be accepted, not rejected.
            BulkheadPolicy edge = new BulkheadPolicy(1, 0, 0);
            assertEquals(testName, 1, edge.totalCapacity());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testInvalidRetryPolicyConstructorArgumentsThrow() {
        String testName = "testInvalidRetryPolicyConstructorArgumentsThrow";
        try {
            try {
                new RetryPolicy(0, 0);
                fail(testName, "Expected IllegalArgumentException for maxAttempts=0");
            } catch (IllegalArgumentException expected) {
                pass(testName);
            }
            try {
                new RetryPolicy(1, -1);
                fail(testName, "Expected IllegalArgumentException for delayMillis=-1");
            } catch (IllegalArgumentException expected) {
                pass(testName);
            }
            // Boundary values that must be accepted, not rejected.
            RetryPolicy edge = new RetryPolicy(1, 0);
            assertEquals(testName, 1, edge.maxAttempts());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    private static void testInvalidTimeLimitPolicyConstructorArgumentsThrow() {
        String testName = "testInvalidTimeLimitPolicyConstructorArgumentsThrow";
        try {
            try {
                new TimeLimitPolicy(-1);
                fail(testName, "Expected IllegalArgumentException for millis=-1");
            } catch (IllegalArgumentException expected) {
                pass(testName);
            }
            // millis=0 is the documented "disabled" sentinel, not an error.
            TimeLimitPolicy disabled = new TimeLimitPolicy(0);
            assertFalse(testName, disabled.isEnabled(), "Expected millis=0 to construct successfully as disabled");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * {@code Try.retry(String)}/{@code timeLimit(String)}/{@code bulkhead(String)} must reject a
     * null policy name immediately (via {@code Objects.requireNonNull}), at call time - not
     * defer the failure until {@link Try#get()}.
     */
    private static void testTryRejectsNullPolicyNames() {
        String testName = "testTryRejectsNullPolicyNames";
        try {
            try {
                new Try<String>().retry((String) null);
                fail(testName, "Expected NullPointerException from retry(null)");
            } catch (NullPointerException expected) {
                pass(testName);
            }
            try {
                new Try<String>().timeLimit((String) null);
                fail(testName, "Expected NullPointerException from timeLimit(null)");
            } catch (NullPointerException expected) {
                pass(testName);
            }
            try {
                new Try<String>().bulkhead(null);
                fail(testName, "Expected NullPointerException from bulkhead(null)");
            } catch (NullPointerException expected) {
                pass(testName);
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Pool lifecycle
    // ------------------------------------------------------------------

    /**
     * {BulkheadExecutor#shutdown()} (via {@link BulkheadPolicies#remove}) only stops the
     * pool from accepting new work - it must let a task already running to completion finish
     * normally rather than being cut off, per {@code shutdown()}'s javadoc.
     */
    private static void testBulkheadRemoveDuringInFlightExecutionLetsInFlightWorkFinish() {
        String testName = "testBulkheadRemoveDuringInFlightExecutionLetsInFlightWorkFinish";
        String poolName = "test-bh-remove-in-flight";
        try {
            BulkheadPolicies.define(poolName, new BulkheadPolicy(1, 0, 1000));
            CountDownLatch taskStarted = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>();
            AtomicReference<Exception> failure = new AtomicReference<>();

            Thread worker = new Thread(() -> {
                try {
                    result.set(new Try<String>()
                            .doWork(() -> {
                                taskStarted.countDown();
                                Thread.sleep(400);
                                return "finished despite removal";
                            })
                            .bulkhead(poolName)
                            .get());
                } catch (Exception e) {
                    failure.set(e);
                }
            });
            worker.start();
            taskStarted.await(5, TimeUnit.SECONDS);
            BulkheadPolicies.remove(poolName); // must not cut off the in-flight task above
            worker.join(5000);

            assertFalse(testName, worker.isAlive(), "Expected the worker to finish");
            assertEquals(testName, "finished despite removal", result.get());
            assertTrue(testName, failure.get() == null, "Expected no exception, got: " + failure.get());

            // The name is now unusable for new callers, as documented.
            try {
                BulkheadPolicies.execute(poolName, () -> "should not run");
                fail(testName, "Expected NotFoundException for a new call after remove()");
            } catch (NotFoundException expected) {
                pass(testName);
            }
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Repeated define/execute/remove cycles under distinct pool names must not leak the
     * dedicated worker threads {BulkheadExecutor} creates per pool - each pool's threads
     * are named {@code resicord-bulkhead-<name>-N}, so after every pool from this test has been
     * removed and given a brief grace period to wind down, none of their threads should still be
     * alive in the JVM.
     */
    private static void testRepeatedDefineRemoveCyclesDoNotLeakBulkheadThreads() {
        String testName = "testRepeatedDefineRemoveCyclesDoNotLeakBulkheadThreads";
        String namePrefix = "test-bh-cycle-";
        int cycles = 40;
        try {
            for (int i = 0; i < cycles; i++) {
                String poolName = namePrefix + i;
                BulkheadPolicies.define(poolName, new BulkheadPolicy(2, 2, 500));
                BulkheadPolicies.execute(poolName, () -> "ok");
                BulkheadPolicies.remove(poolName);
            }

            // Give shutdown() worker threads a brief, generous grace period to actually
            // terminate (they exit once their queue.take() observes the shutdown state).
            long deadline = System.currentTimeMillis() + 5000;
            long leftoverCount;
            do {
                Thread.sleep(100);
                leftoverCount = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getName().startsWith("resicord-bulkhead-" + namePrefix))
                        .count();
            } while (leftoverCount > 0 && System.currentTimeMillis() < deadline);

            assertEquals(testName, 0L, leftoverCount);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Drives several hundred concurrent {@code timeLimit()} calls through the single shared
     * static {@code timeLimitWatcherPool} (capacity 2000, per the library's design) - well below
     * its cap, so every call should succeed cleanly. This does not attempt to actually saturate
     * the full 2000-thread ceiling (creating that many real OS threads is heavy and environment-
     * dependent); it instead gives confidence that ordinary heavy concurrent use of {@code
     * timeLimit()} across many independent {@code Try} instances behaves correctly when sharing
     * that one static pool, with no cross-talk between unrelated calls' results.
     */
    private static void testTimeLimitWatcherPoolHandlesHeavyConcurrentUsageWithoutFailures() {
        String testName = "testTimeLimitWatcherPoolHandlesHeavyConcurrentUsageWithoutFailures";
        try {
            int callerCount = 400;
            AtomicInteger correctResults = new AtomicInteger();
            AtomicInteger unexpectedFailures = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(callerCount);
            ExecutorService callers = Executors.newFixedThreadPool(callerCount);

            for (int i = 0; i < callerCount; i++) {
                int index = i;
                callers.submit(() -> {
                    try {
                        startLatch.await();
                        String expected = "result-" + index;
                        String actual = new Try<String>()
                                .doWork(() -> {
                                    Thread.sleep(30);
                                    return expected;
                                })
                                .timeLimit(2000)
                                .get();
                        if (expected.equals(actual)) {
                            correctResults.incrementAndGet();
                        } else {
                            unexpectedFailures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedFailures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            callers.shutdownNow();

            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            assertEquals(testName, 0, unexpectedFailures.get());
            assertEquals(testName, callerCount, correctResults.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Heavy concurrency
    // ------------------------------------------------------------------

    /**
     * Drives a bulkhead with {@code maxConcurrentThreads = 3} using far more concurrent callers
     * than that, each recording the number of callers running simultaneously. The observed peak
     * concurrency must never exceed 3, proving the semaphore + fixed-size pool genuinely bounds
     * parallelism rather than merely queuing without a hard cap.
     */
    private static void testBulkheadEnforcesConcurrencyLimitUnderRealContention() {
        String testName = "testBulkheadEnforcesConcurrencyLimitUnderRealContention";
        String poolName = "test-bh-concurrency-limit";
        try {
            int maxConcurrent = 3;
            int callerCount = 30;
            BulkheadPolicies.define(poolName, new BulkheadPolicy(maxConcurrent, callerCount, 10_000));

            AtomicInteger currentlyRunning = new AtomicInteger();
            AtomicInteger peakRunning = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(callerCount);
            ExecutorService callers = Executors.newFixedThreadPool(callerCount);

            for (int i = 0; i < callerCount; i++) {
                callers.submit(() -> {
                    try {
                        startLatch.await();
                        new Try<String>()
                                .doWork(() -> {
                                    int running = currentlyRunning.incrementAndGet();
                                    peakRunning.updateAndGet(peak -> Math.max(peak, running));
                                    Thread.sleep(60);
                                    currentlyRunning.decrementAndGet();
                                    return "ok";
                                })
                                .bulkhead(poolName)
                                .get();
                    } catch (Exception ignored) {
                        // not relevant to this assertion
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            callers.shutdownNow();

            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            assertTrue(testName, peakRunning.get() <= maxConcurrent,
                    "Expected peak concurrency <= " + maxConcurrent + " but observed " + peakRunning.get());
            assertTrue(testName, peakRunning.get() == maxConcurrent,
                    "Expected the bulkhead to actually reach its concurrency ceiling of " + maxConcurrent
                            + " at some point, but peak was only " + peakRunning.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove(poolName);
        }
    }

    /**
     * With a small total capacity and a short {@code maxWaitMillis}, callers beyond the
     * concurrent+queue capacity must be rejected with {@link BulkheadRejectedExecutionException}
     * rather than admitted or hung indefinitely, while callers within capacity all succeed.
     */
    private static void testBulkheadRejectsBeyondTotalCapacityAfterMaxWait() {
        String testName = "testBulkheadRejectsBeyondTotalCapacityAfterMaxWait";
        String poolName = "test-bh-rejection";
        try {
            int maxConcurrent = 2;
            int maxQueue = 2;
            int totalCapacity = maxConcurrent + maxQueue; // 4
            int callerCount = 12; // well beyond capacity
            BulkheadPolicies.define(poolName, new BulkheadPolicy(maxConcurrent, maxQueue, 150));

            AtomicInteger succeeded = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            AtomicInteger unexpectedFailures = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(callerCount);
            ExecutorService callers = Executors.newFixedThreadPool(callerCount);

            for (int i = 0; i < callerCount; i++) {
                callers.submit(() -> {
                    try {
                        startLatch.await();
                        new Try<String>()
                                .doWork(() -> {
                                    Thread.sleep(400); // long enough that late arrivals time out waiting
                                    return "ok";
                                })
                                .bulkhead(poolName)
                                .get();
                        succeeded.incrementAndGet();
                    } catch (RuntimeException e) {
                        if (e instanceof BulkheadRejectedExecutionException
                                || e.getCause() instanceof BulkheadRejectedExecutionException) {
                            rejected.incrementAndGet();
                        } else {
                            unexpectedFailures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedFailures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            callers.shutdownNow();

            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            assertEquals(testName, 0, unexpectedFailures.get());
            assertTrue(testName, succeeded.get() + rejected.get() == callerCount,
                    "Expected succeeded+rejected to equal callerCount (" + callerCount + "), got "
                            + succeeded.get() + "+" + rejected.get());
            assertTrue(testName, rejected.get() > 0,
                    "Expected at least one caller to be rejected given capacity " + totalCapacity + " < " + callerCount);
            assertTrue(testName, succeeded.get() <= totalCapacity,
                    "Expected at most " + totalCapacity + " successes, got " + succeeded.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove(poolName);
        }
    }

    /**
     * Resizes a bulkhead's capacity (both up and down) repeatedly while callers are actively
     * submitting work through it, and verifies that no caller's task is silently lost or
     * double-counted - every submitted unit of work is accounted for as exactly one success or
     * one rejection.
     */
    private static void testBulkheadReconfigureUnderLoadNeverLosesOrDuplicatesWork() {
        String testName = "testBulkheadReconfigureUnderLoadNeverLosesOrDuplicatesWork";
        String poolName = "test-bh-reconfig-under-load";
        try {
            BulkheadPolicies.define(poolName, new BulkheadPolicy(2, 4, 2000));

            int callerCount = 60;
            AtomicInteger completedTasks = new AtomicInteger();
            AtomicInteger rejections = new AtomicInteger();
            AtomicInteger unexpectedFailures = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(callerCount);
            ExecutorService callers = Executors.newFixedThreadPool(callerCount);
            ExecutorService reconfigurer = Executors.newSingleThreadExecutor();
            AtomicBoolean keepReconfiguring = new AtomicBoolean(true);

            reconfigurer.submit(() -> {
                int[] sizes = {1, 5, 2, 6, 3};
                int idx = 0;
                while (keepReconfiguring.get()) {
                    try {
                        int size = sizes[idx++ % sizes.length];
                        BulkheadPolicies.reconfigure(poolName, new BulkheadPolicy(size, size, 2000));
                        Thread.sleep(15);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (NotFoundException alreadyRemoved) {
                        return;
                    }
                }
            });

            for (int i = 0; i < callerCount; i++) {
                callers.submit(() -> {
                    try {
                        startLatch.await();
                        new Try<String>()
                                .doWork(() -> {
                                    Thread.sleep(20);
                                    return "ok";
                                })
                                .bulkhead(poolName)
                                .get();
                        completedTasks.incrementAndGet();
                    } catch (RuntimeException e) {
                        if (e instanceof BulkheadRejectedExecutionException
                                || e.getCause() instanceof BulkheadRejectedExecutionException) {
                            rejections.incrementAndGet();
                        } else {
                            unexpectedFailures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedFailures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            keepReconfiguring.set(false);
            reconfigurer.shutdownNow();
            callers.shutdownNow();

            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            assertEquals(testName, 0, unexpectedFailures.get());
            assertEquals(testName, callerCount, completedTasks.get() + rejections.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove(poolName);
        }
    }

    /**
     * Many threads concurrently define/read/remove differently-named retry policies. Since each
     * thread uses its own uniquely-named policy, there is no legitimate contention on a single
     * entry - this instead stresses the shared {@code ConcurrentHashMap}-backed registry itself
     * (via {SettingsRegistry}) for corruption (lost entries, wrong values read back,
     * exceptions escaping).
     */
    private static void testConcurrentRetryPoliciesRegistryNeverCorruptsUnderHeavyReadWriteContention() {
        String testName = "testConcurrentRetryPoliciesRegistryNeverCorruptsUnderHeavyReadWriteContention";
        try {
            int threadCount = 20;
            int iterationsPerThread = 500;
            AtomicInteger mismatches = new AtomicInteger();
            AtomicInteger unexpectedErrors = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Thread> threads = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                int threadIndex = t;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String name = "concurrent-retry-" + threadIndex;
                            int attempts = (i % 9) + 1; // 1..9, always valid
                            RetryPolicies.define(name, new RetryPolicy(attempts, 0));
                            RetryPolicy readBack = RetryPolicies.get(name);
                            if (readBack.maxAttempts() != attempts) {
                                mismatches.incrementAndGet();
                            }
                            RetryPolicies.remove(name);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } catch (NotFoundException expectedRaceIsImpossibleHere) {
                        // Each thread only touches its own uniquely-named key, so a
                        // concurrently-running peer can never remove *this* thread's entry
                        // between define() and get() above - surface it as a real failure.
                        unexpectedErrors.incrementAndGet();
                    } catch (Exception unexpected) {
                        unexpectedErrors.incrementAndGet();
                    }
                });
                threads.add(thread);
            }

            threads.forEach(Thread::start);
            startLatch.countDown();
            boolean finishedInTime = true;
            for (Thread thread : threads) {
                thread.join(60_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            }

            assertTrue(testName,
                    finishedInTime && mismatches.get() == 0 && unexpectedErrors.get() == 0,
                    "finishedInTime=" + finishedInTime + ", mismatches=" + mismatches.get()
                            + ", unexpectedErrors=" + unexpectedErrors.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Mirror of the retry-registry stress test, but for {@link TimeLimitPolicies}. */
    private static void testConcurrentTimeLimitPoliciesRegistryNeverCorruptsUnderHeavyReadWriteContention() {
        String testName = "testConcurrentTimeLimitPoliciesRegistryNeverCorruptsUnderHeavyReadWriteContention";
        try {
            int threadCount = 20;
            int iterationsPerThread = 500;
            AtomicInteger mismatches = new AtomicInteger();
            AtomicInteger unexpectedErrors = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Thread> threads = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                int threadIndex = t;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String name = "concurrent-timelimit-" + threadIndex;
                            long millis = (i % 500) + 1;
                            TimeLimitPolicies.define(name, new TimeLimitPolicy(millis));
                            TimeLimitPolicy readBack = TimeLimitPolicies.get(name);
                            if (readBack.millis() != millis) {
                                mismatches.incrementAndGet();
                            }
                            TimeLimitPolicies.remove(name);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } catch (Exception unexpected) {
                        unexpectedErrors.incrementAndGet();
                    }
                });
                threads.add(thread);
            }

            threads.forEach(Thread::start);
            startLatch.countDown();
            boolean finishedInTime = true;
            for (Thread thread : threads) {
                thread.join(60_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            }

            assertTrue(testName,
                    finishedInTime && mismatches.get() == 0 && unexpectedErrors.get() == 0,
                    "finishedInTime=" + finishedInTime + ", mismatches=" + mismatches.get()
                            + ", unexpectedErrors=" + unexpectedErrors.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Many threads race to {@link BulkheadPolicies#define} the *same* pool name simultaneously.
     * Per {@code BulkheadPolicies.define}'s semantics (compute: create if absent, else
     * reconfigure in place), this must never create two separate live pools under one name, and
     * exactly one {org.j2os.platform.resicord.bulkhead.BulkheadExecutor} should end up
     * owning the name - verified indirectly by confirming the pool remains fully usable
     * afterward and that its final config matches one of the racing definitions (not a
     * corrupted mix).
     */
    private static void testConcurrentBulkheadDefineIsIdempotentUnderRace() {
        String testName = "testConcurrentBulkheadDefineIsIdempotentUnderRace";
        String poolName = "test-bh-define-race";
        try {
            int threadCount = 30;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger unexpectedErrors = new AtomicInteger();
            Set<Integer> attemptedSizes = ConcurrentHashMap.newKeySet();

            for (int t = 1; t <= threadCount; t++) {
                int size = t; // each thread proposes a distinct maxConcurrentThreads value
                attemptedSizes.add(size);
                new Thread(() -> {
                    try {
                        startLatch.await();
                        BulkheadPolicies.define(poolName, new BulkheadPolicy(size, size, 1000));
                    } catch (Exception e) {
                        unexpectedErrors.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);

            assertTrue(testName, finished, "Expected all defining threads to finish within the timeout");
            assertEquals(testName, 0, unexpectedErrors.get());

            BulkheadPolicy finalConfig = BulkheadPolicies.currentConfig(poolName);
            assertTrue(testName, attemptedSizes.contains(finalConfig.maxConcurrentThreads()),
                    "Expected the final config to match one of the racing definitions, got " + finalConfig);

            // The pool must still be fully functional after the race - not left half-configured.
            String result = BulkheadPolicies.execute(poolName, () -> "still works");
            assertEquals(testName, "still works", result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove(poolName);
        }
    }

    /**
     * The single-round version of the define-race test above gives one chance to hit a rare
     * timing window. This project's own history (see the resicord v2.x concurrent-override
     * investigation) showed a real race that surfaced in only 1 of 20 runs on one pass and 6 of
     * 20 on another - i.e. a single run is not reliable evidence of absence. This soak test
     * repeats the same race scenario across many independent rounds (fresh pool name each time)
     * to raise the odds of catching a rare corruption instead of relying on one lucky/unlucky
     * sample.
     */
    private static void testSoakRepeatedDefineRaceAcrossManyRoundsNeverCorruptsFinalState() {
        String testName = "testSoakRepeatedDefineRaceAcrossManyRoundsNeverCorruptsFinalState";
        int rounds = 30;
        int threadsPerRound = 15;
        try {
            AtomicInteger corruptedRounds = new AtomicInteger();
            AtomicInteger unusablePoolsAfterRace = new AtomicInteger();
            AtomicInteger unexpectedErrors = new AtomicInteger();

            for (int round = 0; round < rounds; round++) {
                String poolName = "test-bh-define-soak-" + round;
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(threadsPerRound);
                Set<Integer> attemptedSizes = ConcurrentHashMap.newKeySet();

                for (int t = 1; t <= threadsPerRound; t++) {
                    int size = t;
                    attemptedSizes.add(size);
                    new Thread(() -> {
                        try {
                            startLatch.await();
                            BulkheadPolicies.define(poolName, new BulkheadPolicy(size, size, 500));
                        } catch (Exception e) {
                            unexpectedErrors.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    }).start();
                }

                startLatch.countDown();
                boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
                if (!finished) {
                    corruptedRounds.incrementAndGet();
                    continue;
                }

                try {
                    BulkheadPolicy finalConfig = BulkheadPolicies.currentConfig(poolName);
                    if (!attemptedSizes.contains(finalConfig.maxConcurrentThreads())) {
                        corruptedRounds.incrementAndGet();
                    }
                    String result = BulkheadPolicies.execute(poolName, () -> "ok");
                    if (!"ok".equals(result)) {
                        unusablePoolsAfterRace.incrementAndGet();
                    }
                } catch (Exception e) {
                    unusablePoolsAfterRace.incrementAndGet();
                } finally {
                    BulkheadPolicies.remove(poolName);
                }
            }

            assertEquals(testName, 0, unexpectedErrors.get());
            assertEquals(testName, 0, corruptedRounds.get());
            assertEquals(testName, 0, unusablePoolsAfterRace.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Defines several independently-named bulkheads, each with its own concurrency ceiling, and
     * drives them all at once. Work in one pool must not be throttled by another pool's
     * capacity - i.e. bulkheads genuinely isolate concurrency per name rather than sharing one
     * hidden global limit.
     */
    private static void testManyIndependentBulkheadsRunFullyInParallelNotSerialized() {
        String testName = "testManyIndependentBulkheadsRunFullyInParallelNotSerialized";
        int poolCount = 5;
        int perPoolConcurrency = 4;
        String[] poolNames = new String[poolCount];
        try {
            for (int i = 0; i < poolCount; i++) {
                poolNames[i] = "test-bh-isolated-" + i;
                BulkheadPolicies.define(poolNames[i], new BulkheadPolicy(perPoolConcurrency, 0, 5000));
            }

            AtomicInteger globalCurrentlyRunning = new AtomicInteger();
            AtomicInteger globalPeakRunning = new AtomicInteger();
            int totalCallers = poolCount * perPoolConcurrency;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalCallers);
            ExecutorService callers = Executors.newFixedThreadPool(totalCallers);

            for (int p = 0; p < poolCount; p++) {
                String poolName = poolNames[p];
                for (int c = 0; c < perPoolConcurrency; c++) {
                    callers.submit(() -> {
                        try {
                            startLatch.await();
                            new Try<String>()
                                    .doWork(() -> {
                                        int running = globalCurrentlyRunning.incrementAndGet();
                                        globalPeakRunning.updateAndGet(peak -> Math.max(peak, running));
                                        Thread.sleep(150);
                                        globalCurrentlyRunning.decrementAndGet();
                                        return "ok";
                                    })
                                    .bulkhead(poolName)
                                    .get();
                        } catch (Exception ignored) {
                            // not relevant to this assertion
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            callers.shutdownNow();

            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            // If pools were secretly sharing one global limit of perPoolConcurrency, the observed
            // peak could never exceed it. Since every pool allows perPoolConcurrency at once and
            // they are independent, the true global peak should be able to reach all of them
            // running together (allowing some slack for scheduling variance).
            assertTrue(testName, globalPeakRunning.get() > perPoolConcurrency,
                    "Expected peak global concurrency to exceed a single pool's limit of " + perPoolConcurrency
                            + " (proving pools run independently), but observed " + globalPeakRunning.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            for (String poolName : poolNames) {
                BulkheadPolicies.remove(poolName);
            }
        }
    }

    /**
     * The heaviest single test: many concurrent callers, each independently combining retry,
     * time-limit, and bulkhead behavior (mirroring {@code Example.java}'s Part 5 demo), with
     * randomized failure/timeout/success outcomes. Verifies the whole pipeline stays correct
     * under real contention - every caller ends up with exactly one of a small set of expected
     * outcomes, and nothing throws unexpectedly or hangs.
     */
    private static void testHighConcurrencyMixedWorkloadAcrossAllThreeConcerns() {
        String testName = "testHighConcurrencyMixedWorkloadAcrossAllThreeConcerns";
        String retryName = "test-mixed-retry";
        String timeLimitName = "test-mixed-timelimit";
        String bulkheadName = "test-mixed-bulkhead";
        try {
            RetryPolicies.define(retryName, new RetryPolicy(3, 20));
            TimeLimitPolicies.define(timeLimitName, new TimeLimitPolicy(150));
            BulkheadPolicies.define(bulkheadName, new BulkheadPolicy(5, 20, 3000));

            int callerCount = 80;
            AtomicInteger succeeded = new AtomicInteger();
            AtomicInteger timedOut = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            AtomicInteger unexpectedOutcomes = new AtomicInteger();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(callerCount);
            ExecutorService callers = Executors.newFixedThreadPool(callerCount);

            for (int i = 0; i < callerCount; i++) {
                int index = i;
                callers.submit(() -> {
                    try {
                        startLatch.await();
                        // A third of callers are fast (always succeed), a third are slow enough
                        // to always trip the 150ms time limit, a third fail on their first two
                        // attempts and succeed on the third (exercising the retry loop for real).
                        int bucket = index % 3;
                        AtomicInteger callsInThisCaller = new AtomicInteger();
                        String outcome = new Try<String>()
                                .doWork(() -> {
                                    callsInThisCaller.incrementAndGet();
                                    if (bucket == 0) {
                                        return "fast-ok";
                                    } else if (bucket == 1) {
                                        Thread.sleep(500);
                                        return "unreachable-too-slow";
                                    } else {
                                        if (callsInThisCaller.get() < 3) {
                                            throw new RuntimeException("not yet");
                                        }
                                        return "eventually-ok";
                                    }
                                })
                                .retry(retryName)
                                .timeLimit(timeLimitName)
                                .bulkhead(bulkheadName)
                                .onError(e -> e instanceof TimeoutExecutionException ? "timeout" : "other-error")
                                .get();

                        switch (outcome) {
                            case "fast-ok", "eventually-ok" -> succeeded.incrementAndGet();
                            case "timeout" -> timedOut.incrementAndGet();
                            default -> unexpectedOutcomes.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        if (e instanceof BulkheadRejectedExecutionException
                                || e.getCause() instanceof BulkheadRejectedExecutionException) {
                            rejected.incrementAndGet();
                        } else {
                            unexpectedOutcomes.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedOutcomes.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(60, TimeUnit.SECONDS);
            callers.shutdownNow();

            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            assertEquals(testName, 0, unexpectedOutcomes.get());
            assertEquals(testName, callerCount, succeeded.get() + timedOut.get() + rejected.get());
            // Two-thirds of callers (buckets 0 and 2) are designed to eventually succeed;
            // rejections should be rare given generous bulkhead capacity, but are tolerated.
            assertTrue(testName, succeeded.get() >= (callerCount * 2 / 3) - rejected.get(),
                    "Expected most fast/eventually-ok callers to succeed: succeeded=" + succeeded.get()
                            + " timedOut=" + timedOut.get() + " rejected=" + rejected.get());
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            RetryPolicies.remove(retryName);
            TimeLimitPolicies.remove(timeLimitName);
            BulkheadPolicies.remove(bulkheadName);
        }
    }

    /**
     * After a large, sustained burst of mixed success/failure/rejection traffic through one
     * bulkhead finishes completely, its semaphore must be back at full available capacity - no
     * permit leaked by an exception path (e.g. a task that throws, or a caller that gets
     * rejected) ever failing to be released.
     */
    private static void testBulkheadSemaphoreNeverLeaksPermitsAcrossMixedSuccessFailureAndRejection() {
        String testName = "testBulkheadSemaphoreNeverLeaksPermitsAcrossMixedSuccessFailureAndRejection";
        String poolName = "test-bh-leak-check";
        try {
            int maxConcurrent = 4;
            int maxQueue = 2;
            BulkheadPolicies.define(poolName, new BulkheadPolicy(maxConcurrent, maxQueue, 100));

            int callerCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(callerCount);
            ExecutorService callers = Executors.newFixedThreadPool(30);
            AtomicInteger unexpectedFailures = new AtomicInteger();

            for (int i = 0; i < callerCount; i++) {
                int index = i;
                callers.submit(() -> {
                    try {
                        startLatch.await();
                        new Try<String>()
                                .doWork(() -> {
                                    if (index % 4 == 0) {
                                        throw new RuntimeException("deliberate failure");
                                    }
                                    Thread.sleep(30);
                                    return "ok";
                                })
                                .bulkhead(poolName)
                                .onError(e -> "handled")
                                .get();
                    } catch (RuntimeException e) {
                        if (!(e instanceof BulkheadRejectedExecutionException
                                || e.getCause() instanceof BulkheadRejectedExecutionException)) {
                            unexpectedFailures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedFailures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            callers.shutdownNow();
            assertTrue(testName, finished, "Expected all callers to finish within the timeout");
            assertEquals(testName, 0, unexpectedFailures.get());

            // The pool should now be fully free again: totalCapacity concurrent callers with a
            // slow-ish task should all run essentially in parallel and finish well within a
            // generous timeout, and every one of them should succeed (no stale held permits).
            int totalCapacity = maxConcurrent + maxQueue;
            AtomicInteger postCheckSuccesses = new AtomicInteger();
            CountDownLatch postCheckLatch = new CountDownLatch(totalCapacity);
            ExecutorService postCheckers = Executors.newFixedThreadPool(totalCapacity);
            long start = System.currentTimeMillis();
            for (int i = 0; i < totalCapacity; i++) {
                postCheckers.submit(() -> {
                    try {
                        new Try<String>()
                                .doWork(() -> {
                                    Thread.sleep(50);
                                    return "ok";
                                })
                                .bulkhead(poolName)
                                .get();
                        postCheckSuccesses.incrementAndGet();
                    } catch (Exception ignored) {
                        // counted via the success counter instead
                    } finally {
                        postCheckLatch.countDown();
                    }
                });
            }
            boolean postCheckFinished = postCheckLatch.await(10, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            postCheckers.shutdownNow();

            assertTrue(testName, postCheckFinished, "Expected the post-leak-check batch to finish within the timeout");
            assertEquals(testName, totalCapacity, postCheckSuccesses.get());
            assertTrue(testName, elapsed < 2000,
                    "Expected " + totalCapacity + " permit-holders to run essentially in parallel (fast), took " + elapsed + "ms");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove(poolName);
        }
    }

    /**
     * Callers stuck waiting for a bulkhead permit (because the pool is saturated) must, when
     * interrupted, fail promptly with their interrupt flag restored rather than hanging - and
     * must not leave the bulkhead itself in a broken state for callers that come after.
     */
    private static void testConcurrentInterruptionOfWaitingCallersDoesNotWedgeTheBulkhead() {
        String testName = "testConcurrentInterruptionOfWaitingCallersDoesNotWedgeTheBulkhead";
        String poolName = "test-bh-interrupt-waiters";
        try {
            BulkheadPolicies.define(poolName, new BulkheadPolicy(1, 5, 60_000));

            // Occupy the single slot for a while so every other caller below has to wait.
            CountDownLatch occupierStarted = new CountDownLatch(1);
            CountDownLatch releaseOccupier = new CountDownLatch(1);
            Thread occupier = new Thread(() -> {
                try {
                    new Try<String>()
                            .doWork(() -> {
                                occupierStarted.countDown();
                                releaseOccupier.await(10, TimeUnit.SECONDS);
                                return "released";
                            })
                            .bulkhead(poolName)
                            .get();
                } catch (Exception ignored) {
                    // not relevant
                }
            });
            occupier.start();
            occupierStarted.await(5, TimeUnit.SECONDS);

            int waiterCount = 5;
            List<Thread> waiters = new CopyOnWriteArrayList<>();
            AtomicInteger interruptedCleanly = new AtomicInteger();
            AtomicInteger unexpected = new AtomicInteger();
            CountDownLatch waitersStarted = new CountDownLatch(waiterCount);
            CountDownLatch waitersDone = new CountDownLatch(waiterCount);

            for (int i = 0; i < waiterCount; i++) {
                Thread waiter = new Thread(() -> {
                    waitersStarted.countDown();
                    try {
                        new Try<String>()
                                .doWork(() -> "should never run while occupied")
                                .bulkhead(poolName)
                                .get();
                        unexpected.incrementAndGet();
                    } catch (RuntimeException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof InterruptedException && Thread.currentThread().isInterrupted()) {
                            interruptedCleanly.incrementAndGet();
                        } else {
                            unexpected.incrementAndGet();
                        }
                    } finally {
                        waitersDone.countDown();
                    }
                });
                waiters.add(waiter);
                waiter.start();
            }

            waitersStarted.await(5, TimeUnit.SECONDS);
            Thread.sleep(200); // ensure all waiters are actually blocked on the semaphore
            waiters.forEach(Thread::interrupt);
            boolean waitersFinished = waitersDone.await(10, TimeUnit.SECONDS);

            releaseOccupier.countDown();
            occupier.join(10_000);

            assertTrue(testName, waitersFinished, "Expected all interrupted waiters to unblock promptly");
            assertEquals(testName, 0, unexpected.get());
            assertEquals(testName, waiterCount, interruptedCleanly.get());

            // The bulkhead itself must still be healthy afterward - a fresh caller succeeds.
            String result = BulkheadPolicies.execute(poolName, () -> "still healthy");
            assertEquals(testName, "still healthy", result);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        } finally {
            BulkheadPolicies.remove(poolName);
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (deliberately not using any test framework)
    // ------------------------------------------------------------------

    private static void assertEquals(String testName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(testName);
        } else {
            fail(testName, "Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(String testName, boolean condition, String failureMessage) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName, failureMessage);
        }
    }

    private static void assertFalse(String testName, boolean condition, String failureMessage) {
        assertTrue(testName, !condition, failureMessage);
    }

    private static void pass(String testName) {
        passedCount++;
        System.out.println("[PASS] " + testName);
    }

    private static void fail(String testName, String reason) {
        failedCount++;
        System.out.println("[FAIL] " + testName + " -> " + reason);
    }
}