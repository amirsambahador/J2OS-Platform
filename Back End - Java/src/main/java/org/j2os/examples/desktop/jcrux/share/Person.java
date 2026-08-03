package org.j2os.examples.desktop.jcrux.share;

import java.io.Serializable;

/**
 * Example domain class used to demonstrate storing, invoking, and mutating an
 * object through JCrux.
 * <p>
 * Per JCrux's usage constraints, classes shared through JCrux must not use
 * primitive fields — this class uses {@link Integer} instead of {@code int}
 * for {@link #age} for that reason.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Person implements Serializable {

    /**
     * This person's name.
     */
    private String name;

    /**
     * This person's age, in years.
     */
    private Integer age;

    /**
     * Creates a person with no name or age set.
     */
    public Person() {
    }

    /**
     * Creates a person with the given name and age.
     *
     * @param name the person's name
     * @param age  the person's age, in years
     */
    public Person(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

    /**
     * Returns this person's name.
     *
     * @return the person's name
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
     * @return the person's age, in years
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
     * Builds a greeting introducing this person by name and age.
     *
     * @return a greeting string
     */
    public String greet() {
        return "Hello, my name is " + name + " and I'm " + age + " years old.";
    }

    /**
     * Builds an introduction using an alternate nickname alongside this person's real name.
     *
     * @param nickname the nickname to introduce this person as
     * @return an introduction string
     */
    public String introduceAs(String nickname) {
        return "You can call me " + nickname + " (" + name + ")";
    }

    /**
     * Returns a debug-friendly string representation of this person.
     *
     * @return a string containing this person's name and age
     */
    @Override
    public String toString() {
        return "Person{name='" + name + "', age=" + age + '}';
    }
}