package org.j2os.test.jsecurity;

import org.j2os.platform.jsecurity.access.RequestAccessControl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain, dependency-free test suite for {@code org.j2os.platform.jsecurity.access}
 * ({@link RequestAccessControl}) (no test framework such as JUnit is used). Run it directly
 * with its {@link #main(String[])} method; each test case reports PASS/FAIL to standard output
 * and a summary is printed at the end.
 * <p>
 * Every test uses its own unique scope/action names so tests never interfere with each other's
 * registered restrictions, even though {@link RequestAccessControl}'s registry is a shared,
 * static, JVM-wide store.
 * <p>
 * <b>Classpath requirements:</b> Apache Commons Lang3 (for {@link RequestAccessControl}'s
 * reflection helpers).
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class RequestAccessControlTest {

    /** Total number of test cases executed so far. */
    private static int totalTestCount = 0;

    /** Number of test cases that failed so far. */
    private static int failedTestCount = 0;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        testApplyNullsRestrictedFieldWithoutOldTarget();
        testApplyRestoresFieldFromOldTarget();
        testActionDenialThrowsDeniedExceptionWithActionAsMessage();
        testDifferentScopesAreIsolated();
        testUnrestrictedFieldsAreUntouched();
        testNestedDotPathRestrictionOnlyAffectsNestedField();
        testRegisteringPrimitiveFieldThrows();
        testRegisteringUnknownFieldThrows();
        testApplyOnClassWithoutNoArgConstructorThrows();
        testUnregisterFieldLimitationLiftsRestriction();
        testUnregisterActionDenialLiftsDenial();
        testShallowCopySharesUnrestrictedNestedObjectByReference();
        testRegisterByUnresolvableClassNameDoesNotThrow();
        testApplyOnNullTargetReturnsNull();
        testConcurrentChurnNeverCorruptsRegistryOrLosesData();

        printSummary();
        System.exit(failedTestCount == 0 ? 0 : 1);
    }

    /** Verifies a restricted field is nulled out when no oldTarget is supplied. */
    private static void testApplyNullsRestrictedFieldWithoutOldTarget() {
        String testName = "RequestAccessControl.apply nulls a restricted field with no oldTarget";
        String scope = "test-null-no-old";
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "name", "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        TestPerson result = RequestAccessControl.apply(scope, person, "INSERT");

        assertTrue(testName, result.name == null && "Bahador".equals(result.family) && Integer.valueOf(40).equals(result.age));
    }

    /** Verifies a restricted field falls back to oldTarget's value when one is supplied. */
    private static void testApplyRestoresFieldFromOldTarget() {
        String testName = "RequestAccessControl.apply restores a restricted field from oldTarget";
        String scope = "test-restore-old";
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "name", "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        TestPerson oldPerson = new TestPerson("Reza", "Jamshidi", 70, null);
        TestPerson result = RequestAccessControl.apply(scope, person, oldPerson, "INSERT");

        assertTrue(testName, "Reza".equals(result.name) && "Bahador".equals(result.family));
    }

    /** Verifies a full action denial throws DeniedException whose message is the action name. */
    private static void testActionDenialThrowsDeniedExceptionWithActionAsMessage() {
        String testName = "RequestAccessControl full action denial throws DeniedException(action)";
        String scope = "test-deny-all";
        RequestAccessControl.registerActionDenial(scope, TestPerson.class, "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        try {
            RequestAccessControl.apply(scope, person, "INSERT");
            fail(testName + " [expected DeniedException]");
        } catch (RequestAccessControl.DeniedException expected) {
            assertTrue(testName, "INSERT".equals(expected.getMessage()));
        }
    }

    /** Verifies a restriction registered under one scope does not affect another scope. */
    private static void testDifferentScopesAreIsolated() {
        String testName = "RequestAccessControl restrictions are isolated per scope";
        String restrictedScope = "test-scope-a";
        String unrestrictedScope = "test-scope-b";
        RequestAccessControl.registerFieldLimitation(restrictedScope, TestPerson.class, "name", "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        TestPerson restrictedResult = RequestAccessControl.apply(restrictedScope, person, "INSERT");
        TestPerson unrestrictedResult = RequestAccessControl.apply(unrestrictedScope, person, "INSERT");

        assertTrue(testName, restrictedResult.name == null && "Amirsam".equals(unrestrictedResult.name));
    }

    /** Verifies fields not restricted are left untouched on the returned copy. */
    private static void testUnrestrictedFieldsAreUntouched() {
        String testName = "RequestAccessControl leaves unrestricted fields untouched";
        String scope = "test-unrestricted-fields";
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "age", "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        TestPerson result = RequestAccessControl.apply(scope, person, "INSERT");

        assertTrue(testName, "Amirsam".equals(result.name) && "Bahador".equals(result.family) && result.age == null);
    }

    /** Verifies a nested dot-path restriction only nulls the nested field, without disturbing sibling nested fields. */
    private static void testNestedDotPathRestrictionOnlyAffectsNestedField() {
        String testName = "RequestAccessControl restricts a nested dot-path field without disturbing siblings";
        String scope = "test-nested-path";
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "car.factory.name", "INSERT");

        TestFactory factory = new TestFactory("KIA");
        TestCar car = new TestCar("CERATO", factory);
        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, car);

        TestPerson result = RequestAccessControl.apply(scope, person, "INSERT");

        assertTrue(testName, result.car.factory.name == null && "CERATO".equals(result.car.name));
    }

    /** Verifies registering a restriction on a primitive-typed field is rejected. */
    private static void testRegisteringPrimitiveFieldThrows() {
        String testName = "RequestAccessControl rejects registering a primitive-typed field";
        try {
            RequestAccessControl.registerFieldLimitation("test-primitive-reject", PrimitiveFieldEntity.class, "count", "INSERT");
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /** Verifies registering a restriction on a field that does not exist is rejected. */
    private static void testRegisteringUnknownFieldThrows() {
        String testName = "RequestAccessControl rejects registering an unknown field";
        try {
            RequestAccessControl.registerFieldLimitation("test-unknown-field", TestPerson.class, "doesNotExist", "INSERT");
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /** Verifies apply() on a class without an accessible no-arg constructor fails with a clear error. */
    private static void testApplyOnClassWithoutNoArgConstructorThrows() {
        String testName = "RequestAccessControl.apply fails clearly for a class with no no-arg constructor";
        String scope = "test-no-noarg-ctor";
        NoDefaultConstructorEntity entity = new NoDefaultConstructorEntity("value");
        try {
            RequestAccessControl.apply(scope, entity, "INSERT");
            fail(testName + " [expected RuntimeException]");
        } catch (RuntimeException expected) {
            assertTrue(testName, expected.getMessage() != null && expected.getMessage().contains("no-arg constructor"));
        }
    }

    /** Verifies unregisterFieldLimitation lifts a previously registered field restriction. */
    private static void testUnregisterFieldLimitationLiftsRestriction() {
        String testName = "RequestAccessControl.unregisterFieldLimitation lifts a field restriction";
        String scope = "test-unregister-field";
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "name", "INSERT");
        RequestAccessControl.unregisterFieldLimitation(scope, TestPerson.class, "name", "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        TestPerson result = RequestAccessControl.apply(scope, person, "INSERT");

        assertTrue(testName, "Amirsam".equals(result.name));
    }

    /** Verifies unregisterActionDenial lifts a previously registered full denial. */
    private static void testUnregisterActionDenialLiftsDenial() {
        String testName = "RequestAccessControl.unregisterActionDenial lifts a full denial";
        String scope = "test-unregister-deny";
        RequestAccessControl.registerActionDenial(scope, TestPerson.class, "INSERT");
        RequestAccessControl.unregisterActionDenial(scope, TestPerson.class, "INSERT");

        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
        try {
            TestPerson result = RequestAccessControl.apply(scope, person, "INSERT");
            assertTrue(testName, "Amirsam".equals(result.name));
        } catch (RequestAccessControl.DeniedException unexpected) {
            fail(testName + " [denial should have been lifted]");
        }
    }

    /**
     * Verifies that a nested field NOT on any restricted path is shared by reference between the
     * original and the copy (the shallow-copy limitation): mutating it through the copy also
     * changes the original.
     */
    private static void testShallowCopySharesUnrestrictedNestedObjectByReference() {
        String testName = "RequestAccessControl.apply shares an unrestricted nested object by reference (shallow copy)";
        String scope = "test-shallow-copy-sharing";
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "name", "INSERT");

        TestCar car = new TestCar("CERATO", new TestFactory("KIA"));
        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, car);

        TestPerson result = RequestAccessControl.apply(scope, person, "INSERT");
        result.car.name = "MUTATED";

        assertTrue(testName, "MUTATED".equals(person.car.name));
    }

    /** Verifies registering by an unresolvable class name silently skips validation rather than throwing. */
    private static void testRegisterByUnresolvableClassNameDoesNotThrow() {
        String testName = "RequestAccessControl.registerFieldLimitation(className) silently skips validation for an unresolvable class";
        try {
            RequestAccessControl.registerFieldLimitation("test-unresolvable-class", "com.example.DoesNotExist", "anyField", "INSERT");
            pass(testName);
        } catch (Exception unexpected) {
            fail(testName + " [unexpected exception: " + unexpected + "]");
        }
    }

    /** Verifies apply() returns null when given a null target. */
    private static void testApplyOnNullTargetReturnsNull() {
        String testName = "RequestAccessControl.apply returns null for a null target";
        TestPerson result = RequestAccessControl.apply("test-null-target", null, "INSERT");
        assertTrue(testName, result == null);
    }

    /**
     * Heavy concurrency stress test, targeting exactly the scenario the class's own javadoc
     * calls out ("two admins granting/revoking access at the same time"): many threads
     * concurrently toggle an action denial on and off, other threads concurrently churn an
     * unrelated field limitation on and off, and a third group continuously calls apply() -
     * all against the very same scope/class/action key, for tens of thousands of operations.
     * <p>
     * A {@code "name"} field limitation is registered once, up front, and never touched again
     * by any thread - so it gives a deterministic invariant to check no matter how the other
     * two kinds of churn interleave: every apply() call that doesn't hit the (intermittently
     * toggled) denial must still return a genuinely distinct copy whose {@code name} is null
     * and whose untouched {@code family} field is unchanged. If concurrent denial toggling or
     * concurrent churn of the unrelated {@code "age"} field ever corrupted the shared
     * {@code Restriction} object for this key (e.g. via the isEmpty()-triggered key removal
     * racing with a concurrent read), this invariant would be violated.
     */
    private static void testConcurrentChurnNeverCorruptsRegistryOrLosesData() {
        String testName = "Concurrent denial-toggle and field-churn never corrupt an unrelated field limitation";
        String scope = "test-concurrency-stress";
        String action = "INSERT";

        // Registered once, up front, and never touched again by any thread below - must survive
        // everything that follows, no matter how it interleaves.
        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "name", action);

        int denialTogglerThreads = 4;
        int fieldChurnThreads = 4;
        int applierThreads = 12;
        int iterationsPerThread = 3000;

        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicInteger copyIdentityViolationCount = new AtomicInteger(0);
        AtomicInteger invariantViolationCount = new AtomicInteger(0);
        AtomicInteger deniedExceptionCount = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Threads that repeatedly toggle an independent action denial on the SAME key.
        for (int t = 0; t < denialTogglerThreads; t++) {
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        RequestAccessControl.registerActionDenial(scope, TestPerson.class, action);
                        RequestAccessControl.unregisterActionDenial(scope, TestPerson.class, action);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        // Threads that repeatedly register/unregister a DIFFERENT field ("age") on the same
        // key - exercising concurrent Set churn without ever touching the "name" limitation
        // being checked below.
        for (int t = 0; t < fieldChurnThreads; t++) {
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        RequestAccessControl.registerFieldLimitation(scope, TestPerson.class, "age", action);
                        RequestAccessControl.unregisterFieldLimitation(scope, TestPerson.class, "age", action);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        // Threads that repeatedly apply() throughout the churn above.
        for (int t = 0; t < applierThreads; t++) {
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        TestPerson person = new TestPerson("Amirsam", "Bahador", 40, null);
                        try {
                            TestPerson result = RequestAccessControl.apply(scope, person, action);
                            if (result == person) {
                                copyIdentityViolationCount.incrementAndGet();
                            }
                            if (result.name != null || !"Bahador".equals(result.family)) {
                                invariantViolationCount.incrementAndGet();
                            }
                        } catch (RequestAccessControl.DeniedException expectedWhileDenialIsToggledOn) {
                            deniedExceptionCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        threads.forEach(Thread::start);
        startLatch.countDown();

        boolean finishedInTime = true;
        for (Thread thread : threads) {
            try {
                thread.join(30_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                finishedInTime = false;
            }
        }

        // Defensive cleanup: make sure nothing was left registered for this scope/action by an
        // unlucky final iteration, so this test never leaks state to a later run sharing the JVM.
        RequestAccessControl.unregisterActionDenial(scope, TestPerson.class, action);
        RequestAccessControl.unregisterFieldLimitation(scope, TestPerson.class, "age", action);
        RequestAccessControl.unregisterFieldLimitation(scope, TestPerson.class, "name", action);

        assertTrue(testName,
                finishedInTime
                        && unexpectedErrorCount.get() == 0
                        && copyIdentityViolationCount.get() == 0
                        && invariantViolationCount.get() == 0);

        // Diagnostic only, not a pass/fail condition: a nonzero count here confirms apply() and
        // the denial-toggling threads actually raced against each other for real, rather than
        // this test accidentally running with no real contention.
        System.out.println("      (diagnostic: " + deniedExceptionCount.get()
                + " of " + (applierThreads * iterationsPerThread) + " apply() calls observed the denial mid-toggle)");
    }

    // ------------------------------------------------------------------
    // Test domain types
    // ------------------------------------------------------------------

    /** A mutable, reflection-friendly test entity with a nested {@link TestCar}. */
    private static class TestPerson {
        private String name;
        private String family;
        private Integer age;
        private TestCar car;

        TestPerson() {
        }

        TestPerson(String name, String family, Integer age, TestCar car) {
            this.name = name;
            this.family = family;
            this.age = age;
            this.car = car;
        }
    }

    /** A nested test entity representing a car, with its own nested {@link TestFactory}. */
    private static class TestCar {
        private String name;
        private TestFactory factory;

        TestCar() {
        }

        TestCar(String name, TestFactory factory) {
            this.name = name;
            this.factory = factory;
        }
    }

    /** A nested test entity representing a car's manufacturer. */
    private static class TestFactory {
        private String name;

        TestFactory() {
        }

        TestFactory(String name) {
            this.name = name;
        }
    }

    /** A test entity with a primitive field, used to verify primitive fields are rejected at registration. */
    private static class PrimitiveFieldEntity {
        private int count;

        PrimitiveFieldEntity() {
        }
    }

    /** A test entity with no accessible no-arg constructor, used to verify shallow-copy fails clearly for it. */
    private static class NoDefaultConstructorEntity {
        private final String value;

        NoDefaultConstructorEntity(String value) {
            this.value = value;
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (no external test framework)
    // ------------------------------------------------------------------

    /**
     * Records a passing test case if {@code condition} is true, otherwise records a failure.
     *
     * @param testName  the name of the test case, printed in the report
     * @param condition the condition that must be true for the test to pass
     */
    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        totalTestCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void fail(String testName) {
        totalTestCount++;
        failedTestCount++;
        System.out.println("[FAIL] " + testName);
    }

    /** Prints a final pass/fail summary of the whole suite. */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}