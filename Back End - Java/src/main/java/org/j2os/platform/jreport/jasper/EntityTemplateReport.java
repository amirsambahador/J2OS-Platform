package org.j2os.platform.jreport.jasper;

import jakarta.servlet.http.HttpServletResponse;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.j2os.platform.jreport.report.ReportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills a hand-designed {@code .jrxml} template from an in-memory list of records
 * (each represented as a {@code Map<String, Object>}), with no database connection involved.
 * <p>
 * Compiled templates are cached in {@link #COMPILED_TEMPLATES}, keyed by classpath path,
 * so the same template is only compiled once per JVM.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */

public class EntityTemplateReport {

    /**
     * Logger used to record how long each report took to fill.
     */
    private static final Logger log = LoggerFactory.getLogger(EntityTemplateReport.class);

    /**
     * Compiled {@code .jrxml} templates, cached by their classpath path.
     */
    private static final Map<String, JasperReport> COMPILED_TEMPLATES = new ConcurrentHashMap<>();

    /**
     * Fills the given template with the given entities and writes it to an HTTP response
     * as a downloadable attachment.
     *
     * @param response           the response to write the exported report to
     * @param jrxmlClasspathPath the classpath location of the compiled {@code .jrxml} template
     * @param reportType         the export format (PDF, DOCX, or XLSX)
     * @param parameters         the report parameters
     * @param entities           the records to render, one map per row
     * @throws IOException if writing to the response fails
     */
    public static void generateToResponse(HttpServletResponse response, String jrxmlClasspathPath,
                                          ReportType reportType, Map<String, Object> parameters,
                                          List<Map<String, Object>> entities) throws IOException {
        long startedAt = System.currentTimeMillis();
        JasperPrint jasperPrint = fill(jrxmlClasspathPath, parameters, entities);
        reportType.writeAsAttachment(response, ReportType.deriveDisplayName(jrxmlClasspathPath), jasperPrint);
        logElapsed(jrxmlClasspathPath, startedAt);
    }

    /**
     * Fills the given template with the given entities and writes it to a file on disk.
     *
     * @param filePath           the path to write the exported report to; parent directories are created if needed
     * @param jrxmlClasspathPath the classpath location of the compiled {@code .jrxml} template
     * @param reportType         the export format (PDF, DOCX, or XLSX)
     * @param parameters         the report parameters
     * @param entities           the records to render, one map per row
     * @throws IOException if writing the file fails
     */
    public static void generateToFile(String filePath, String jrxmlClasspathPath,
                                      ReportType reportType, Map<String, Object> parameters,
                                      List<Map<String, Object>> entities) throws IOException {
        long startedAt = System.currentTimeMillis();
        JasperPrint jasperPrint = fill(jrxmlClasspathPath, parameters, entities);
        reportType.writeToFile(filePath, jrxmlClasspathPath, jasperPrint);
        logElapsed(jrxmlClasspathPath, startedAt);
    }

    /**
     * Compiles (or reuses a cached compilation of) the given template, and fills it using
     * the given entities as the data source.
     *
     * @param jrxmlClasspathPath the classpath location of the {@code .jrxml} template
     * @param parameters         the report parameters
     * @param entities           the records to render, one map per row
     * @return the filled report, ready to be exported
     * @throws RuntimeException if filling the report fails
     */
    private static JasperPrint fill(String jrxmlClasspathPath, Map<String, Object> parameters, List<Map<String, Object>> entities) {
        JasperReport jasperReport = getCompiledReport(jrxmlClasspathPath);

        List<Map<String, ?>> rows = new ArrayList<>(entities);

        Map<String, Object> mutableParameters = new HashMap<>(parameters);

        try {
            return JasperFillManager.fillReport(jasperReport, mutableParameters, new JRMapCollectionDataSource(rows));
        } catch (Exception e) {
            throw new RuntimeException("Failed to fill bean report " + jrxmlClasspathPath, e);
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
        try (InputStream jrxmlTemplate = EntityTemplateReport.class.getResourceAsStream(jrxmlClasspathPath)) {
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
