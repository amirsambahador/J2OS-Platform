package org.j2os.test.jsecurity;

import org.j2os.platform.jsecurity.access.ResponseAccessControl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain, dependency-free test suite for {@code org.j2os.platform.jsecurity.access}
 * ({@link ResponseAccessControl}) (no test framework such as JUnit is used). Run it directly
 * with its {@link #main(String[])} method; each test case reports PASS/FAIL to standard output
 * and a summary is printed at the end.
 * <p>
 * <b>Classpath requirements:</b> the {@code org.j2os.platform.page2} library (for {@link
 * ResponseAccessControl}'s underlying {@code PageDataResultFilter}, which in turn needs its own
 * Jackson dependency).
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ResponseAccessControlTest {

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
        testResponseAccessControlMapOverloadRemovesFieldByDefault();
        testResponseAccessControlMapOverloadEmptiesFieldWhenRequested();
        testResponseAccessControlListOverloadRemovesNestedField();
        testResponseAccessControlSingleObjectOverloadRemovesWholeNestedObject();
        testResponseAccessControlOverloadsReturnNullForNullInput();
        testResponseAccessControlEmptyRestrictedFieldsLeavesRowsUnchanged();

        printSummary();
        System.exit(failedTestCount == 0 ? 0 : 1);
    }

    /** Verifies the Map-shaped overload removes a restricted field by default (no action specified). */
    private static void testResponseAccessControlMapOverloadRemovesFieldByDefault() {
        String testName = "ResponseAccessControl (Map overload) removes a restricted field by default";
        Map<String, Object> page2Result = new HashMap<>();
        page2Result.put("rows", List.of(new SimplePerson("Amirsam", "Bahador")));

        Map<String, Object> result = ResponseAccessControl.apply(page2Result, List.of("firstName"), null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertTrue(testName, !rows.get(0).containsKey("firstName") && "Bahador".equals(rows.get(0).get("lastName")));
    }

    /** Verifies the Map-shaped overload blanks a restricted field instead of removing it, when EMPTY is requested. */
    private static void testResponseAccessControlMapOverloadEmptiesFieldWhenRequested() {
        String testName = "ResponseAccessControl (Map overload) blanks a restricted field when EMPTY is requested";
        Map<String, Object> page2Result = new HashMap<>();
        page2Result.put("rows", List.of(new SimplePerson("Amirsam", "Bahador")));

        Map<String, Object> result = ResponseAccessControl.apply(page2Result, List.of("firstName"), ResponseAccessControl.EMPTY);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertTrue(testName, "".equals(rows.get(0).get("firstName")));
    }

    /** Verifies the List-shaped overload removes a nested dot-path field from every row. */
    private static void testResponseAccessControlListOverloadRemovesNestedField() {
        String testName = "ResponseAccessControl (List overload) removes a nested dot-path field";
        List<SimplePersonWithCar> people = List.of(
                new SimplePersonWithCar("Amirsam", new SimpleCar("CERATO", new SimpleFactory("KIA"))));

        List<Map<String, Object>> rows = ResponseAccessControl.apply(people, List.of("car.factory.name"), ResponseAccessControl.REMOVE);

        @SuppressWarnings("unchecked")
        Map<String, Object> carMap = (Map<String, Object>) rows.get(0).get("car");
        @SuppressWarnings("unchecked")
        Map<String, Object> factoryMap = (Map<String, Object>) carMap.get("factory");
        assertTrue(testName, !factoryMap.containsKey("name") && "CERATO".equals(carMap.get("name")));
    }

    /** Verifies the single-object overload can remove an entire nested object. */
    private static void testResponseAccessControlSingleObjectOverloadRemovesWholeNestedObject() {
        String testName = "ResponseAccessControl (single-object overload) removes a whole nested object";
        SimplePersonWithCar person = new SimplePersonWithCar("Amirsam", new SimpleCar("CERATO", new SimpleFactory("KIA")));

        Map<String, Object> result = ResponseAccessControl.apply((Object) person, List.of("car"), ResponseAccessControl.REMOVE);

        assertTrue(testName, !result.containsKey("car") && "Amirsam".equals(result.get("firstName")));
    }

    /** Verifies all three overloads return null when given a null input. */
    private static void testResponseAccessControlOverloadsReturnNullForNullInput() {
        String testName = "ResponseAccessControl overloads all return null for null input";
        Map<String, Object> mapResult = ResponseAccessControl.apply((Map<String, Object>) null, List.of("x"), ResponseAccessControl.REMOVE);
        List<Map<String, Object>> listResult = ResponseAccessControl.apply((List<?>) null, List.of("x"), ResponseAccessControl.REMOVE);
        Map<String, Object> objectResult = ResponseAccessControl.apply((Object) null, List.of("x"), ResponseAccessControl.REMOVE);

        assertTrue(testName, mapResult == null && listResult == null && objectResult == null);
    }

    /** Verifies an empty/null restrictedFields collection leaves rows unchanged aside from the Map conversion. */
    private static void testResponseAccessControlEmptyRestrictedFieldsLeavesRowsUnchanged() {
        String testName = "ResponseAccessControl leaves rows unchanged when restrictedFields is empty";
        List<SimplePerson> people = List.of(new SimplePerson("Amirsam", "Bahador"));

        List<Map<String, Object>> rows = ResponseAccessControl.apply(people, List.of(), ResponseAccessControl.REMOVE);

        assertTrue(testName, "Amirsam".equals(rows.get(0).get("firstName")) && "Bahador".equals(rows.get(0).get("lastName")));
    }

    // ------------------------------------------------------------------
    // Test domain types
    // ------------------------------------------------------------------

    /** A minimal record used by these tests. */
    private record SimplePerson(String firstName, String lastName) {
    }

    /** A record with a nested {@link SimpleCar}, used by the nested-path tests. */
    private record SimplePersonWithCar(String firstName, SimpleCar car) {
    }

    /** A nested record representing a car, with its own nested {@link SimpleFactory}. */
    private record SimpleCar(String name, SimpleFactory factory) {
    }

    /** A nested record representing a car's manufacturer. */
    private record SimpleFactory(String name) {
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