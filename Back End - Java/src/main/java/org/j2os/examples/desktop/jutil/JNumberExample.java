package org.j2os.examples.desktop.jutil;

import org.j2os.platform.jutil.number.JNumber;

/**
 * Simple, self-contained tutorial that demonstrates the most common ways to
 * use {@link JNumber}.
 * <p>
 * This class is meant purely for learning purposes: each method below
 * focuses on a single feature and prints its result to the console so you
 * can read the output and see exactly what each API call does. It does not
 * perform any assertions and is not a test.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JNumberExample {

    public static void main(String[] args) {
        spellOutNumbersInEnglish();
        spellOutNumbersInPersian();
        formatLatinNumberAsPersian();
        formatPersianNumberAsEnglish();
        stripSeparatorsFromFormattedNumber();
    }

    /**
     * Shows how to convert numeric strings into their spelled-out English
     * word form with {@link JNumber#getEnglishWords(String)}.
     */
    private static void spellOutNumbersInEnglish() {
        System.out.println("--- getEnglishWords(String) ---");

        System.out.println("123      -> " + JNumber.getEnglishWords("123"));
        System.out.println("1000     -> " + JNumber.getEnglishWords("1000"));
        System.out.println("-45.6    -> " + JNumber.getEnglishWords("-45.6"));
        System.out.println("0        -> " + JNumber.getEnglishWords("0"));
        System.out.println();
    }

    /**
     * Shows how to convert numeric strings into their spelled-out Persian
     * word form with {@link JNumber#getPersianWords(String)}.
     */
    private static void spellOutNumbersInPersian() {
        System.out.println("--- getPersianWords(String) ---");

        System.out.println("123      -> " + JNumber.getPersianWords("123"));
        System.out.println("1000     -> " + JNumber.getPersianWords("1000"));
        System.out.println("-45.6    -> " + JNumber.getPersianWords("-45.6"));
        System.out.println("0        -> " + JNumber.getPersianWords("0"));
        System.out.println();
    }

    /**
     * Shows how to format a Latin-digit number into its Persian-digit,
     * comma-grouped representation with {@link JNumber#getPersianNumber(String)}.
     */
    private static void formatLatinNumberAsPersian() {
        System.out.println("--- getPersianNumber(String) ---");

        System.out.println("1234567   -> " + JNumber.getPersianNumber("1234567"));
        System.out.println("-1234.5   -> " + JNumber.getPersianNumber("-1234.5"));
        System.out.println();
    }

    /**
     * Shows how to format a Persian-digit number into its Latin-digit,
     * comma-grouped representation with {@link JNumber#getEnglishNumber(String)}.
     */
    private static void formatPersianNumberAsEnglish() {
        System.out.println("--- getEnglishNumber(String) ---");

        System.out.println("۱۲۳۴۵۶۷   -> " + JNumber.getEnglishNumber("۱۲۳۴۵۶۷"));
        // Stray characters (spaces, letters, ...) are stripped before formatting,
        // just like getPersianNumber does.
        System.out.println(" 1234     -> " + JNumber.getEnglishNumber(" 1234 "));
        System.out.println();
    }

    /**
     * Shows how to strip thousands separators (and normalize the decimal
     * point) from an already-formatted number with
     * {@link JNumber#getEnglishNumberWithoutCommas(String)}.
     */
    private static void stripSeparatorsFromFormattedNumber() {
        System.out.println("--- getEnglishNumberWithoutCommas(String) ---");

        System.out.println("1,234,567 -> " + JNumber.getEnglishNumberWithoutCommas("1,234,567"));
        System.out.println("1,234٫56  -> " + JNumber.getEnglishNumberWithoutCommas("1,234٫56"));
    }
}