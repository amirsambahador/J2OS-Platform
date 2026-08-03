package org.j2os.examples.desktop.resicord;

import org.j2os.platform.resicord.Try;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicies;
import org.j2os.platform.resicord.bulkhead.BulkheadPolicy;
import org.j2os.platform.resicord.exception.BlockNotInitializedException;
import org.j2os.platform.resicord.exception.NotFoundException;
import org.j2os.platform.resicord.exception.TimeoutExecutionException;
import org.j2os.platform.resicord.retry.RetryPolicies;
import org.j2os.platform.resicord.retry.RetryPolicy;
import org.j2os.platform.resicord.timelimit.TimeLimitPolicies;
import org.j2os.platform.resicord.timelimit.TimeLimitPolicy;

/**
 * Simple, self-contained tutorial that demonstrates the most common ways to
 * use {@link Try}, plus the named-policy registries it can draw on
 * ({@link BulkheadPolicies}, {@link RetryPolicies}, {@link TimeLimitPolicies}).
 * <p>
 * This class is meant purely for learning purposes: each section below
 * focuses on one resilience concern and prints its result to the console so
 * you can read the output and see exactly what each API call does. It does
 * not perform any assertions and is not a test.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class Example {

    public static void main(String[] args) {

        // ==========================================================
        // Part 1: bulkhead — limiting how many tasks can run at once
        // ==========================================================

        // BulkheadPolicies.define(name, policy): creates a bulkhead under this name.
        // The 1st number is the max number of doWork calls allowed to run *concurrently*.
        // The 2nd number is how many additional callers are allowed to queue and wait.
        // The 3rd number is the max milliseconds a caller waits for its turn before
        // being rejected.
        BulkheadPolicies.define("sms-service", new BulkheadPolicy(2, 5, 1000));
        System.out.println("bulkhead 'sms-service' created: 2 concurrent max, queue of 5, 1s wait");

        // BulkheadPolicies.currentConfig(name): returns a bulkhead's current config -
        // the kind of thing an admin page would call to show current status.
        BulkheadPolicy config = BulkheadPolicies.currentConfig("sms-service");
        System.out.println("current config: " + config);

        // BulkheadPolicies.reconfigure(name, newPolicy): changes an existing bulkhead's
        // config in place, without shutting anything down or interrupting in-flight work.
        BulkheadPolicies.reconfigure("sms-service", new BulkheadPolicy(5, 10, 2000));
        System.out.println("after reconfigure: " + BulkheadPolicies.currentConfig("sms-service"));

        // new Try<>().doWork(...).bulkhead(name).get(): runs the task through that
        // bulkhead - this call has to wait its turn like any other caller.
        String smsResult = new Try<String>()
                .doWork(() -> "SMS sent") // this is the actual work we want done
                .bulkhead("sms-service")
                .get();
        System.out.println("result: " + smsResult);

        // BulkheadPolicies.listAll(): every bulkhead defined so far.
        System.out.println("all bulkheads: " + BulkheadPolicies.listAll().keySet());

        // BulkheadPolicies.remove(name): removes a bulkhead entirely. No one can
        // attach to this name anymore after this.
        BulkheadPolicies.remove("sms-service");
        System.out.println("bulkhead 'sms-service' removed");
        System.out.println();

        // ==========================================================
        // Part 2: retry — trying again when a task fails
        // ==========================================================

        // .retry(attempts, delayMillis): if doWork throws, retries it up to this
        // many times.
        String retryResult = new Try<String>()
                .doWork(() -> "task succeeded")
                .retry(3, 100) // up to 3 attempts, 100ms between each
                .get();
        System.out.println("retry result: " + retryResult);

        // RetryPolicies.define(name, policy): registers a retry config under a name
        // so several places in the code can share it.
        RetryPolicies.define("standard-retry", new RetryPolicy(3, 200));
        System.out.println("retry policy 'standard-retry' registered");

        // RetryPolicies.get(name): reads a retry policy's current config.
        RetryPolicy retryPolicy = RetryPolicies.get("standard-retry");
        System.out.println("standard-retry currently: max " + retryPolicy.maxAttempts()
                + " attempts, " + retryPolicy.delayMillis() + "ms delay");

        // .retry(name): uses an already-registered retry policy instead of raw
        // numbers. If that policy is changed later, this call picks up the change too.
        String namedRetryResult = new Try<String>()
                .doWork(() -> "ran with the shared policy")
                .retry("standard-retry")
                .get();
        System.out.println("result: " + namedRetryResult);

        // RetryPolicies.listAll(): every registered retry policy.
        System.out.println("all retry policies: " + RetryPolicies.listAll().keySet());

        // RetryPolicies.remove(name): removes this policy.
        RetryPolicies.remove("standard-retry");
        System.out.println("retry policy 'standard-retry' removed");
        System.out.println();

        // ==========================================================
        // Part 3: timeLimit — limiting how long a task may run
        // ==========================================================

        // .timeLimit(millis): if doWork takes longer than this, it's cancelled and a
        // timeout error is raised instead.
        String timeLimitResult = new Try<String>()
                .doWork(() -> "finished quickly")
                .timeLimit(1000) // 1 second allowed
                .get();
        System.out.println("timeLimit result: " + timeLimitResult);

        // TimeLimitPolicies.define(name, policy): like RetryPolicies, registers a time
        // limit under a name so several places in the code can share it.
        TimeLimitPolicies.define("standard-timeout", new TimeLimitPolicy(500));
        System.out.println("time-limit policy 'standard-timeout' registered");

        // TimeLimitPolicies.get(name): reads a time-limit policy's current config.
        TimeLimitPolicy timeLimitPolicy = TimeLimitPolicies.get("standard-timeout");
        System.out.println("standard-timeout currently: " + timeLimitPolicy.millis()
                + "ms, enabled? " + timeLimitPolicy.isEnabled());

        // .timeLimit(name): uses an already-registered time-limit policy instead of a
        // raw number.
        String namedTimeLimitResult = new Try<String>()
                .doWork(() -> "ran with the shared policy")
                .timeLimit("standard-timeout")
                .get();
        System.out.println("result: " + namedTimeLimitResult);

        // TimeLimitPolicies.listAll(): every registered time-limit policy.
        System.out.println("all time-limit policies: " + TimeLimitPolicies.listAll().keySet());

        // TimeLimitPolicies.remove(name): removes this policy.
        TimeLimitPolicies.remove("standard-timeout");
        System.out.println("time-limit policy 'standard-timeout' removed");
        System.out.println();

        // ==========================================================
        // Part 4: onError — when every attempt has failed
        // ==========================================================

        // .onError(...): if the task still fails after retries are exhausted, this
        // fallback value is returned instead of throwing the error out to the caller.
        String fallbackResult = new Try<String>()
                .doWork(() -> {
                    throw new RuntimeException("service unavailable");
                })
                .retry(2, 50)
                .onError(error -> "couldn't connect, falling back to this default: " + error.getMessage())
                .get();
        System.out.println("result with onError: " + fallbackResult);
        System.out.println();


        // ==========================================================
        // Part 5: errors you might run into
        // ==========================================================

        // If you don't call doWork and call get() directly, you get this error -
        // because there's no work defined to run yet.
        try {
            new Try<String>().get();
        } catch (BlockNotInitializedException e) {
            System.out.println("error: " + e.getMessage());
        }

        // If you name a bulkhead/retry/timeLimit that was never defined, you get
        // this error - a typo or a forgotten define(), not a transient failure
        // that's worth retrying.
        try {
            new Try<String>().doWork(() -> "test").bulkhead("a-name-that-does-not-exist").get();
        } catch (NotFoundException e) {
            System.out.println("error: " + e.getMessage());
        }

        RetryPolicies.define("retry1", new RetryPolicy(3, 200));
        BulkheadPolicies.define("bulkhead1", new BulkheadPolicy(5, 95, 2000));
        TimeLimitPolicies.define("timeLimit1", new TimeLimitPolicy(500));

        // Combines all three concerns at once: the task deliberately sleeps longer
        // than timeLimit1 allows, so it always times out; onError then distinguishes
        // between the different failure types it might see.
        Object fallbackObjectResult = new Try<>()
                .doWork(() -> {
                    Thread.sleep(2000);
                    return "OK";
                })
                .retry("retry1")
                .timeLimit("timeLimit1")
                .bulkhead("bulkhead1")
                .onError(error -> {
                    if (error instanceof BlockNotInitializedException) {
                        return "no work was ever defined";
                    } else if (error instanceof TimeoutExecutionException) {
                        return "took too long to respond";
                    } else if (error instanceof ArithmeticException) {
                        return "not applicable here";
                    } else {
                        return "unknown error: " + error.getMessage();
                    }
                })
                .get();
        System.out.println(fallbackObjectResult);


        System.out.println("Example: done.");
    }
}