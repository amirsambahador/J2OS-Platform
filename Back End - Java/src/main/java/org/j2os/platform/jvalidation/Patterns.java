package org.j2os.platform.jvalidation;

import java.util.regex.Pattern;

/**
 * A collection of ready-made {@link Pattern} constants for common formats,
 * for use with {@link Validator.Field#regex(Pattern)} - e.g.
 * {@code .field(User::getNationalCode).regex(Patterns.NATIONAL_CODE)}.
 * <p>
 * Grouped into Iran-specific formats and general-purpose formats.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class Patterns {

    /** Iranian mobile number: {@code 09} followed by 9 digits (11 digits total). */
    public static final Pattern MOBILE = Pattern.compile("^09\\d{9}$");

    /** Iranian national ID code: exactly 10 digits. */
    public static final Pattern NATIONAL_CODE = exactDigits(10);

    // ───── Iran ─────

    /** Iranian legal entity (company) ID: exactly 11 digits. */
    public static final Pattern LEGAL_ENTITY_ID = exactDigits(11);

    /** Iranian postal code: exactly 10 digits. */
    public static final Pattern POSTAL_CODE = exactDigits(10);

    /** Iranian landline number: leading {@code 0}, a 2-3 digit area code, then a 7-8 digit number. */
    public static final Pattern LANDLINE_IR = Pattern.compile("^0\\d{2,3}\\d{7,8}$");

    /** Iranian IBAN (Sheba) number: {@code IR} followed by 24 digits. */
    public static final Pattern SHEBA_NUMBER = Pattern.compile("^IR\\d{24}$");

    /** Bank card number: exactly 16 digits. */
    public static final Pattern CARD_NUMBER = Pattern.compile("^\\d{16}$");

    /** Iranian vehicle license plate: {@code NN<Persian letter>NNN-NN}. */
    public static final Pattern LICENSE_PLATE_IR = Pattern.compile("^\\d{2}[\\u0600-\\u06FF]\\d{3}-\\d{2}$");

    /** Basic email address shape: {@code local@domain.tld}, no embedded whitespace. */
    public static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** {@code http://} or {@code https://} URL with a domain containing at least one dot. */
    public static final Pattern URL = Pattern.compile("^https?://[^\\s$.?#][^\\s]*\\.[^\\s]+$");

    // ───── General ─────

    /** Username: 3-36 characters, letters/digits/underscore only. */
    public static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,36}$");

    /**
     * Strong password: at least 8 characters, containing at least one
     * lowercase letter, one uppercase letter, one digit, and one of
     * {@code @$!%*?&}.
     */
    public static final Pattern STRONG_PASSWORD =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$");

    /** IPv4 address in dotted-decimal notation (each octet 0-255). */
    public static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    /** IPv6 address, covering full, compressed ({@code ::}), and mixed forms. */
    public static final Pattern IPV6 = Pattern.compile(
            "^(" +
                    "([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}" +
                    "|([0-9A-Fa-f]{1,4}:){1,7}:" +
                    "|([0-9A-Fa-f]{1,4}:){1,6}:[0-9A-Fa-f]{1,4}" +
                    "|([0-9A-Fa-f]{1,4}:){1,5}(:[0-9A-Fa-f]{1,4}){1,2}" +
                    "|([0-9A-Fa-f]{1,4}:){1,4}(:[0-9A-Fa-f]{1,4}){1,3}" +
                    "|([0-9A-Fa-f]{1,4}:){1,3}(:[0-9A-Fa-f]{1,4}){1,4}" +
                    "|([0-9A-Fa-f]{1,4}:){1,2}(:[0-9A-Fa-f]{1,4}){1,5}" +
                    "|[0-9A-Fa-f]{1,4}:(:[0-9A-Fa-f]{1,4}){1,6}" +
                    "|:((:[0-9A-Fa-f]{1,4}){1,7}|:)" +
                    ")$");

    /** URL-friendly slug: lowercase letters/digits, hyphen-separated words. */
    public static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    /** Standard UUID shape: {@code 8-4-4-4-12} hexadecimal digits, hyphen-separated. */
    public static final Pattern UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Base64-encoded data, with optional {@code =}/{@code ==} padding. */
    public static final Pattern BASE64 =
            Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");

    /** JSON Web Token shape: three base64url segments separated by {@code .}. */
    public static final Pattern JWT =
            Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    /** Generic IBAN shape: 2 letters (country code), 2 digits (check digits), then 11-30 alphanumeric characters (BBAN). */
    public static final Pattern IBAN = Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$");

    /** Hex color code: {@code #RGB} or {@code #RRGGBB}. */
    public static final Pattern HEX_COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

    /** 24-hour time: {@code HH:mm} or {@code HH:mm:ss}. */
    public static final Pattern TIME_24H =
            Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?$");

    /** ISO-8601 calendar date: {@code yyyy-MM-dd}. */
    public static final Pattern DATE_ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** Domain name: one or more dot-separated labels, ending in a letters-only TLD of at least 2 characters. */
    public static final Pattern DOMAIN_NAME =
            Pattern.compile("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

    /** MAC address, colon- or hyphen-separated hex octets (e.g. {@code 00:1A:2B:3C:4D:5E}). */
    public static final Pattern MAC_ADDRESS =
            Pattern.compile("^(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$|^(?:[0-9a-fA-F]{2}-){5}[0-9a-fA-F]{2}$");

    /** IMEI (mobile device identifier): exactly 15 digits. */
    public static final Pattern IMEI = exactDigits(15);

    /** Generic payment card number shape: 13-19 digits (no Luhn check). */
    public static final Pattern CREDIT_CARD_GENERIC = Pattern.compile("^\\d{13,19}$");

    /** Persian text: Arabic-script letters, ZWNJ, and whitespace, excluding Persian/Arabic digits. */
    public static final Pattern PERSIAN_TEXT = Pattern.compile(
            "^[[\\u0600-\\u06FF&&[^\\u0660-\\u0669\\u06F0-\\u06F9]]\\u200C\\s]+$");

    /** English text: Latin letters and whitespace only. */
    public static final Pattern ENGLISH_TEXT = Pattern.compile("^[a-zA-Z\\s]+$");

    /** Text in either English or Persian script (and whitespace/ZWNJ), excluding Persian/Arabic digits. */
    public static final Pattern TEXT_ANY_LANGUAGE = Pattern.compile(
            "^[a-zA-Z[\\u0600-\\u06FF&&[^\\u0660-\\u0669\\u06F0-\\u06F9]]\\u200C\\s]+$");

    /** One or more Persian/Arabic-Indic digit characters (۰-۹). */
    public static final Pattern PERSIAN_DIGITS = Pattern.compile("^[\\u06F0-\\u06F9]+$");

    /** One or more Latin digit characters (0-9). */
    public static final Pattern ENGLISH_DIGITS = Pattern.compile("^[0-9]+$");

    private Patterns() {
    }

    /**
     * Builds a pattern that matches exactly {@code n} digits, anchored at
     * both ends.
     *
     * @param n the required number of digits
     * @return a compiled pattern matching exactly {@code n} digits
     */
    private static Pattern exactDigits(int n) {
        return Pattern.compile("^\\d{" + n + "}$");
    }
}