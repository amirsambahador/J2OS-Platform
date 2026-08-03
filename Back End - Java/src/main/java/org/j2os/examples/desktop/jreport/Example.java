package org.j2os.examples.desktop.jreport;

import org.j2os.platform.jreport.dynamic.DynamicReport;
import org.j2os.platform.jreport.jasper.EntityTemplateReport;
import org.j2os.platform.jreport.jasper.TemplateReport;
import org.j2os.platform.jreport.report.ReportType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates all three report engines ({@link DynamicReport}, {@link TemplateReport},
 * {@link EntityTemplateReport}), each exported both to an (intentionally null, for this
 * demo) {@code HttpServletResponse} and to a file on disk. The report title, column
 * headers, and sample data are kept in Persian to demonstrate this library's RTL/Vazirmatn
 * text support.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example {

    /**
     * Directory the file-based demos write their output into.
     */
    private static final String OUTPUT_DIR = "target/demo-output";

    /**
     * Classpath location of the template used by the {@link TemplateReport} demos.
     */
    private static final String TEMPLATE_PATH = "/report-generator/reports/template-sample.jrxml";

    /**
     * Classpath location of the template used by the {@link EntityTemplateReport} demos.
     */
    private static final String ENTITY_PATH = "/report-generator/reports/entity-sample.jrxml";

    /**
     * Sample Persian report title used across the demos, to exercise RTL text rendering.
     */
    private static final String DEMO_TITLE = "عنوان گزارش: لیست اعضای سازمان جاوا متن باز در سال 1404";

    /**
     * Runs every demo in sequence.
     *
     * @param args not used
     * @throws Exception if any demo fails unexpectedly
     */
    public static void main(String[] args) throws Exception {
        demoDynamicReportEngineToResponse();
        demoDynamicReportEngineToFile();
        demoTemplateReportEngineToResponse();
        demoTemplateReportEngineToResponse(); // Same path again - should hit the compiled-template cache, no file I/O.
        demoTemplateReportEngineToFile();
        demoEntityReportEngineToResponse();
        demoEntityReportEngineToFile();
    }

    /**
     * Demonstrates {@link DynamicReport} exporting to an HTTP response.
     */
    private static void demoDynamicReportEngineToResponse() {
        System.out.println("\n=== DynamicReportEngine -> HttpServletResponse (SQL + dynamic columns) ===");
        try (Connection connection = openDemoDatabase()) {
            DynamicReport.generateToResponse(
                    null, // HttpServletResponse - supplied by a real servlet/controller in actual use
                    connection,
                    "user-list-dynamic-response", ReportType.PDF, "گزارش پرسنل",
                    "select name, family from person",
                    List.of("name", "family"),
                    List.of("نام", "نام خانوادگی"));
        } catch (NullPointerException expected) {
            System.out.println("Report built successfully; writing to the response only stopped because it was null (expected behavior for this demo).");
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Report built and written successfully.");
    }

    /**
     * Demonstrates {@link DynamicReport} exporting to a file.
     */
    private static void demoDynamicReportEngineToFile() throws Exception {
        System.out.println("\n=== DynamicReportEngine -> File ===");
        try (Connection connection = openDemoDatabase()) {
            String filePath = OUTPUT_DIR + "/Gozaresh.pdf";
            DynamicReport.generateToFile(
                    filePath,
                    connection,
                    "user-list-dynamic-file", ReportType.PDF, DEMO_TITLE,
                    "select name, family from person",
                    List.of("name", "family"),
                    List.of("نام", "نام خانوادگی"));
            System.out.println("File created: " + filePath);
        }
    }

    /**
     * Demonstrates {@link TemplateReport} exporting to an HTTP response.
     */
    private static void demoTemplateReportEngineToResponse() {
        System.out.println("\n=== TemplateReportEngine -> HttpServletResponse (.jrxml + Connection) ===");
        try (Connection connection = openDemoDatabase()) {
            TemplateReport.generateToResponse(
                    null,
                    connection,
                    TEMPLATE_PATH, ReportType.PDF,
                    Map.of("title", DEMO_TITLE));
        } catch (NullPointerException expected) {
            System.out.println("Report built successfully; writing to the response only stopped because it was null (expected behavior for this demo).");
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Report built and written successfully.");
    }

    /**
     * Demonstrates {@link TemplateReport} exporting to a file.
     */
    private static void demoTemplateReportEngineToFile() throws Exception {
        System.out.println("\n=== TemplateReportEngine -> File ===");
        try (Connection connection = openDemoDatabase()) {
            String filePath = OUTPUT_DIR + "/template-sample.pdf";
            TemplateReport.generateToFile(
                    filePath,
                    connection,
                    TEMPLATE_PATH, ReportType.PDF,
                    Map.of("title", DEMO_TITLE));
            System.out.println("File created: " + filePath);
        }
    }

    /**
     * Demonstrates {@link EntityTemplateReport} exporting to an HTTP response.
     */
    private static void demoEntityReportEngineToResponse() {
        System.out.println("\n=== EntityReportEngine -> HttpServletResponse (.jrxml + in-memory list of records, no DB) ===");
        try {
            EntityTemplateReport.generateToResponse(
                    null,
                    ENTITY_PATH, ReportType.PDF,
                    Map.of("title", DEMO_TITLE), demoEntities());
        } catch (NullPointerException expected) {
            System.out.println("Report built successfully; writing to the response only stopped because it was null (expected behavior for this demo).");
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Report built and written successfully.");
    }

    /**
     * Demonstrates {@link EntityTemplateReport} exporting to a file.
     */
    private static void demoEntityReportEngineToFile() throws Exception {
        System.out.println("\n=== EntityReportEngine -> File ===");
        String filePath = OUTPUT_DIR + "/entity-sample.pdf";
        EntityTemplateReport.generateToFile(
                filePath,
                ENTITY_PATH, ReportType.PDF,
                Map.of("title", DEMO_TITLE), demoEntities());
        System.out.println("File created: " + filePath);
    }

    /**
     * Builds the sample in-memory records used by the {@link EntityTemplateReport} demos.
     * Names are kept in Persian to exercise this library's RTL text support.
     *
     * @return two sample person records
     */
    private static List<Map<String, Object>> demoEntities() {
        return List.of(
                Map.of("name", "امیرسام", "family", "بهادر"),
                Map.of("name", "مرجان", "family", "محبی")
        );
    }

    /**
     * Opens an in-memory H2 database and seeds it with sample Persian person data, for the
     * {@link DynamicReport} and {@link TemplateReport} demos. 34 rows total (2 distinct
     * people, with one duplicated 32 extra times) so the dynamic-report demo has enough
     * rows to be a meaningful example rather than a two-row table.
     *
     * @return an open connection to the seeded demo database; the caller is responsible for closing it
     * @throws Exception if opening the connection or seeding the data fails
     */
    private static Connection openDemoDatabase() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS person(id INT, name VARCHAR(100), family VARCHAR(100))");
            statement.execute("DELETE FROM person");
            statement.execute("INSERT INTO person VALUES (1,'امیرسام','بهادر'), (2,'مرجان','محبی')");
            for (int i = 0; i < 32; i++) {
                statement.execute("INSERT INTO person VALUES (2,'مرجان','محبی')");
            }
        }
        return connection;
    }
}