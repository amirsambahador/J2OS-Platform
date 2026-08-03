package org.j2os.platform.jsecurity.protection;

import lombok.experimental.UtilityClass;
import org.apache.commons.text.StringEscapeUtils;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Sanitizes or escapes untrusted HTML/text input to prevent cross-site scripting (XSS), using
 * the OWASP Java HTML Sanitizer.
 * <p>
 * Three modes are provided: {@link #toRichText}, which keeps a curated set of formatting
 * tags while stripping anything dangerous; {@link #toPlainText}, which strips all markup down
 * to plain text; and {@link #toDisplayHtml}, which HTML-escapes the input so markup is shown
 * literally rather than rendered.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@UtilityClass
public class XssProtector {

    /**
     * Safe HTML policy for rich text.
     * <p>
     * Input:
     * {@code <b>Hello</b><script>alert(1)</script>}
     * <p>
     * Output:
     * {@code <b>Hello</b>}
     */
    private final PolicyFactory RICH_TEXT_POLICY =
            Sanitizers.FORMATTING
                    .and(Sanitizers.BLOCKS)
                    .and(Sanitizers.STYLES)
                    .and(Sanitizers.LINKS)
                    .and(Sanitizers.TABLES)
                    .and(Sanitizers.IMAGES);

    /**
     * Policy that keeps text only.
     * <p>
     * Input:
     * {@code <b>Hello</b>}
     * <p>
     * Output:
     * {@code Hello}
     */
    private final PolicyFactory PLAIN_TEXT_POLICY =
            new HtmlPolicyBuilder().toFactory();

    /**
     * Returns safe HTML suitable for rendering as rich text.
     * <p>
     * Input:
     * {@code <b>Hello</b><script>alert(1)</script>}
     * <p>
     * Output:
     * {@code <b>Hello</b>}
     *
     * @param value the untrusted HTML to sanitize
     * @return the sanitized HTML, or {@code null} if {@code value} was {@code null}
     */
    public String toRichText(String value) {
        return value == null ? null : RICH_TEXT_POLICY.sanitize(value);
    }

    /**
     * Removes all HTML tags, leaving only the text content.
     * <p>
     * Input:
     * {@code <b>Hello</b>}
     * <p>
     * Output:
     * {@code Hello}
     *
     * @param value the untrusted HTML to strip
     * @return the plain text, or {@code null} if {@code value} was {@code null}
     */
    public String toPlainText(String value) {
        return value == null ? null : PLAIN_TEXT_POLICY.sanitize(value);
    }

    /**
     * Converts HTML into text safe to display literally (escaped rather than rendered).
     * <p>
     * Input:
     * {@code <b>Hello</b>}
     * <p>
     * Output:
     * {@code &lt;b&gt;Hello&lt;/b&gt;}
     * <p>
     * (Displayed in a browser as the literal text {@code <b>Hello</b>}.)
     *
     * @param value the untrusted HTML to escape
     * @return the HTML-escaped text, or {@code null} if {@code value} was {@code null}
     */
    public String toDisplayHtml(String value) {
        return value == null ? null : StringEscapeUtils.escapeHtml4(value);
    }
}