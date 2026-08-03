package org.j2os.examples.desktop.jutil;

import org.j2os.platform.jutil.date.JDate;

import java.sql.Timestamp;

/**
 * Simple, self-contained tutorial that demonstrates the most common ways to
 * use {@link JDate}.
 * <p>
 * This class is meant purely for learning purposes: each method below
 * focuses on a single feature and prints its result to the console so you
 * can read the output and see exactly what each API call does. It does not
 * perform any assertions and is not a test.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JDateExample {

    public static void main(String[] args) {
        showTodaysDate();
        createDateFromGregorianComponents();
        createDateFromPersianComponents();
        convertGregorianTimestampToPersianString();
        convertPersianStringToGregorianTimestamp();
        moveDateForwardAndBackward();
        checkLeapYear();
        readWeekDayNames();
    }

    /**
     * Shows how to get today's date and read it back in both calendars.
     */
    private static void showTodaysDate() {
        System.out.println("--- Today's date ---");

        JDate today = new JDate();
        System.out.println("Gregorian: " + today.getGregorianDate());
        System.out.println("Persian:   " + today.getPersianDate());
        System.out.println("Summary:   " + today); // uses toString()
        System.out.println();
    }

    /**
     * Shows how to build a {@link JDate} from known Gregorian year/month/day
     * values and read the equivalent Persian date.
     */
    private static void createDateFromGregorianComponents() {
        System.out.println("--- Build from Gregorian components ---");

        JDate date = new JDate(2024, 3, 20); // 20 March 2024
        System.out.println("Gregorian input:      2024/03/20");
        System.out.println("Equivalent Persian:   " + date.getPersianDate()); // 1403/01/01
        System.out.println();
    }

    /**
     * Shows how to set an existing {@link JDate} instance to a Persian date
     * and read the equivalent Gregorian date.
     */
    private static void createDateFromPersianComponents() {
        System.out.println("--- Build from Persian components ---");

        JDate date = new JDate();
        date.setPersianDate(1403, 1, 1); // 1 Farvardin 1403 (Persian New Year)
        System.out.println("Persian input:        1403/01/01");
        System.out.println("Equivalent Gregorian: " + date.getGregorianDate()); // 2024/03/20
        System.out.println();
    }

    /**
     * Shows how to convert a Gregorian {@link Timestamp} into a Persian
     * date string, formatted as {@code yyyy/MM/dd}.
     */
    private static void convertGregorianTimestampToPersianString() {
        System.out.println("--- Timestamp -> Persian string ---");

        Timestamp timestamp = Timestamp.valueOf("2024-03-20 00:00:00");
        String persianDate = new JDate().getPersianDateString(timestamp);
        System.out.println("Gregorian timestamp: " + timestamp);
        System.out.println("Persian date string:  " + persianDate); // 1403/01/01
        System.out.println();
    }

    /**
     * Shows how to convert a Persian date string ({@code yyyy/MM/dd}) into
     * the equivalent Gregorian {@link Timestamp}.
     */
    private static void convertPersianStringToGregorianTimestamp() {
        System.out.println("--- Persian string -> Timestamp ---");

        String persianDate = "1403/01/01";
        Timestamp timestamp = new JDate().getGregorianDateTimestamp(persianDate);
        System.out.println("Persian date string:  " + persianDate);
        System.out.println("Gregorian timestamp:  " + timestamp); // 2024-03-20 00:00:00.0
        System.out.println();
    }

    /**
     * Shows how to move a date forward and backward using
     * {@link JDate#addDays(int)}, {@link JDate#nextDay()} and
     * {@link JDate#previousDay()}.
     */
    private static void moveDateForwardAndBackward() {
        System.out.println("--- Moving a date forward/backward ---");

        JDate date = new JDate(2024, 3, 20);
        System.out.println("Start:            " + date.getGregorianDate());

        date.nextDay(); // +1 day
        System.out.println("After nextDay():  " + date.getGregorianDate());

        date.addDays(10); // +10 days
        System.out.println("After +10 days:   " + date.getGregorianDate());

        date.previousDay(5); // -5 days
        System.out.println("After -5 days:    " + date.getGregorianDate());
        System.out.println();
    }

    /**
     * Shows how to check whether a given Persian year is a leap year.
     */
    private static void checkLeapYear() {
        System.out.println("--- Leap year check ---");

        JDate date = new JDate();
        System.out.println("Is 1403 a leap year? " + date.isLeap(1403)); // true
        System.out.println("Is 1402 a leap year? " + date.isLeap(1402)); // false
        System.out.println();
    }

    /**
     * Shows how to read the week day name in both English and Persian.
     */
    private static void readWeekDayNames() {
        System.out.println("--- Week day names ---");

        JDate date = new JDate(2024, 3, 20); // a Wednesday
        System.out.println("English week day: " + date.getWeekDayName());        // Wednesday
        System.out.println("Persian week day:  " + date.getPersianWeekDayName()); // چهار شنبه
    }
}