package org.j2os.examples.desktop.jutil;

import org.j2os.platform.jutil.json.JSon;

/**
 * Simple, self-contained tutorial that demonstrates the most common ways to
 * use {@link JSon}.
 * <p>
 * This class is meant purely for learning purposes: each method below
 * focuses on a single feature and prints its result to the console so you
 * can read the output and see exactly what each API call does. It does not
 * perform any assertions and is not a test.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JSonExample {

    /**
     * Simple POJO used throughout the examples below. Jackson auto-detects
     * public fields, so no getters/setters or annotations are required for
     * this demonstration.
     */
    public static class Person {
        public String name;
        public int age;
        public Address address;

        /** No-arg constructor required by Jackson for deserialization. */
        public Person() {
        }

        public Person(String name, int age, Address address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
    }

    /** Nested POJO, used to demonstrate reading nested fields. */
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

    public static void main(String[] args) {
        writeObjectToJson();
        readJsonIntoObject();
        readTopLevelFieldAsText();
        readNestedFieldAsText();
        readFieldAsRawJsonString();
        readFieldFromArrayElement();
    }

    /**
     * Shows how to serialize a Java object into a JSON string with
     * {@link JSon#write(Object)}.
     */
    private static void writeObjectToJson() {
        System.out.println("--- write(Object) ---");

        Person person = new Person("Sara", 29, new Address("Tehran", "Iran"));
        String json = JSon.write(person);

        System.out.println("Person object -> JSON:");
        System.out.println(json);
        System.out.println();
    }

    /**
     * Shows how to deserialize a JSON string into a Java object with
     * {@link JSon#read(String, Class)}.
     */
    private static void readJsonIntoObject() {
        System.out.println("--- read(String, Class) ---");

        String json = "{\"name\":\"Sara\",\"age\":29,\"address\":{\"city\":\"Tehran\",\"country\":\"Iran\"}}";
        Person person = JSon.read(json, Person.class);

        System.out.println("JSON -> Person object:");
        System.out.println("name = " + person.name);
        System.out.println("age = " + person.age);
        System.out.println("address.city = " + person.address.city);
        System.out.println();
    }

    /**
     * Shows how to read a top-level field's text value with
     * {@link JSon#readFieldAsText(String, String...)}.
     */
    private static void readTopLevelFieldAsText() {
        System.out.println("--- readFieldAsText(String, String...) : top-level field ---");

        String json = "{\"name\":\"Sara\",\"age\":29}";
        String name = JSon.readFieldAsText(json, "name");

        System.out.println("json = " + json);
        System.out.println("readFieldAsText(json, \"name\") = " + name); // Sara
        System.out.println();
    }

    /**
     * Shows how to follow a path of nested field names with
     * {@link JSon#readFieldAsText(String, String...)}, and what happens
     * when a field in the path does not exist.
     */
    private static void readNestedFieldAsText() {
        System.out.println("--- readFieldAsText(String, String...) : nested field ---");

        String json = "{\"name\":\"Sara\",\"address\":{\"city\":\"Tehran\",\"country\":\"Iran\"}}";
        String city = JSon.readFieldAsText(json, "address", "city");
        String missing = JSon.readFieldAsText(json, "address", "zipCode"); // field does not exist

        System.out.println("json = " + json);
        System.out.println("readFieldAsText(json, \"address\", \"city\") = " + city);       // Tehran
        System.out.println("readFieldAsText(json, \"address\", \"zipCode\") = \"" + missing + "\""); // "" (missing field)
        System.out.println();
    }

    /**
     * Shows the difference between {@link JSon#readFieldAsText(String, String...)}
     * (raw text content) and {@link JSon#readFieldAsString(String, String...)}
     * (full JSON representation, including quotes for strings and full
     * structure for objects/arrays).
     */
    private static void readFieldAsRawJsonString() {
        System.out.println("--- readFieldAsText vs readFieldAsString ---");

        String json = "{\"name\":\"Sara\",\"address\":{\"city\":\"Tehran\",\"country\":\"Iran\"}}";

        String nameAsText = JSon.readFieldAsText(json, "name");
        String nameAsString = JSon.readFieldAsString(json, "name");
        String addressAsString = JSon.readFieldAsString(json, "address");

        System.out.println("readFieldAsText(json, \"name\")    = " + nameAsText);      // Sara            (no quotes)
        System.out.println("readFieldAsString(json, \"name\")  = " + nameAsString);    // "Sara"          (with quotes)
        System.out.println("readFieldAsString(json, \"address\") = " + addressAsString); // full nested JSON object
        System.out.println();
    }

    /**
     * Shows how to read a field from a specific element of a JSON array
     * with {@link JSon#readFieldArrayAsText(String, int, String)} and
     * {@link JSon#readFieldArrayAsString(String, int, String)}.
     * <p>
     * <b>Note:</b> both methods throw a {@link NullPointerException} if
     * {@code index} is out of bounds, or if the JSON root is not an array
     * at all - this is existing behavior of {@link JSon}, not something
     * this tutorial works around.
     */
    private static void readFieldFromArrayElement() {
        System.out.println("--- readFieldArrayAsText / readFieldArrayAsString ---");

        String json = "[{\"name\":\"Sara\"},{\"name\":\"Ali\"}]";

        String firstName = JSon.readFieldArrayAsText(json, 0, "name");
        String secondNameAsString = JSon.readFieldArrayAsString(json, 1, "name");

        System.out.println("json = " + json);
        System.out.println("readFieldArrayAsText(json, 0, \"name\")   = " + firstName);        // Sara
        System.out.println("readFieldArrayAsString(json, 1, \"name\") = " + secondNameAsString); // "Ali"
    }
}