package org.j2os.examples.desktop.page2;

import org.j2os.platform.page2.PageDataList;
import org.j2os.platform.page2.PageDataResultFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Plain, dependency-free demonstration of {@link PageDataList} (filter/search/sort/page an
 * in-memory list) and {@link PageDataResultFilter} (post-process the resulting rows), using a
 * small local {@link Person} domain class instead of the project's real JPA entities.
 * <p>
 * This mirrors the shape of the Spring {@code PersonAPI} usage this example was based on, but
 * as a plain {@code main} method with no Spring/JPA dependency, so it can run standalone.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example {

    /**
     * Runs the demonstration.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        demoFilterSearchSortPage();
        demoResultFilterPostProcessing();
    }

    /** Demonstrates {@link PageDataList}: filtering, free-text search, sorting, and paging. */
    private static void demoFilterSearchSortPage() {
        System.out.println("=== PageDataList: filter + search + sort + page ===");

        List<Person> people = samplePeople();

        PageDataList pageDataList = new PageDataList();
        Map<String, Object> result = pageDataList
                .searchAndSortOn("id", "firstName", "lastName", "carFactory", "carName")
                .where("carFactory", "LIKE", "toyota")
                .or("carFactory", "LIKE", "KIA")
                .or("carName", "LIKE", "JETA")
                .getResult(people, Map.of("sort", "id", "order", "ASC", "page", "1", "rows", "10"));

        System.out.println("total=" + result.get("total"));
        for (Object row : (List<?>) result.get("rows")) {
            System.out.println("  " + row);
        }
    }

    /** Demonstrates {@link PageDataResultFilter}: removing, blanking, and adding fields on the rows of a result. */
    private static void demoResultFilterPostProcessing() {
        System.out.println("\n=== PageDataResultFilter: post-process the rows of a result ===");

        List<Person> people = samplePeople();
        PageDataList pageDataList = new PageDataList();
        Map<String, Object> rawResult = pageDataList
                .searchAndSortOn("id", "firstName", "lastName")
                .getResult(people, Map.of("sort", "id"));

        PageDataResultFilter<Person> filter = new PageDataResultFilter<>(rawResult);
        Map<String, Object> filtered = filter
                // Plain removal.
                .remove("firstName")
                // Blank out a field.
                .empty("lastName")
                // Replace a field's value (output can be of any type).
                .put("fullName", person -> person.getFirstName() + " " + person.getLastName())
                // Add a computed list field.
                .put("languages", person -> Arrays.asList("English", "Persian"))
                .getResult();

        for (Object row : (List<?>) filtered.get("rows")) {
            System.out.println("  " + row);
        }
    }

    /**
     * Builds the sample in-memory people used by both demos.
     *
     * @return five sample {@link Person} records
     */
    private static List<Person> samplePeople() {
        List<Person> people = new ArrayList<>();
        people.add(new Person(1, "Mohammad", "Ghaderi", "BMW", null));
        people.add(new Person(2, "Amirsam", "Bahador", "KIA", "CERATO"));
        people.add(new Person(3, "Farid", "Ghaderi", "FW", "JETA"));
        people.add(new Person(4, "Ali", "Ghaderi", "IK", "206"));
        people.add(new Person(5, "Erfan", "Entezari", "TOYOTA", "COROLLA"));
        return people;
    }

    /** Minimal local domain class standing in for the project's real JPA {@code Person} entity. */
    private static class Person {

        /** This person's id. */
        private final int id;

        /** This person's first name. */
        private final String firstName;

        /** This person's last name. */
        private final String lastName;

        /** The manufacturer of this person's car. */
        private final String carFactory;

        /** The model name of this person's car, or {@code null} if they have none on file. */
        private final String carName;

        /**
         * Creates a person.
         *
         * @param id         the person's id
         * @param firstName  the person's first name
         * @param lastName   the person's last name
         * @param carFactory the manufacturer of the person's car
         * @param carName    the model name of the person's car, or {@code null} if none
         */
        Person(int id, String firstName, String lastName, String carFactory, String carName) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.carFactory = carFactory;
            this.carName = carName;
        }

        /**
         * Returns this person's id.
         *
         * @return the id
         */
        public int getId() {
            return id;
        }

        /**
         * Returns this person's first name.
         *
         * @return the first name
         */
        public String getFirstName() {
            return firstName;
        }

        /**
         * Returns this person's last name.
         *
         * @return the last name
         */
        public String getLastName() {
            return lastName;
        }

        /**
         * Returns the manufacturer of this person's car.
         *
         * @return the car's manufacturer
         */
        public String getCarFactory() {
            return carFactory;
        }

        /**
         * Returns the model name of this person's car.
         *
         * @return the car's model name, or {@code null} if the person has no car on file
         */
        public String getCarName() {
            return carName;
        }

        /**
         * Returns a debug-friendly string representation of this person.
         *
         * @return a string containing this person's fields
         */
        @Override
        public String toString() {
            return "Person{id=" + id + ", firstName='" + firstName + "', lastName='" + lastName
                    + "', carFactory='" + carFactory + "', carName='" + carName + "'}";
        }
    }
}