package org.j2os.examples.desktop.jsecurity;

import org.j2os.platform.jsecurity.access.RequestAccessControl;

/**
 * Demonstrates {@link RequestAccessControl}: field-level restrictions with and without an
 * {@code oldTarget} fallback, and a full action denial.
 * <p>
 * The scope ("SHARGH", "GHARB", "TEST") and action ("INSERT", "HAR ESMI") strings used below
 * are arbitrary caller-defined identifiers — {@code RequestAccessControl} attaches no built-in
 * meaning to them, it only matches registrations and {@code apply()} calls that use the exact
 * same strings.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class RequestAccessControlExample {

    private static final String SEPARATOR = "________________________________________";

    private RequestAccessControlExample() {
    }

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        RequestAccessControl.registerFieldLimitation("SHARGH", "org.j2os.examples.desktop.jsecurity.PersonEntity", "name", "INSERT");
        RequestAccessControl.registerFieldLimitation("SHARGH", "org.j2os.examples.desktop.jsecurity.PersonEntity", "family", "INSERT");
        RequestAccessControl.registerFieldLimitation("SHARGH", "org.j2os.examples.desktop.jsecurity.PersonEntity", "age", "INSERT");
        RequestAccessControl.registerActionDenial("GHARB", "org.j2os.examples.desktop.jsecurity.PersonEntity", "INSERT");
        RequestAccessControl.registerFieldLimitation("TEST", "org.j2os.examples.desktop.jsecurity.PersonEntity", "age", "HAR ESMI");

        PersonEntity person = new PersonEntity();
        person.setName("Amirsam");
        person.setFamily("Bahador");
        person.setAge(40);
        person.setSchool("J2OS");

        // scope=SHARGH restricts the name/family/age fields for action=INSERT,
        // so all three become null on the copy; school is not restricted and stays untouched.
        PersonEntity newPerson = RequestAccessControl.apply("SHARGH", person, "INSERT");
        System.out.println(newPerson.getName());    // null   (restricted field, no oldTarget)
        System.out.println(newPerson.getFamily());  // null   (restricted field, no oldTarget)
        System.out.println(newPerson.getAge());     // null   (restricted field, no oldTarget)
        System.out.println(newPerson.getSchool());  // J2OS   (not restricted)
        System.out.println(SEPARATOR);

        PersonEntity oldPerson = new PersonEntity();
        oldPerson.setName("Reza");
        oldPerson.setFamily("Jamshidi");
        oldPerson.setAge(70);
        oldPerson.setSchool("beheshti");

        // The same three fields are restricted, but this time an oldTarget is given, so instead
        // of null, each field's value comes back from oldPerson.
        newPerson = RequestAccessControl.apply("SHARGH", person, oldPerson, "INSERT");
        System.out.println(newPerson.getName());    // Reza      (from oldPerson.name)
        System.out.println(newPerson.getFamily());  // Jamshidi  (from oldPerson.family)
        System.out.println(newPerson.getAge());     // 70        (from oldPerson.age)
        System.out.println(newPerson.getSchool());  // J2OS      (not restricted - from person itself)
        System.out.println(SEPARATOR);

        // scope=GHARB has a full denial (deny-all) registered for action=INSERT - not a
        // field-level restriction. apply() here never returns a copy at all; it throws
        // DeniedException right at the start.
        try {
            RequestAccessControl.apply("GHARB", person, "INSERT");
            System.out.println("This line never runs");
        } catch (RequestAccessControl.DeniedException deniedException) {
            System.out.println("Denied: " + deniedException.getMessage()); // Denied: INSERT
        }

        // The 4-argument overload of apply behaves exactly the same for deny-all - having an
        // oldTarget makes no difference to deny-all, since the DENY_ALL_MARKER check runs
        // before any other logic.
        try {
            RequestAccessControl.apply("GHARB", person, oldPerson, "INSERT");
            System.out.println("This line never runs");
        } catch (RequestAccessControl.DeniedException deniedException) {
            System.out.println("Denied: " + deniedException.getMessage()); // Denied: INSERT
        }
        System.out.println(SEPARATOR);

        // scope=TEST only restricts age for action=HAR ESMI.
        newPerson = RequestAccessControl.apply("TEST", person, "HAR ESMI");
        System.out.println(newPerson.getName());    // Amirsam (not restricted)
        System.out.println(newPerson.getFamily());  // Bahador (not restricted)
        System.out.println(newPerson.getAge());     // null    (restricted field, no oldTarget)
        System.out.println(newPerson.getSchool());  // J2OS    (not restricted)
        System.out.println(SEPARATOR);

        // Same restriction (age only), but this time with an oldTarget - so age gets
        // oldPerson.age's value instead of null.
        newPerson = RequestAccessControl.apply("TEST", person, oldPerson, "HAR ESMI");
        System.out.println(newPerson.getName());    // Amirsam (not restricted)
        System.out.println(newPerson.getFamily());  // Bahador (not restricted)
        System.out.println(newPerson.getAge());     // 70      (from oldPerson.age)
        System.out.println(newPerson.getSchool());  // J2OS    (not restricted)
    }
}