package org.j2os.examples.desktop.jsecurity;

import org.j2os.platform.jsecurity.protection.XssProtector;

/**
 * Demonstrates {@link XssProtector}'s three sanitization modes on the same untrusted input.
 * <p>
 * Two different attack vectors are used across the demos: a {@code <script>} tag (for
 * {@link #main}'s rich-text demo) and an {@code onerror} event-handler attribute on an
 * {@code <img>} tag (for the plain-text and display-HTML demos) — both are stripped, just via
 * different mechanisms depending on the mode.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class XssProtectorExample {

    private XssProtectorExample() {
    }

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        String untrustedRichText = "<b>Hello</b> <i>World</i> <script>alert('xss')</script>";
        String untrustedPlainInput = "<b>Hello</b><img src=x onerror=alert(1)>";

        // Keeps a curated set of formatting tags (e.g. <b>, <i>) but strips anything that could
        // execute script, such as <script> itself.
        System.out.println("=== toRichText ===");
        System.out.println("Input:  " + untrustedRichText);
        System.out.println("Output: " + XssProtector.toRichText(untrustedRichText));

        // Strips all HTML tags, leaving only the underlying text.
        System.out.println("\n=== toPlainText ===");
        System.out.println("Input:  " + untrustedPlainInput);
        System.out.println("Output: " + XssProtector.toPlainText(untrustedPlainInput));

        // Escapes the input so it can be safely embedded and rendered as literal text inside
        // HTML (e.g. "<" becomes "&lt;"), rather than being interpreted as markup at all.
        System.out.println("\n=== toDisplayHtml ===");
        System.out.println("Input:  " + untrustedPlainInput);
        System.out.println("Output: " + XssProtector.toDisplayHtml(untrustedPlainInput));

        System.out.println("\n=== null input ===");
        System.out.println("toRichText(null):    " + XssProtector.toRichText(null));
        System.out.println("toPlainText(null):   " + XssProtector.toPlainText(null));
        System.out.println("toDisplayHtml(null): " + XssProtector.toDisplayHtml(null));
    }
}