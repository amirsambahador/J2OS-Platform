package org.j2os.test.jutil;

import org.j2os.platform.jutil.json.JSon;

/**
 * Standalone, dependency-free test suite for {@link JSon}.
 * <p>
 * This class intentionally does <b>not</b> use JUnit or any other testing
 * framework: it is a plain Java class with a {@code main} method that runs
 * every test case sequentially, prints a PASS/FAIL line for each one, and
 * prints a final summary. Run it directly (adjust the classpath to include
 * Lombok and the Jackson {@code tools.jackson} databind jar):
 * <pre>{@code
 * javac -cp lombok.jar:jackson-databind.jar -d out org/j2os/platform/jutil/json/JSon.java org/j2os/test/jutil/JSonTest.java
 * java -cp out:jackson-databind.jar org.j2os.test.jutil.JSonTest
 * }</pre>
 * A non-zero process exit code indicates at least one failed test.
 * <p>
 * <b>On {@code readFieldArray*} out-of-range/non-array input:</b> {@link JSon#readFieldArrayAsText}
 * and {@link JSon#readFieldArrayAsString} resolve both the array index and the field name via
 * {@code JsonNode.path(...)} rather than {@code JsonNode.get(...)} - {@code path(...)} always
 * returns a non-null "missing" node instead of {@code null} when the index is out of bounds,
 * negative, or the root isn't an array. As a result these methods degrade gracefully (an empty
 * string, or a non-null missing-node representation) for all of those cases rather than
 * throwing - this is verified below rather than assumed.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JSonTest {

    private static int passedCount = 0;
    private static int failedCount = 0;

    /** Simple POJO used across the tests. Jackson auto-detects public fields. */
    public static class Person {
        public String name;
        public int age;
        public Address address;

        public Person() {
        }

        public Person(String name, int age, Address address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
    }

    /** Nested POJO, used to test reading of nested fields. */
    public static class Address {
        public String city;
        public String country;

        public Address() {
        }

        public Address(String city, String country) {
            this.city = city;
            this.country = country;
        }
    }

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== JSon test suite ===");

        testWriteSerializesSimpleObject();
        testWriteThenReadRoundTrip();
        testReadDeserializesFlatJson();
        testReadDeserializesNestedJson();
        testReadFieldAsTextTopLevelField();
        testReadFieldAsTextNestedField();
        testReadFieldAsTextMissingFieldReturnsEmptyString();
        testReadFieldAsTextMissingNestedPathReturnsEmptyString();
        testReadFieldAsStringReturnsQuotedTextForStringField();
        testReadFieldAsStringReturnsRawNumberForNumericField();
        testReadFieldAsStringReturnsFullJsonForObjectField();
        testReadFieldAsStringReturnsMissingNodeTextForMissingField();
        testReadFieldArrayAsTextValidIndex();
        testReadFieldArrayAsStringValidIndex();
        testReadFieldArrayAsTextMissingFieldOnElementReturnsEmptyString();
        testReadInvalidJsonThrowsException();
        testReadMismatchedTypeThrowsException();

        // Out-of-range / non-array input: handled gracefully, not exceptionally (see class javadoc).
        testReadFieldArrayAsTextOutOfBoundsIndexReturnsEmptyString();
        testReadFieldArrayAsStringOutOfBoundsIndexDoesNotThrow();
        testReadFieldArrayAsTextNonArrayRootReturnsEmptyString();
        testReadFieldArrayAsTextNegativeIndexReturnsEmptyString();

        System.out.println();
        System.out.println("=== Summary: " + passedCount + " passed, " + failedCount + " failed ===");
        if (failedCount > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // write / read round trips
    // ------------------------------------------------------------------

    /** Verifies write(Object) serializes an object's fields into the JSON output. */
    private static void testWriteSerializesSimpleObject() {
        String testName = "testWriteSerializesSimpleObject";
        try {
            Person person = new Person("Sara", 29, new Address("Tehran", "Iran"));
            String json = JSon.write(person);
            assertTrue(testName, json.contains("\"name\":\"Sara\""), "Expected serialized JSON to contain the name field, was: " + json);
            assertTrue(testName, json.contains("\"age\":29"), "Expected serialized JSON to contain the age field, was: " + json);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies write(Object) followed by read(String, Class) round-trips an object, including its nested field. */
    private static void testWriteThenReadRoundTrip() {
        String testName = "testWriteThenReadRoundTrip";
        try {
            Person original = new Person("Sara", 29, new Address("Tehran", "Iran"));
            String json = JSon.write(original);
            Person roundTripped = JSon.read(json, Person.class);

            assertEquals(testName, original.name, roundTripped.name);
            assertEquals(testName, original.age, roundTripped.age);
            assertEquals(testName, original.address.city, roundTripped.address.city);
            assertEquals(testName, original.address.country, roundTripped.address.country);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies read(String, Class) deserializes a flat JSON object correctly. */
    private static void testReadDeserializesFlatJson() {
        String testName = "testReadDeserializesFlatJson";
        try {
            String json = "{\"name\":\"Ali\",\"age\":40}";
            Person person = JSon.read(json, Person.class);
            assertEquals(testName, "Ali", person.name);
            assertEquals(testName, 40, person.age);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies read(String, Class) deserializes a JSON object with a nested object correctly. */
    private static void testReadDeserializesNestedJson() {
        String testName = "testReadDeserializesNestedJson";
        try {
            String json = "{\"name\":\"Ali\",\"age\":40,\"address\":{\"city\":\"Shiraz\",\"country\":\"Iran\"}}";
            Person person = JSon.read(json, Person.class);
            assertEquals(testName, "Shiraz", person.address.city);
            assertEquals(testName, "Iran", person.address.country);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // readFieldAsText
    // ------------------------------------------------------------------

    /** Verifies readFieldAsText reads a top-level field's raw text value. */
    private static void testReadFieldAsTextTopLevelField() {
        String testName = "testReadFieldAsTextTopLevelField";
        try {
            String json = "{\"name\":\"Sara\",\"age\":29}";
            assertEquals(testName, "Sara", JSon.readFieldAsText(json, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldAsText follows a path of nested field names to read a nested field's raw text value. */
    private static void testReadFieldAsTextNestedField() {
        String testName = "testReadFieldAsTextNestedField";
        try {
            String json = "{\"address\":{\"city\":\"Tehran\",\"country\":\"Iran\"}}";
            assertEquals(testName, "Tehran", JSon.readFieldAsText(json, "address", "city"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldAsText returns an empty string for a missing top-level field, rather than throwing. */
    private static void testReadFieldAsTextMissingFieldReturnsEmptyString() {
        String testName = "testReadFieldAsTextMissingFieldReturnsEmptyString";
        try {
            String json = "{\"name\":\"Sara\"}";
            assertEquals(testName, "", JSon.readFieldAsText(json, "age"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldAsText returns an empty string when a segment of the nested path is missing. */
    private static void testReadFieldAsTextMissingNestedPathReturnsEmptyString() {
        String testName = "testReadFieldAsTextMissingNestedPathReturnsEmptyString";
        try {
            String json = "{\"address\":{\"city\":\"Tehran\"}}";
            assertEquals(testName, "", JSon.readFieldAsText(json, "address", "zipCode"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // readFieldAsString
    // ------------------------------------------------------------------

    /** Verifies readFieldAsString returns a string field's value with surrounding quotes (full JSON representation). */
    private static void testReadFieldAsStringReturnsQuotedTextForStringField() {
        String testName = "testReadFieldAsStringReturnsQuotedTextForStringField";
        try {
            String json = "{\"name\":\"Sara\"}";
            assertEquals(testName, "\"Sara\"", JSon.readFieldAsString(json, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldAsString returns a numeric field's raw (unquoted) representation. */
    private static void testReadFieldAsStringReturnsRawNumberForNumericField() {
        String testName = "testReadFieldAsStringReturnsRawNumberForNumericField";
        try {
            String json = "{\"age\":29}";
            assertEquals(testName, "29", JSon.readFieldAsString(json, "age"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldAsString returns the full nested JSON structure for an object-valued field. */
    private static void testReadFieldAsStringReturnsFullJsonForObjectField() {
        String testName = "testReadFieldAsStringReturnsFullJsonForObjectField";
        try {
            String json = "{\"address\":{\"city\":\"Tehran\",\"country\":\"Iran\"}}";
            String addressJson = JSon.readFieldAsString(json, "address");
            assertTrue(testName, addressJson.contains("\"city\":\"Tehran\""), "Expected full nested JSON, was: " + addressJson);
            assertTrue(testName, addressJson.contains("\"country\":\"Iran\""), "Expected full nested JSON, was: " + addressJson);
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldAsString returns a non-null string (a "missing node" representation) for a missing field. */
    private static void testReadFieldAsStringReturnsMissingNodeTextForMissingField() {
        String testName = "testReadFieldAsStringReturnsMissingNodeTextForMissingField";
        try {
            String json = "{\"name\":\"Sara\"}";
            // A missing field's toString() representation should not blow up,
            // and should not equal the text of a present field.
            String result = JSon.readFieldAsString(json, "age");
            assertTrue(testName, result != null, "Expected a non-null string for a missing field");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // readFieldArrayAsText / readFieldArrayAsString - happy paths
    // ------------------------------------------------------------------

    /** Verifies readFieldArrayAsText reads a field's raw text value from a valid array index. */
    private static void testReadFieldArrayAsTextValidIndex() {
        String testName = "testReadFieldArrayAsTextValidIndex";
        try {
            String json = "[{\"name\":\"Sara\"},{\"name\":\"Ali\"}]";
            assertEquals(testName, "Ali", JSon.readFieldArrayAsText(json, 1, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldArrayAsString reads a field's quoted string value from a valid array index. */
    private static void testReadFieldArrayAsStringValidIndex() {
        String testName = "testReadFieldArrayAsStringValidIndex";
        try {
            String json = "[{\"name\":\"Sara\"},{\"name\":\"Ali\"}]";
            assertEquals(testName, "\"Sara\"", JSon.readFieldArrayAsString(json, 0, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldArrayAsText returns an empty string for a missing field on a valid array element. */
    private static void testReadFieldArrayAsTextMissingFieldOnElementReturnsEmptyString() {
        String testName = "testReadFieldArrayAsTextMissingFieldOnElementReturnsEmptyString";
        try {
            String json = "[{\"name\":\"Sara\"}]";
            assertEquals(testName, "", JSon.readFieldArrayAsText(json, 0, "age"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Invalid input handling
    // ------------------------------------------------------------------

    /** Verifies read(String, Class) throws an exception for syntactically invalid JSON. */
    private static void testReadInvalidJsonThrowsException() {
        String testName = "testReadInvalidJsonThrowsException";
        try {
            JSon.read("not valid json", Person.class);
            fail(testName, "Expected an exception for invalid JSON but none was thrown");
        } catch (Exception expected) {
            pass(testName);
        }
    }

    /** Verifies read(String, Class) throws an exception when the JSON root's type doesn't match the target class. */
    private static void testReadMismatchedTypeThrowsException() {
        String testName = "testReadMismatchedTypeThrowsException";
        try {
            // "age" expects an int; passing a JSON string for the whole
            // payload where an object is expected should fail to bind.
            JSon.read("\"just a string\"", Person.class);
            fail(testName, "Expected an exception for a type mismatch but none was thrown");
        } catch (Exception expected) {
            pass(testName);
        }
    }

    // ------------------------------------------------------------------
    // readFieldArrayAsText / readFieldArrayAsString - out-of-range and
    // non-array input, handled gracefully via JsonNode.path(...) rather
    // than throwing (see class javadoc).
    // ------------------------------------------------------------------

    /** Verifies readFieldArrayAsText returns an empty string for an out-of-bounds index, rather than throwing. */
    private static void testReadFieldArrayAsTextOutOfBoundsIndexReturnsEmptyString() {
        String testName = "testReadFieldArrayAsTextOutOfBoundsIndexReturnsEmptyString";
        try {
            assertEquals(testName, "", JSon.readFieldArrayAsText("[{\"name\":\"Sara\"}]", 5, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /**
     * Verifies readFieldArrayAsString does not throw for an out-of-bounds index. The exact
     * missing-node text representation isn't asserted (mirrors the same caution taken in
     * {@link #testReadFieldAsStringReturnsMissingNodeTextForMissingField}) - only that it
     * degrades gracefully rather than throwing.
     */
    private static void testReadFieldArrayAsStringOutOfBoundsIndexDoesNotThrow() {
        String testName = "testReadFieldArrayAsStringOutOfBoundsIndexDoesNotThrow";
        try {
            String result = JSon.readFieldArrayAsString("[{\"name\":\"Sara\"}]", 5, "name");
            assertTrue(testName, result != null, "Expected a non-null string for an out-of-bounds index");
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldArrayAsText returns an empty string when the JSON root is not an array, rather than throwing. */
    private static void testReadFieldArrayAsTextNonArrayRootReturnsEmptyString() {
        String testName = "testReadFieldArrayAsTextNonArrayRootReturnsEmptyString";
        try {
            assertEquals(testName, "", JSon.readFieldArrayAsText("{\"name\":\"Sara\"}", 0, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    /** Verifies readFieldArrayAsText returns an empty string for a negative index, rather than throwing. */
    private static void testReadFieldArrayAsTextNegativeIndexReturnsEmptyString() {
        String testName = "testReadFieldArrayAsTextNegativeIndexReturnsEmptyString";
        try {
            assertEquals(testName, "", JSon.readFieldArrayAsText("[{\"name\":\"Sara\"}]", -1, "name"));
        } catch (Exception unexpectedException) {
            fail(testName, "Unexpected exception: " + unexpectedException);
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (deliberately not using any test framework)
    // ------------------------------------------------------------------

    /**
     * Records a pass if {@code expected} equals {@code actual} (null-safe), otherwise a failure.
     *
     * @param testName the name of the test case, printed in the report
     * @param expected the expected value
     * @param actual   the actual value
     */
    private static void assertEquals(String testName, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(testName);
        } else {
            fail(testName, "Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /**
     * Records a pass if {@code condition} is true, otherwise a failure with the given message.
     *
     * @param testName       the name of the test case, printed in the report
     * @param condition      the condition that must be true for the test to pass
     * @param failureMessage the message to report if {@code condition} is false
     */
    private static void assertTrue(String testName, boolean condition, String failureMessage) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName, failureMessage);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        passedCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     * @param reason   why the test failed, printed alongside the test name
     */
    private static void fail(String testName, String reason) {
        failedCount++;
        System.out.println("[FAIL] " + testName + " -> " + reason);
    }
}