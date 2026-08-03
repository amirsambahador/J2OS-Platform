package org.j2os.platform.jutil.number;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static-style numeric formatting utility that provides:
 * <ul>
 *   <li>Conversion of a numeric string into its English or Persian
 *       spelled-out word form (e.g. {@code "123"} -&gt; {@code "One Hundred Twenty Three"}
 *       or {@code "صد و بیست و سه"}), including optional decimal parts.</li>
 *   <li>Conversion between Latin-digit and Persian-digit representations of
 *       a number, with thousands separators ({@code ,}) and a Persian
 *       decimal separator ({@code ٫}).</li>
 * </ul>
 * <p>
 * This class is annotated with Lombok's {@link UtilityClass}, which makes
 * every member implicitly {@code static}, generates a private constructor,
 * and marks the class {@code final} - so it can be used directly as
 * {@code JNumber.getEnglishWords(...)}, etc. without needing to instantiate
 * it. (Note: the source also declares several members with an explicit
 * {@code static} modifier; under {@code @UtilityClass} this is redundant
 * but harmless.)
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@UtilityClass
public class JNumber {

    /** English words for single digits 0-9, used within a three-digit group ("" for 0). */
    private static final String[] ONES_EN = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"
    };

    /** English words for the numbers 10-19. */
    private static final String[] TEENS_EN = {
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
            "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    /** English words for multiples of ten from 20 to 90 (indices 0 and 1 unused). */
    private static final String[] TENS_EN = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /** English words for multiples of one hundred from 100 to 900. */
    private static final String[] HUNDREDS_EN = {
            "", "One Hundred", "Two Hundred", "Three Hundred", "Four Hundred",
            "Five Hundred", "Six Hundred", "Seven Hundred", "Eight Hundred", "Nine Hundred"
    };

    /**
     * English names for each successive group of three digits (ones,
     * thousand, million, ...), indexed by group position starting from the
     * least-significant group. Defines the largest magnitude supported by
     * {@link #getEnglishWords(String)}.
     */
    private static final String[] THOUSANDS_EN = {
            "", "Thousand", "Million", "Billion", "Trillion", "Quadrillion",
            "Quintillion", "Sextillion", "Septillion", "Octillion", "Nonillion", "Decillion"
    };

    /** English words for individual digits 0-9, used to spell out a decimal part digit by digit. */
    private static final String[] DIGIT_WORDS_EN = {
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"
    };

    /** English word prefixed to the result for negative numbers. */
    private static final String NEGATIVE_WORD_EN = "Negative";

    /** Persian words for single digits 0-9, used within a three-digit group ("" for 0). */
    private static final String[] ONES_FA = {
            "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"
    };

    /** Persian words for the numbers 10-19. */
    private static final String[] TEENS_FA = {
            "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده",
            "شانزده", "هفده", "هجده", "نوزده"
    };

    /** Persian words for multiples of ten from 20 to 90 (indices 0 and 1 unused). */
    private static final String[] TENS_FA = {
            "", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"
    };

    /** Persian words for multiples of one hundred from 100 to 900. */
    private static final String[] HUNDREDS_FA = {
            "", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"
    };

    /**
     * Persian names for each successive group of three digits (ones,
     * thousand, million, ...), indexed by group position starting from the
     * least-significant group. Defines the largest magnitude supported by
     * {@link #getPersianWords(String)}.
     */
    private static final String[] THOUSANDS_FA = {
            "", "هزار", "میلیون", "میلیارد", "بیلیون", "بیلیارد", "تریلیون",
            "تریلیارد", "کوادریلیون", "کوادریلیارد", "کوینتیلیون", "کوینتیلیارد"
    };

    /** Persian words for individual digits 0-9, used to spell out a decimal part digit by digit. */
    private static final String[] DIGIT_WORDS_FA = {
            "صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"
    };

    /** Persian word prefixed to the result for negative numbers. */
    private static final String NEGATIVE_WORD_FA = "منفی";

    /**
     * The Persian/Arabic decimal separator character (U+066B). The decimal
     * separator is always rendered as this character in both the English
     * and Persian numeric formatting output, so the visual shape of
     * formatted numbers stays consistent regardless of the digit script
     * used ({@link #toPersianDigits} / {@link #toEnglishDigits}).
     */
    private static final char PERSIAN_DECIMAL_SEPARATOR = '٫';

    /** Persian digit characters 0-9, in order, used by {@link #toPersianDigits(String)} and {@link #toEnglishDigits(String)}. */
    private static final String PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

    /** Latin digit characters 0-9, in order, used by {@link #toPersianDigits(String)} and {@link #toEnglishDigits(String)}. */
    private static final String ENGLISH_DIGITS = "0123456789";

    // ==========================================================================
    // آغاز تبدیل اعداد به حروف (فارسی و انگلیسی - منطق مشترک)
    // ==========================================================================

    /**
     * Converts a numeric string into its English spelled-out word form.
     * <p>
     * Accepts an optional leading {@code -} sign, thousands separators
     * ({@code ,}), and an optional decimal part separated by either
     * {@code .} or the Persian decimal separator ({@code ٫}); Persian
     * digits are also accepted and normalized to Latin digits before
     * conversion. The decimal part, if present, is spelled out digit by
     * digit after the word {@code "Point"}.
     *
     * <p>Example:</p>
     * <pre>{@code
     * JNumber.getEnglishWords("123");     // "One Hundred Twenty Three"
     * JNumber.getEnglishWords("-45.6");   // "Negative Forty Five Point Six"
     * JNumber.getEnglishWords("0");       // "Zero"
     * }</pre>
     *
     * @param numStr the numeric string to convert; {@code null} or empty returns {@code ""}
     * @return the English word representation of {@code numStr}
     * @throws IllegalArgumentException if {@code numStr} is not a valid
     *                                   numeric string (non-digit
     *                                   characters, or more than one
     *                                   decimal point)
     * @throws ArithmeticException      if the integer part's magnitude
     *                                   exceeds the largest supported group
     *                                   name ({@link #THOUSANDS_EN})
     */
    public static String getEnglishWords(String numStr) {
        return convertToWords(numStr, ONES_EN, TEENS_EN, TENS_EN, HUNDREDS_EN,
                THOUSANDS_EN, DIGIT_WORDS_EN, NEGATIVE_WORD_EN, "Zero", " ", " Point ",
                "Number is too large to convert to words (exceeds supported magnitude).");
    }

    /**
     * Converts a numeric string into its Persian spelled-out word form.
     * <p>
     * Accepts an optional leading {@code -} sign, thousands separators
     * ({@code ,}), and an optional decimal part separated by either
     * {@code .} or the Persian decimal separator ({@code ٫}); Persian
     * digits are also accepted and normalized to Latin digits before
     * conversion. The decimal part, if present, is spelled out digit by
     * digit after the word {@code "ممیز"}.
     *
     * <p>Example:</p>
     * <pre>{@code
     * JNumber.getPersianWords("123");     // "صد و بیست و سه"
     * JNumber.getPersianWords("-45.6");   // "منفی چهل و پنج ممیز شش"
     * JNumber.getPersianWords("0");       // "صفر"
     * }</pre>
     *
     * @param numStr the numeric string to convert; {@code null} or empty returns {@code ""}
     * @return the Persian word representation of {@code numStr}
     * @throws IllegalArgumentException if {@code numStr} is not a valid
     *                                   numeric string (non-digit
     *                                   characters, or more than one
     *                                   decimal point)
     * @throws ArithmeticException      if the integer part's magnitude
     *                                   exceeds the largest supported group
     *                                   name ({@link #THOUSANDS_FA})
     */
    public static String getPersianWords(String numStr) {
        return convertToWords(numStr, ONES_FA, TEENS_FA, TENS_FA, HUNDREDS_FA,
                THOUSANDS_FA, DIGIT_WORDS_FA, NEGATIVE_WORD_FA, "صفر", " و ", " ممیز ",
                "عدد برای تبدیل به حروف بسیار بزرگ است (خارج از محدوده‌ی پشتیبانی‌شده).");
    }

    /**
     * Shared implementation behind {@link #getEnglishWords(String)} and
     * {@link #getPersianWords(String)}: normalizes the input string,
     * splits it into integer and decimal parts, spells out the integer
     * part in groups of three digits, spells out the decimal part digit by
     * digit, and re-applies the negative sign word if needed.
     *
     * @param numStr               the raw numeric string to convert
     * @param ones                 language-specific words for digits 1-9 within a group
     * @param teens                language-specific words for 10-19
     * @param tens                 language-specific words for multiples of ten
     * @param hundreds             language-specific words for multiples of one hundred
     * @param thousands            language-specific names for each group of three digits
     * @param digitWords           language-specific words for individual digits, for the decimal part
     * @param negativeWord         language-specific word prefixed for negative numbers
     * @param zeroWord             language-specific word for zero
     * @param joiner               separator inserted between word segments
     * @param decimalSeparatorWord separator phrase inserted between the integer and decimal words
     * @param tooLargeMessage      message used for the exception thrown when the number is too large
     * @return the word representation of {@code numStr} in the requested language
     * @throws IllegalArgumentException if {@code numStr} is not a valid numeric string
     * @throws ArithmeticException      if the integer part's magnitude exceeds {@code thousands.length}
     */
    private static String convertToWords(String numStr, String[] ones, String[] teens, String[] tens,
                                         String[] hundreds, String[] thousands, String[] digitWords,
                                         String negativeWord, String zeroWord, String joiner,
                                         String decimalSeparatorWord, String tooLargeMessage) {
        if (numStr == null || numStr.isEmpty())
            return "";

        String normalized = numStr.trim()
                .replace(",", "")
                .replace(String.valueOf(PERSIAN_DECIMAL_SEPARATOR), ".");
        normalized = toEnglishDigits(normalized);

        boolean negative = normalized.startsWith("-");
        if (negative)
            normalized = normalized.substring(1);

        validateNumericString(normalized);

        String integerPart;
        String decimalPart = null;
        int dotIndex = normalized.indexOf('.');

        if (dotIndex == -1) {
            integerPart = normalized;
        } else {
            integerPart = normalized.substring(0, dotIndex);
            decimalPart = normalized.substring(dotIndex + 1);
        }

        if (integerPart.isEmpty())
            integerPart = "0";

        integerPart = stripLeadingZeros(integerPart);

        String integerWords = integerToWords(integerPart, ones, teens, tens, hundreds, thousands,
                zeroWord, joiner, tooLargeMessage);

        String result;
        if (decimalPart == null || decimalPart.isEmpty()) {
            result = integerWords;
        } else {
            StringBuilder decimalWords = new StringBuilder();
            for (int i = 0; i < decimalPart.length(); i++) {
                if (decimalWords.length() > 0)
                    decimalWords.append(' ');
                decimalWords.append(digitWords[decimalPart.charAt(i) - '0']);
            }
            result = integerWords + decimalSeparatorWord + decimalWords;
        }

        if (negative && !isZeroValue(integerPart, decimalPart))
            result = negativeWord + " " + result;

        return result;
    }

    /**
     * Converts a string of up to three digits into its spelled-out word
     * form (e.g. {@code "123"} -&gt; hundreds + tens/teens + ones), for a
     * single group within a larger number.
     *
     * @param nStr     the digit string to convert, left-padded to length 3 if shorter
     * @param ones     language-specific words for digits 1-9
     * @param teens    language-specific words for 10-19
     * @param tens     language-specific words for multiples of ten
     * @param hundreds language-specific words for multiples of one hundred
     * @param joiner   separator inserted between word segments
     * @return the word representation of the three-digit group, or an
     *         empty string if the group is {@code "000"}
     */
    private static String threeDigitToWords(String nStr, String[] ones, String[] teens, String[] tens,
                                            String[] hundreds, String joiner) {
        nStr = padStartZero(nStr, 3);

        int h = nStr.charAt(0) - '0';
        int t = nStr.charAt(1) - '0';
        int o = nStr.charAt(2) - '0';

        List<String> result = new ArrayList<>();

        if (h != 0)
            result.add(hundreds[h]);

        if (t == 1) {
            result.add(teens[o]);
        } else {
            if (t > 1)
                result.add(tens[t]);
            if (o != 0)
                result.add(ones[o]);
        }

        return String.join(joiner, result);
    }

    /**
     * Converts a non-negative integer digit string (with no leading zeros,
     * except {@code "0"} itself) into its full spelled-out word form,
     * splitting it into groups of three digits and appending the
     * appropriate magnitude name ({@code thousands}, {@code millions}, ...)
     * to each non-zero group.
     *
     * @param str            the integer digit string to convert
     * @param ones           language-specific words for digits 1-9 within a group
     * @param teens          language-specific words for 10-19
     * @param tens           language-specific words for multiples of ten
     * @param hundreds       language-specific words for multiples of one hundred
     * @param thousands      language-specific names for each group of three digits
     * @param zeroWord       language-specific word for zero
     * @param joiner         separator inserted between word segments
     * @param tooLargeMessage message used for the exception thrown when the number is too large
     * @return the word representation of {@code str}
     * @throws ArithmeticException if {@code str} requires more groups than {@code thousands.length}
     */
    private static String integerToWords(String str, String[] ones, String[] teens, String[] tens,
                                         String[] hundreds, String[] thousands, String zeroWord,
                                         String joiner, String tooLargeMessage) {
        str = stripLeadingZeros(str);

        if (str.equals("0"))
            return zeroWord;

        int len = str.length();
        int groupCount = (len + 2) / 3;

        if (groupCount > thousands.length) {
            throw new ArithmeticException(tooLargeMessage);
        }

        List<String> words = new ArrayList<>();
        int end = len;
        for (int i = 0; i < groupCount; i++) {
            int start = Math.max(0, end - 3);
            String part = str.substring(start, end);
            end = start;

            int num = Integer.parseInt(part);
            if (num == 0)
                continue;

            String text = threeDigitToWords(part, ones, teens, tens, hundreds, joiner);
            if (!thousands[i].isEmpty())
                text = text + " " + thousands[i];

            words.add(text);
        }

        Collections.reverse(words);
        return String.join(joiner, words);
    }

    // پایان تبدیل اعداد به حروف
    // ==========================================================================
    // آغاز تبدیل اعداد لاتین به اعداد فارسی با کاما (جداکننده اعشار: ٫)
    // ==========================================================================

    /**
     * Formats a numeric string (Latin or Persian digits) into its Persian
     * representation: Persian digits, thousands separators ({@code ,}) in
     * the integer part, and the Persian decimal separator ({@code ٫})
     * before an unformatted decimal part.
     *
     * <p>Example:</p>
     * <pre>{@code
     * JNumber.getPersianNumber("1234567");     // "۱,۲۳۴,۵۶۷"
     * JNumber.getPersianNumber("۱۲۳۴۵۶۷");     // "۱,۲۳۴,۵۶۷"
     * JNumber.getPersianNumber("-1234.5");     // "-۱,۲۳۴٫۵" (the decimal digits are also
     *                                           //  converted to Persian digits, same as the
     *                                           //  integer part)
     * }</pre>
     *
     * @param str the numeric string to format; {@code null} or empty returns {@code ""}
     * @return the Persian-digit, comma-grouped representation of {@code str}
     */
    public static String getPersianNumber(String str) {
        if (str == null || str.isEmpty())
            return "";

        boolean negative = str.trim().startsWith("-");

        // toEnglishDigits(...) must run before the [^\d.] strip below: \d only matches
        // Latin digits, so a Persian-digit input (e.g. "۱۲۳۴۵۶۷") would otherwise have
        // every digit stripped out and silently collapse to "0".
        String cleaned = str.replace(String.valueOf(PERSIAN_DECIMAL_SEPARATOR), ".");
        cleaned = toEnglishDigits(cleaned);
        cleaned = cleaned.replaceAll("[^\\d.]", "");

        int firstDot = cleaned.indexOf('.');
        if (firstDot != -1) {
            String head = cleaned.substring(0, firstDot + 1);
            String tail = cleaned.substring(firstDot + 1).replace(".", "");
            cleaned = head + tail;
        }

        String integer;
        String decimal = null;
        int dotIndex = cleaned.indexOf('.');
        if (dotIndex == -1) {
            integer = cleaned;
        } else {
            integer = cleaned.substring(0, dotIndex);
            decimal = cleaned.substring(dotIndex + 1);
        }

        integer = stripLeadingZeros(integer);
        if (integer.isEmpty())
            integer = "0";

        String formatted = addThousandsSeparators(integer);
        String result = (decimal != null)
                ? formatted + PERSIAN_DECIMAL_SEPARATOR + decimal
                : formatted;

        if (negative && !isZeroValue(integer, decimal))
            result = "-" + result;

        return toPersianDigits(result);
    }

    // پایان تبدیل اعداد لاتین به اعداد فارسی
    // ==========================================================================
    // آغاز تبدیل اعداد فارسی به اعداد لاتین با کاما (جداکننده اعشار: ٫)
    // ==========================================================================

    /**
     * Formats a numeric string (Latin or Persian digits) into its Latin
     * representation: Latin digits, thousands separators ({@code ,}) in
     * the integer part, and the Persian decimal separator ({@code ٫})
     * before an unformatted decimal part.
     * <p>
     * Despite the "English" name, note that the decimal separator used is
     * still {@link #PERSIAN_DECIMAL_SEPARATOR}, matching the convention
     * used by {@link #getPersianNumber(String)} so both outputs share the
     * same visual separator.
     *
     * <p>
     * Any character that is not a digit or a decimal point is stripped
     * from the input before formatting (mirroring
     * {@link #getPersianNumber(String)}), so stray whitespace or other
     * junk characters do not end up embedded in the result.
     *
     * <p>Example:</p>
     * <pre>{@code
     * JNumber.getEnglishNumber("۱۲۳۴۵۶۷");   // "1,234,567"
     * JNumber.getEnglishNumber(" 1234 ");    // "1,234"
     * }</pre>
     *
     * @param str the numeric string to format; {@code null} or empty returns {@code ""}
     * @return the Latin-digit, comma-grouped representation of {@code str}
     */
    public static String getEnglishNumber(String str) {
        if (str == null || str.isEmpty())
            return "";

        boolean negative = str.trim().startsWith("-");

        String cleaned = str.replace(String.valueOf(PERSIAN_DECIMAL_SEPARATOR), ".");
        cleaned = toEnglishDigits(cleaned);
        cleaned = cleaned.replaceAll("[^\\d.]", "");

        int firstDot = cleaned.indexOf('.');
        if (firstDot != -1) {
            String head = cleaned.substring(0, firstDot + 1);
            String tail = cleaned.substring(firstDot + 1).replace(".", "");
            cleaned = head + tail;
        }

        String integer;
        String decimal = null;
        int dotIndex = cleaned.indexOf('.');
        if (dotIndex == -1) {
            integer = cleaned;
        } else {
            integer = cleaned.substring(0, dotIndex);
            decimal = cleaned.substring(dotIndex + 1);
        }

        integer = stripLeadingZeros(integer);
        if (integer.isEmpty())
            integer = "0";

        String formatted = addThousandsSeparators(integer);
        String formattedResult = (decimal != null)
                ? formatted + PERSIAN_DECIMAL_SEPARATOR + decimal
                : formatted;

        if (negative && !isZeroValue(integer, decimal))
            formattedResult = "-" + formattedResult;

        return formattedResult;
    }

    /**
     * Strips thousands separators and normalizes an already Latin-digit
     * formatted numeric string (as produced by {@link #getEnglishNumber(String)})
     * back into a plain numeric string: Latin digits, no separators, and
     * {@code .} as the decimal point (with any trailing decimal point
     * removed).
     *
     * <p>Example:</p>
     * <pre>{@code
     * JNumber.getEnglishNumberWithoutCommas("1,234,567");    // "1234567"
     * JNumber.getEnglishNumberWithoutCommas("1,234٫56");     // "1234.56"
     * }</pre>
     *
     * @param englishFormattedNumber the formatted numeric string to normalize; {@code null} or empty returns {@code ""}
     * @return the plain numeric string, with separators removed and the decimal point normalized to {@code .}
     */
    public static String getEnglishNumberWithoutCommas(String englishFormattedNumber) {
        if (englishFormattedNumber == null || englishFormattedNumber.isEmpty())
            return "";

        String result = toEnglishDigits(englishFormattedNumber);
        result = result.replace(",", "");
        result = result.replace(String.valueOf(PERSIAN_DECIMAL_SEPARATOR), ".");

        if (result.endsWith("."))
            result = result.substring(0, result.length() - 1);

        return result;
    }

    // پایان تبدیل اعداد فارسی به اعداد لاتین
    // ==========================================================================
    // توابع کمکی مشترک
    // ==========================================================================

    /**
     * Determines whether a parsed integer/decimal pair represents the
     * value zero (used to suppress a spurious negative sign or negative
     * word for inputs like {@code "-0"} or {@code "-0.00"}).
     *
     * @param integerPart the integer part, expected to already have leading zeros stripped
     * @param decimalPart the decimal part, or {@code null} if there is none
     * @return {@code true} if the integer part is {@code "0"} and the decimal part (if any) consists only of {@code '0'} characters
     */
    private static boolean isZeroValue(String integerPart, String decimalPart) {
        if (!integerPart.equals("0"))
            return false;
        if (decimalPart == null)
            return true;
        for (int i = 0; i < decimalPart.length(); i++) {
            if (decimalPart.charAt(i) != '0')
                return false;
        }
        return true;
    }

    /**
     * Removes leading zero digits from a numeric string, leaving at least
     * one digit (e.g. {@code "0042"} -&gt; {@code "42"}, {@code "0"} -&gt;
     * {@code "0"}).
     *
     * @param s the digit string to strip
     * @return {@code s} with leading zeros removed
     */
    private static String stripLeadingZeros(String s) {
        return s.replaceFirst("^0+(?=\\d)", "");
    }

    /**
     * Validates that a string (with any sign already removed) contains
     * only digits and at most one decimal point.
     *
     * @param s the string to validate
     * @throws IllegalArgumentException if {@code s} contains a non-digit,
     *                                   non-{@code '.'} character, or more
     *                                   than one {@code '.'} character
     */
    private static void validateNumericString(String s) {
        boolean seenDot = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (seenDot) {
                    throw new IllegalArgumentException("Invalid numeric string: multiple decimal points.");
                }
                seenDot = true;
            } else if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Invalid numeric string: contains non-digit characters.");
            }
        }
    }

    /**
     * Left-pads a digit string with {@code '0'} characters until it
     * reaches the given length. If {@code s} is already at least that
     * long, it is returned unchanged.
     *
     * @param s      the string to pad
     * @param length the desired minimum length
     * @return {@code s} left-padded with zeros to at least {@code length} characters
     */
    private static String padStartZero(String s, int length) {
        if (s.length() >= length)
            return s;
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < length; i++)
            sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    /**
     * Inserts {@code ,} thousands separators into an unsigned integer
     * digit string, grouping digits in threes from the right.
     *
     * @param integer the unsigned integer digit string to format
     * @return {@code integer} with thousands separators inserted
     */
    private static String addThousandsSeparators(String integer) {
        int len = integer.length();
        if (len <= 3)
            return integer;

        StringBuilder sb = new StringBuilder(len + len / 3);
        int firstGroupLen = len % 3;
        if (firstGroupLen == 0)
            firstGroupLen = 3;

        sb.append(integer, 0, firstGroupLen);

        for (int i = firstGroupLen; i < len; i += 3) {
            sb.append(',');
            sb.append(integer, i, i + 3);
        }

        return sb.toString();
    }

    /**
     * Replaces every Latin digit character in a string with its Persian
     * digit equivalent, leaving all other characters unchanged.
     *
     * @param s the string to convert
     * @return {@code s} with Latin digits replaced by Persian digits
     */
    private static String toPersianDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9')
                sb.append(PERSIAN_DIGITS.charAt(c - '0'));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Replaces every Persian digit character in a string with its Latin
     * digit equivalent, leaving all other characters (including Latin
     * digits already present) unchanged.
     *
     * @param s the string to convert
     * @return {@code s} with Persian digits replaced by Latin digits
     */
    private static String toEnglishDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            int idx = PERSIAN_DIGITS.indexOf(c);
            if (idx != -1)
                sb.append(ENGLISH_DIGITS.charAt(idx));
            else
                sb.append(c);
        }
        return sb.toString();
    }
}