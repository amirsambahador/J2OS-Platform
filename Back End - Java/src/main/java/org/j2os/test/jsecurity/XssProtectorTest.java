package org.j2os.test.jsecurity;

import org.j2os.platform.jsecurity.protection.XssProtector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain, dependency-free test suite for {@code org.j2os.platform.jsecurity.protection}
 * ({@link XssProtector}) (no test framework such as JUnit is used). Run it directly with its
 * {@link #main(String[])} method; each test case reports PASS/FAIL to standard output and a
 * summary is printed at the end.
 * <p>
 * <b>Classpath requirements:</b> the OWASP Java HTML Sanitizer plus Apache Commons Text.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class XssProtectorTest {

    /** Total number of test cases executed so far. */
    private static int totalTestCount = 0;

    /** Number of test cases that failed so far. */
    private static int failedTestCount = 0;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        try {
            testXssProtectorSafeRichTextStripsScriptKeepsFormatting();
            testXssProtectorPlainTextStripsAllTags();
            testXssProtectorDisplayTextEscapesMarkup();
            testXssProtectorMethodsReturnNullForNullInput();
            testConcurrentXssSanitizationStressCheck();
        } catch (Exception e) {
            System.out.println("[FATAL] Test run failed: " + e);
            e.printStackTrace();
        }

        printSummary();
        System.exit(failedTestCount == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // XssProtector
    // ------------------------------------------------------------------

    /** Verifies toSafeRichText strips a script tag while keeping basic formatting tags. */
    private static void testXssProtectorSafeRichTextStripsScriptKeepsFormatting() {
        String testName = "XssProtector.toRichText strips <script> but keeps formatting tags";
        String result = XssProtector.toRichText("<b>Hello</b><script>alert(1)</script>");
        assertTrue(testName, result.contains("<b>Hello</b>") && !result.toLowerCase().contains("script"));
    }

    /** Verifies toPlainText strips every HTML tag, leaving only text content. */
    private static void testXssProtectorPlainTextStripsAllTags() {
        String testName = "XssProtector.toPlainText strips all HTML tags";
        String result = XssProtector.toPlainText("<b>Hello</b> <i>World</i>");
        assertTrue(testName, "Hello World".equals(result.trim()) || result.replaceAll("\\s+", " ").trim().equals("Hello World"));
    }

    /** Verifies toDisplayText HTML-escapes markup rather than removing it. */
    private static void testXssProtectorDisplayTextEscapesMarkup() {
        String testName = "XssProtector.toDisplayHtml escapes markup rather than removing it";
        String result = XssProtector.toDisplayHtml("<b>Hello</b>");
        assertTrue(testName, "&lt;b&gt;Hello&lt;/b&gt;".equals(result));
    }

    /** Verifies all three XssProtector methods return null for null input. */
    private static void testXssProtectorMethodsReturnNullForNullInput() {
        String testName = "XssProtector methods return null for null input";
        assertTrue(testName,
                XssProtector.toRichText(null) == null
                        && XssProtector.toPlainText(null) == null
                        && XssProtector.toDisplayHtml(null) == null);
    }

    /**
     * Heavy concurrency stress test for XssProtector: many threads simultaneously sanitize
     * distinct, thread- and iteration-specific HTML payloads through all three modes
     * (toRichText/toPlainText/toDisplayHtml) at once, for thousands of calls. Verifies every
     * thread's output always contains its own marker text and never fails to strip/escape its
     * own embedded {@code <script>} tag - the kind of cross-contamination or dropped-sanitization
     * that would only surface under real concurrent load if the underlying sanitizer held any
     * shared, mutable per-call state.
     */
    private static void testConcurrentXssSanitizationStressCheck() {
        String testName = "Concurrent XssProtector sanitization never cross-contaminates or drops sanitization under load";
        int threadCount = 20;
        int iterationsPerThread = 200;

        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicInteger contaminationCount = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String marker = "marker-" + threadIndex + "-" + i;
                        String input = "<b>" + marker + "</b><script>alert('" + marker + "')</script>";

                        String rich = XssProtector.toRichText(input);
                        String plain = XssProtector.toPlainText(input);
                        String display = XssProtector.toDisplayHtml(input);

                        boolean allCorrect =
                                rich.contains(marker) && !rich.toLowerCase().contains("script")
                                        && plain.contains(marker) && !plain.contains("<")
                                        && display.contains(marker) && display.contains("&lt;script&gt;");

                        if (!allCorrect) {
                            contaminationCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        threads.forEach(Thread::start);
        startLatch.countDown();

        boolean finishedInTime = true;
        for (Thread thread : threads) {
            try {
                thread.join(60_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                finishedInTime = false;
            }
        }

        assertTrue(testName, finishedInTime && unexpectedErrorCount.get() == 0 && contaminationCount.get() == 0);
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (no external test framework)
    // ------------------------------------------------------------------

    /**
     * Records a passing test case if {@code condition} is true, otherwise records a failure.
     *
     * @param testName  the name of the test case, printed in the report
     * @param condition the condition that must be true for the test to pass
     */
    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        totalTestCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void fail(String testName) {
        totalTestCount++;
        failedTestCount++;
        System.out.println("[FAIL] " + testName);
    }

    /** Prints a final pass/fail summary of the whole suite. */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}