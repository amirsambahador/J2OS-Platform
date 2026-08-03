package org.j2os.examples.desktop.jvalidation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Example model (DTO) - each field was chosen to demonstrate one or more
 * specific {@link org.j2os.platform.jvalidation.Validator.Field} methods.
 * This class is just a plain POJO; the validation logic lives in {@link Example}.
 */
public class User {

    /**
     * Demonstrates required(), notBlank(), minLength(), maxLength(), lengthBetween().
     */
    private final String username = "ali_2000";

    /**
     * Demonstrates length() - the exact length must be 4.
     */
    private final String code = "AB12";

    /**
     * Demonstrates contains(), startsWith(), endsWith().
     */
    private final String bio = "developer at IR";

    /**
     * Demonstrates regex(String) with a hand-written email pattern.
     */
    private final String email = "user@example.com";

    /**
     * Demonstrates regex(Pattern) with Patterns.NATIONAL_CODE (no need to call .pattern()).
     */
    private final String nationalCode = "0123456789";

    /**
     * Demonstrates regex(Pattern) with Patterns.URL.
     */
    private final String website = "https://example.com";

    /**
     * Demonstrates date(), past(), minimumAge() - Gregorian birth date.
     */
    private final String birthGregorian = "2000/05/10";

    /**
     * Demonstrates persianDate(), past(), minimumAge() - Persian birth date.
     */
    private final String birthPersian = "1378/12/25";

    /**
     * Demonstrates date() + future() - a Gregorian event in the future.
     */
    private final String futureEventGregorian = "2030/01/01";

    /**
     * Demonstrates persianDate() + future() - a Persian event in the future.
     */
    private final String futureEventPersian = "1410/01/01";

    /**
     * Start of the Gregorian range used to test between(String, String).
     */
    private final String rangeStart = "2020/01/01";

    /**
     * End of the Gregorian range used to test between(String, String).
     */
    private final String rangeEnd = "2020/12/29";

    /**
     * Demonstrates date() + between(start, end), Gregorian.
     */
    private final String rangeValue = "2020/06/15";

    /**
     * Start of the Persian range used to test between(String, String).
     */
    private final String rangeStartPersian = "1399/01/01";

    /**
     * End of the Persian range used to test between(String, String).
     */
    private final String rangeEndPersian = "1399/12/29";

    /**
     * Demonstrates persianDate() + between(start, end), Persian.
     */
    private final String rangeValuePersian = "1399/06/15";

    /**
     * Demonstrates before(otherDate) - must be before laterDate.
     */
    private final String earlierDate = "2019/01/01";

    /**
     * Demonstrates after(otherDate) - must be after earlierDate.
     */
    private final String laterDate = "2021/01/01";

    /**
     * An invalid Gregorian date format - demonstrates that past() no longer
     * throws a raw exception on it, but instead records a validation error
     * with code DATE_CONTEXT_MISSING.
     */
    private final String invalidDateGregorian = "2020/13/40";

    /**
     * Demonstrates min(), max(), between(double, double), positive(), digits() on a BigDecimal.
     */
    private final BigDecimal price = new BigDecimal("1234.56");

    /**
     * An integer value with trailing zeros - demonstrates the fix for the
     * digits() bug that previously miscounted integer digits because of
     * stripTrailingZeros() + precision()/scale().
     */
    private final BigDecimal roundedPrice = new BigDecimal("1200.00");

    /**
     * Demonstrates number() on a valid numeric string field (followed by min/max on the same string).
     */
    private final String quantityText = "42";

    /**
     * Demonstrates number() on an invalid numeric string field (should produce a NUMBER error).
     */
    private final String invalidNumberText = "abc";

    /**
     * A non-numeric string checked directly with min() (without calling number() first) -
     * demonstrates that a raw IllegalArgumentException is no longer thrown, and a MIN
     * error is recorded instead.
     */
    private final String nonNumericMin = "not-a-number";

    /**
     * Demonstrates isTrue() on an actual Boolean type.
     */
    private final Boolean accepted = Boolean.TRUE;

    /**
     * Demonstrates isFalse() on an actual Boolean type.
     */
    private final Boolean newsletter = Boolean.FALSE;

    /**
     * Demonstrates bool() + isTrue() on a string field ("TRUE" in uppercase, case-insensitive).
     */
    private final String acceptedText = "TRUE";

    /**
     * Demonstrates bool() + isFalse() on a string field.
     */
    private final String rejectedText = "false";

    /**
     * Demonstrates bool() on an invalid string field (should produce a BOOLEAN error).
     */
    private final String invalidBoolText = "yep";

    /**
     * Demonstrates notEmpty() and an exact size(3) on a Collection.
     */
    private final List<String> tagsExact = List.of("a", "b", "c");

    /**
     * Demonstrates minSize() and maxSize() on a Collection.
     */
    private final List<String> tagsRange = List.of("x", "y");

    /**
     * Demonstrates unique() on a Collection.
     */
    private final List<String> tagsUnique = List.of("a", "b", "c");

    /**
     * Demonstrates equalTo(otherField) - must equal confirmPassword.
     */
    private final String password = "Passw0rd!";

    /**
     * The counterpart used by equalTo() for the password field.
     */
    private final String confirmPassword = "Passw0rd!";

    /**
     * Activation condition for the when()/endWhen() block.
     */
    private final boolean isCompany = true;

    /**
     * Only active when isCompany == true (inside the when/endWhen block).
     */
    private final String companyName = "Acme Co";

    /**
     * Demonstrates withMethod() - custom rule: must start with the SUMMER prefix.
     */
    private final String couponCode = "SUMMER10";

    public String getUsername() {
        return username;
    }

    public String getCode() {
        return code;
    }

    public String getBio() {
        return bio;
    }

    public String getEmail() {
        return email;
    }

    public String getNationalCode() {
        return nationalCode;
    }

    public String getWebsite() {
        return website;
    }

    public String getBirthGregorian() {
        return birthGregorian;
    }

    public String getBirthPersian() {
        return birthPersian;
    }

    public String getFutureEventGregorian() {
        return futureEventGregorian;
    }

    public String getFutureEventPersian() {
        return futureEventPersian;
    }

    public String getRangeStart() {
        return rangeStart;
    }

    public String getRangeEnd() {
        return rangeEnd;
    }

    public String getRangeValue() {
        return rangeValue;
    }

    public String getRangeStartPersian() {
        return rangeStartPersian;
    }

    public String getRangeEndPersian() {
        return rangeEndPersian;
    }

    public String getRangeValuePersian() {
        return rangeValuePersian;
    }

    public String getEarlierDate() {
        return earlierDate;
    }

    public String getLaterDate() {
        return laterDate;
    }

    public String getInvalidDateGregorian() {
        return invalidDateGregorian;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getRoundedPrice() {
        return roundedPrice;
    }

    public String getQuantityText() {
        return quantityText;
    }

    public String getInvalidNumberText() {
        return invalidNumberText;
    }

    public String getNonNumericMin() {
        return nonNumericMin;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public Boolean getNewsletter() {
        return newsletter;
    }

    public String getAcceptedText() {
        return acceptedText;
    }

    public String getRejectedText() {
        return rejectedText;
    }

    public String getInvalidBoolText() {
        return invalidBoolText;
    }

    public List<String> getTagsExact() {
        return tagsExact;
    }

    public List<String> getTagsRange() {
        return tagsRange;
    }

    public List<String> getTagsUnique() {
        return tagsUnique;
    }

    public String getPassword() {
        return password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public boolean isCompany() {
        return isCompany;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCouponCode() {
        return couponCode;
    }
}