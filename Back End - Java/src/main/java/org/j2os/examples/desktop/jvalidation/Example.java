package org.j2os.examples.desktop.jvalidation;

import org.j2os.platform.jvalidation.Patterns;
import org.j2os.platform.jvalidation.ValidationException;
import org.j2os.platform.jvalidation.ValidationResult;
import org.j2os.platform.jvalidation.Validator;

/**
 * Demonstrates {@link Validator}'s rules, grouped by topic (string, numeric, boolean, date,
 * collection, cross-field/conditional/custom) so each concept can be read and run on its own,
 * plus separate demos of {@code message()}/{@code messageKey()} and {@code validateOrThrow()}.
 * <p>
 * All sample values live on {@link User} - each of its fields is documented there with which
 * rule(s) it's meant to exercise.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example {

    public static void main(String[] args) {
        User user = new User();

        demoStringRules(user);
        demoNumericRules(user);
        demoBooleanRules(user);
        demoGregorianDateRules(user);
        demoPersianDateRules(user);
        demoCollectionRules(user);
        demoCrossFieldConditionalAndCustomRules(user);
        demoMessageCustomization(user);
        demoValidateOrThrow(user);
    }

    /** Demonstrates required/notBlank/length rules on strings, plus contains/startsWith/endsWith and regex. */
    private static void demoStringRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getUsername)
                .required()           // must not be null and must not be "" (empty string); a whitespace-only string (" ") passes, since this only checks length zero
                .notBlank()           // must not be null, must not be "", and must not consist only of whitespace/tab/newline; stricter than required()
                .minLength(5)         // string length must be at least 5 characters
                .maxLength(20)        // string length must be at most 20 characters
                .lengthBetween(5, 20) // string length must be between 5 and 20 characters (equivalent to combining the two rules above into one)

                .field(User::getCode)
                .length(4) // string length must be exactly 4 characters, no more, no less

                .field(User::getBio)
                .contains("developer")  // the string must contain the word "developer" somewhere (not necessarily at the start/end)
                .startsWith("developer") // the string must start exactly with the word "developer"
                .endsWith("IR")          // the string must end exactly with "IR"

                .field(User::getEmail)
                .regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") // matches a custom regex pattern that you write yourself as a string

                .field(User::getNationalCode)
                .regex(Patterns.NATIONAL_CODE) // matches a pre-compiled Pattern from the Patterns class (here: exactly 10 digits)

                .field(User::getWebsite)
                .regex(Patterns.URL) // matches the ready-made Patterns.URL pattern (must start with http:// or https://)

                .validate();

        printResult("String rules", result);
    }

    /** Demonstrates min/max/between/positive/digits on numbers, and number() on numeric strings. */
    private static void demoNumericRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getPrice)
                .min(100)           // the numeric value must be at least 100 (inclusive)
                .max(5000)          // the numeric value must be at most 5000 (inclusive)
                .between(100, 5000) // the numeric value must fall within [100, 5000]; equivalent to combining min and max above
                .positive()         // the numeric value must be greater than zero
                .digits(4, 2)       // at most 4 integer digits and at most 2 fraction digits are allowed

                .field(User::getRoundedPrice)
                .digits(4, 2) // 1200.00 -> after the digits() bug fix: 4 integer digits and 0 fraction digits, so this rule should pass

                .field(User::getQuantityText)
                .number() // checks the string's numeric format ("42" is valid); if invalid, a NUMBER error is recorded instead of an exception
                .min(1)   // after number(), the usual numeric rule methods can also be used on this string
                .max(100) // the string is converted to a number and compared against 100

                .field(User::getInvalidNumberText)
                .number() // because the value is "abc", this rule fails and a NUMBER error is recorded (no raw exception is thrown)

                .field(User::getNonNumericMin)
                .min(1) // the value is "not-a-number" and number() was not called; instead of a raw IllegalArgumentException, a MIN error is recorded

                .validate();

        printResult("Numeric rules", result);
    }

    /** Demonstrates isTrue()/isFalse() on actual Booleans, and bool()+isTrue()/isFalse() on strings. */
    private static void demoBooleanRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getAccepted)
                .isTrue() // the Boolean value must be true

                .field(User::getNewsletter)
                .isFalse() // the Boolean value must be false

                .field(User::getAcceptedText)
                .bool()   // checks the string's boolean format; must be exactly "true"/"false" (case-insensitive)
                .isTrue() // because the value is "TRUE", this rule also passes (isTrue also works on strings)

                .field(User::getRejectedText)
                .bool()    // checks the string's boolean format
                .isFalse() // because the value is "false", this rule passes

                .field(User::getInvalidBoolText)
                .bool() // because the value is "yep", this rule fails and a BOOLEAN error is recorded

                .validate();

        printResult("Boolean rules", result);
    }

    /** Demonstrates date()/past()/future()/minimumAge()/between()/before()/after() on Gregorian dates. */
    private static void demoGregorianDateRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getBirthGregorian)
                .date()         // checks the yyyy/MM/dd format and requires a genuinely valid Gregorian date (e.g. 02/30 is rejected)
                .past()         // the date must be before today
                .minimumAge(18) // the gap between this date and today must be at least 18 complete years

                .field(User::getFutureEventGregorian)
                .date()   // validates the Gregorian format and correctness
                .future() // the date must be after today

                .field(User::getRangeValue)
                .date()                                           // validates the Gregorian format and correctness
                .between(user.getRangeStart(), user.getRangeEnd()) // the date must fall within [rangeStart, rangeEnd]

                .field(User::getLaterDate)
                .date()                       // validates the Gregorian format and correctness
                .after(user.getEarlierDate()) // the date must be after the given date (earlierDate)

                .field(User::getEarlierDate)
                .date()                     // validates the Gregorian format and correctness
                .before(user.getLaterDate()) // the date must be before the given date (laterDate)

                .field(User::getInvalidDateGregorian)
                .date() // the format "2020/13/40" is invalid, so this rule fails and the DATE error code is recorded
                .past() // since date() did not succeed, dateKind was never set; instead of a raw IllegalStateException, a DATE_CONTEXT_MISSING error is recorded

                .validate();

        printResult("Gregorian date rules", result);
    }

    /** Demonstrates persianDate()/past()/future()/minimumAge()/between() on Persian dates. */
    private static void demoPersianDateRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getBirthPersian)
                .persianDate()  // checks the yyyy/MM/dd format and requires a genuinely valid Persian date (leap years are also honored)
                .past()         // the date must be before today (today is also computed in the Persian calendar here)
                .minimumAge(18) // the gap to today (in the Persian calendar) must be at least 18 complete years

                .field(User::getFutureEventPersian)
                .persianDate() // validates the Persian format and correctness
                .future()      // the date must be after today (Persian)

                .field(User::getRangeValuePersian)
                .persianDate()                                                  // validates the Persian format and correctness
                .between(user.getRangeStartPersian(), user.getRangeEndPersian()) // the date must fall within the given Persian range

                .validate();

        printResult("Persian date rules", result);
    }

    /** Demonstrates notEmpty()/size()/minSize()/maxSize()/unique() on collections. */
    private static void demoCollectionRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getTagsExact)
                .notEmpty() // the collection (List/Set/Map/Array) must not be empty; only works on collections, not String
                .size(3)    // the collection must have exactly 3 elements

                .field(User::getTagsRange)
                .minSize(1) // the collection must have at least 1 element
                .maxSize(5) // the collection must have at most 5 elements

                .field(User::getTagsUnique)
                .unique() // every element in the collection must be unique (no duplicate values)

                .validate();

        printResult("Collection rules", result);
    }

    /** Demonstrates equalTo() (cross-field), when()/endWhen() (conditional), and withMethod() (fully custom rule). */
    private static void demoCrossFieldConditionalAndCustomRules(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getPassword)
                .equalTo(User::getConfirmPassword) // this field's value must exactly equal another field's value on the same object (here: confirmPassword)

                .when(User::isCompany) // conditional block: the following rules only run when this condition is true
                .field(User::getCompanyName)
                .required() // since isCompany==true, this rule is actually evaluated and checked
                .endWhen()  // end of the conditional block; rules after this always run (unconditionally)

                .field(User::getCouponCode)
                .withMethod((u, v) -> v != null && v.startsWith("SUMMER"), // a fully custom rule with access to the whole object (u) and the field value (v)
                        "COUPON_PREFIX", "couponCode must start with SUMMER") // the error code and message to use if the rule fails

                .validate();

        printResult("Cross-field, conditional, and custom rules", result);
    }

    /** Demonstrates overriding a failed rule's message and attaching a message key (for i18n) with message()/messageKey(). */
    private static void demoMessageCustomization(User user) {
        ValidationResult result = Validator.of(user)
                .field(User::getUsername)
                .minLength(100)                       // deliberately fails, so the effect of message()/messageKey() is visible
                .message("Username is too short")     // replaces the message text of the most recently failed rule with a custom message
                .messageKey("user.username.tooShort") // sets a message key (for localization/i18n) on the same error
                .validate();

        System.out.println("--- message()/messageKey() ---");
        result.errors().forEach(e ->
                System.out.println("message=" + e.getMessage() + " | messageKey=" + e.getMessageKey()));
        System.out.println();
    }

    /** Demonstrates validateOrThrow(), which behaves like validate() but throws ValidationException on any error. */
    private static void demoValidateOrThrow(User user) {
        System.out.println("--- validateOrThrow() ---");
        try {
            Validator.of(user)
                    .field(User::getUsername).minLength(100) // deliberately fails
                    .validateOrThrow();
        } catch (ValidationException e) {
            System.out.println("validateOrThrow correctly threw: " + e.getMessage());
        }
    }

    /**
     * Prints a validation result's pass/fail status, error count, and every individual error.
     *
     * @param title  a short label identifying which demo this result came from
     * @param result the result to print
     */
    private static void printResult(String title, ValidationResult result) {
        System.out.println("--- " + title + " ---");
        System.out.println("Valid: " + result.isValid());
        System.out.println("Error count: " + result.errorCount());
        result.errors().forEach(System.out::println);
        System.out.println();
    }
}