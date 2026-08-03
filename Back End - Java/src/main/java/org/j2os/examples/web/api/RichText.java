package org.j2os.examples.web.api;

import jakarta.servlet.http.HttpServletRequest;
import org.j2os.platform.jsecurity.protection.XssProtector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates {@link XssProtector#toRichText(String)}: sanitizes untrusted HTML on the way in
 * ({@link #setHtmlAndProtectXSS}) and serves the already-sanitized result back out
 * ({@link #getHtmlAndProtectXSS} does no sanitization itself - the protection already happened
 * at write time).
 * <p>
 * <b>State:</b> {@link #sharedMemoryDocument} is an instance field on a {@code @RestController},
 * which Spring instantiates as a single JVM-wide singleton bean by default. That makes this
 * field a single shared, unsynchronized value for the entire running application - every caller
 * of {@code /setHtml} overwrites the same document for every caller of {@code /getHtml},
 * regardless of who set it. This is intentional for this demo (a single shared document, like a
 * shared clipboard), not a per-user/per-session value - do not copy this pattern for anything
 * that needs per-request or per-user isolation.
 */
@RestController
public class RichText {

    private String sharedMemoryDocument;

    /**
     * Sanitizes the submitted HTML and stores it as the current shared document.
     *
     * @param request the incoming request; its {@code t1} parameter is the untrusted HTML to sanitize
     * @return the sanitized HTML that was stored
     */
    @PostMapping("/setHtml")
    public String setHtmlAndProtectXSS(HttpServletRequest request) {
        sharedMemoryDocument = XssProtector.toRichText(request.getParameter("t1"));
        return sharedMemoryDocument;
    }

    /**
     * Returns the current shared document (already sanitized when it was set).
     *
     * @return the current shared document, or {@code null} if nothing has been set yet
     */
    @GetMapping("/getHtml")
    public String getHtmlAndProtectXSS() {
        return sharedMemoryDocument;
    }
}