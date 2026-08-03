package org.j2os.platform.jreport.jasper;

import jakarta.servlet.http.HttpServletResponse;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.j2os.platform.jreport.report.ReportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills a hand-designed {@code .jrxml} template by running its embedded query against a
 * live JDBC connection.
 * <p>
 * Compiled templates are cached in {@link #COMPILED_TEMPLATES}, keyed by classpath path,
 * so the same template is only compiled once per JVM. This cache is intentionally kept
 * separate from {@link EntityTemplateReport}'s cache, in case the same classpath path
 * happens to mean a different template in each engine.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class TemplateReport {

    /**
     * Logger used to record how long each report took to fill.
     */
    private static final Logger log = LoggerFactory.getLogger(TemplateReport.class);

    /**
     * Compiled {@code .jrxml} templates, cached by their classpath path.
     */
    private static final Map<String, JasperReport> COMPILED_TEMPLATES = new ConcurrentHashMap<>();

    /**
     * Fills the given template by running its embedded query against the given connection,
     * and writes it to an HTTP response as a downloadable attachment.
     *
     * @param response           the response to write the exported report to
     * @param connection         an open JDBC connection the template's embedded query is run against
     * @param jrxmlClasspathPath the classpath location of the {@code .jrxml} template
     * @param reportType         the export format (PDF, DOCX, or XLSX)
     * @param parameters         the report parameters
     * @throws IOException if writing to the response fails
     */
    public static void generateToResponse(HttpServletResponse response, Connection connection, String jrxmlClasspathPath, ReportType reportType, Map<String, Object> parameters) throws IOException {
        long startedAt = System.currentTimeMillis();
        JasperPrint jasperPrint = fill(connection, jrxmlClasspathPath, parameters);
        reportType.writeAsAttachment(response, ReportType.deriveDisplayName(jrxmlClasspathPath), jasperPrint);
        logElapsed(jrxmlClasspathPath, startedAt);
    }

    /**
     * Fills the given template by running its embedded query against the given connection,
     * and writes it to a file on disk.
     *
     * @param filePath           the path to write the exported report to; parent directories are created if needed
     * @param connection         an open JDBC connection the template's embedded query is run against
     * @param jrxmlClasspathPath the classpath location of the {@code .jrxml} template
     * @param reportType         the export format (PDF, DOCX, or XLSX)
     * @param parameters         the report parameters
     * @throws IOException if writing the file fails
     */
    public static void generateToFile(String filePath, Connection connection, String jrxmlClasspathPath, ReportType reportType, Map<String, Object> parameters) throws IOException {
        long startedAt = System.currentTimeMillis();
        JasperPrint jasperPrint = fill(connection, jrxmlClasspathPath, parameters);
        reportType.writeToFile(filePath, jrxmlClasspathPath, jasperPrint);
        logElapsed(jrxmlClasspathPath, startedAt);
    }

    /**
     * Compiles (or reuses a cached compilation of) the given template, and fills it by
     * running its embedded query against the given connection.
     *
     * @param connection         an open JDBC connection the template's embedded query is run against
     * @param jrxmlClasspathPath the classpath location of the {@code .jrxml} template
     * @param parameters         the report parameters
     * @return the filled report, ready to be exported
     * @throws RuntimeException if filling the report fails
     */
    private static JasperPrint fill(Connection connection, String jrxmlClasspathPath, Map<String, Object> parameters) {
        JasperReport jasperReport = getCompiledReport(jrxmlClasspathPath);
        Map<String, Object> mutableParameters = new HashMap<>(parameters);

        try {
            return JasperFillManager.fillReport(jasperReport, mutableParameters, connection);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fill template report " + jrxmlClasspathPath, e);
        }
    }

    /**
     * Returns the compiled {@link JasperReport} for the given classpath path, compiling
     * and caching it first if this is the first request for that path.
     *
     * @param jrxmlClasspathPath the classpath location of the {@code .jrxml} template
     * @return the compiled report
     * @throws IllegalArgumentException if no {@code .jrxml} resource exists at that path
     * @throws RuntimeException         if loading or compiling the template fails
     */
    private static JasperReport getCompiledReport(String jrxmlClasspathPath) {
        JasperReport cached = COMPILED_TEMPLATES.get(jrxmlClasspathPath);
        if (cached != null) {
            return cached;
        }
        JasperReport compiled;
        try (InputStream jrxmlTemplate = TemplateReport.class.getResourceAsStream(jrxmlClasspathPath)) {
            if (jrxmlTemplate == null) {
                throw new IllegalArgumentException("No .jrxml found on the classpath at " + jrxmlClasspathPath);
            }
            JasperDesign jasperDesign = JRXmlLoader.load(jrxmlTemplate);
            compiled = JasperCompileManager.compileReport(jasperDesign);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile template " + jrxmlClasspathPath, e);
        }
        JasperReport existing = COMPILED_TEMPLATES.putIfAbsent(jrxmlClasspathPath, compiled);
        return existing != null ? existing : compiled;
    }

    /**
     * Logs how long a report took to fill, at info level.
     *
     * @param jrxmlClasspathPath the classpath location of the template that was filled
     * @param startedAt          the epoch millisecond timestamp generation started at
     */
    private static void logElapsed(String jrxmlClasspathPath, long startedAt) {
        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("report '{}' generated in {} ms", jrxmlClasspathPath, elapsedMs);
    }
}
