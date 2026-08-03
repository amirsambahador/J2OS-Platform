package org.j2os.test.page2;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Minimal JPA entity used only by {@code Page2Test} to exercise {@link
 * org.j2os.platform.page2.PageDataEntity} and {@link org.j2os.platform.page2.PageDataJPQL}
 * against a real (in-memory H2) database, since the project's real entities were not supplied
 * alongside the library source files this suite was written against.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@Entity
public class TestPerson {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** This person's name. */
    private String name;

    /** This person's age, in years. */
    private Integer age;

    /** Required no-arg constructor for JPA. */
    public TestPerson() {
    }

    /**
     * Creates a test person with a name and age (id is assigned on persist).
     *
     * @param name the person's name
     * @param age  the person's age, in years
     */
    public TestPerson(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

    /**
     * Returns this person's id.
     *
     * @return the id, or {@code null} if not yet persisted
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns this person's name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets this person's name.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns this person's age.
     *
     * @return the age, in years
     */
    public Integer getAge() {
        return age;
    }

    /**
     * Sets this person's age.
     *
     * @param age the new age, in years
     */
    public void setAge(Integer age) {
        this.age = age;
    }

    /**
     * Returns a debug-friendly string representation of this person.
     *
     * @return a string containing this person's fields
     */
    @Override
    public String toString() {
        return "TestPerson{id=" + id + ", name='" + name + "', age=" + age + '}';
    }
}