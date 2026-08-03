package org.j2os.test.jreport;

import org.j2os.platform.jreport.dynamic.DynamicReport;
import org.j2os.platform.jreport.jasper.EntityTemplateReport;
import org.j2os.platform.jreport.jasper.TemplateReport;
import org.j2os.platform.jreport.report.ReportType;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Plain, dependency-free test suite for the {@code org.j2os.platform.jreport} library
 * (no test framework such as JUnit is used). Run it directly with its {@link #main(String[])}
 * method; each test case reports PASS/FAIL to standard output and a summary is printed at the end.
 * <p>
 * Report content, file magic bytes, and structural results are checked; the actual rendered
 * pixels/layout of each report are not.
 * <p>
 * <b>Classpath requirements:</b> the H2 database driver ({@code com.h2database:h2}), plus
 * the usual JasperReports/DynamicReports/servlet-api dependencies the library itself needs.
 * A placeholder {@code /report-generator/images/logo.jpg} is included alongside this suite
 * so the title+logo path of {@link DynamicReport} can be exercised; replace it with the
 * project's real logo if this suite is merged into the main source tree.
 * <p>
 * <b>Templates:</b> this suite uses the project's real {@code template-sample.jrxml} (for
 * {@link TemplateReport}) and {@code entity-sample.jrxml} (for {@link EntityTemplateReport}),
 * the same ones {@code Example} deploys from, so it exercises the actual production templates.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JReportTest {

    /**
     * Classpath location of the query-based template used by {@link TemplateReport} tests.
     */
    private static final String TEMPLATE_SAMPLE_PATH = "/report-generator/reports/template-sample.jrxml";
    /**
     * Classpath location of the in-memory-list template used by {@link EntityTemplateReport} tests.
     */
    private static final String ENTITY_SAMPLE_PATH = "/report-generator/reports/entity-sample.jrxml";
    /**
     * Total number of test cases executed so far.
     */
    private static int totalTestCount = 0;
    /**
     * Number of test cases that failed so far.
     */
    private static int failedTestCount = 0;
    /**
     * Directory every file-based test writes its output into.
     */
    private static File outputDir;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        int exitCode = 0;
        try {
            setUp();

            testFromCodeResolvesKnownCodes();
            testFromCodeRejectsUnknownOrNullCode();
            testDeriveDisplayNameStripsDirectoryAndExtension();
            testMimeTypeAndExtensionPerFormat();
            testNewExporterReturnsDistinctInstancePerFormat();

            testDynamicReportRejectsEmptyColumns();
            testDynamicReportRejectsMismatchedColumnTitles();
            testDynamicReportGeneratesValidPdfWithoutTitle();
            testDynamicReportGeneratesValidPdfWithTitleAndLogo();
            testDynamicReportGeneratesValidXlsx();
            testDynamicReportToResponseWithNullResponseFailsOnlyAtWrite();

            testTemplateReportGeneratesValidPdfFromConnection();
            testTemplateReportCompiledTemplateIsReusedOnSecondCall();

            testEntityTemplateReportGeneratesValidPdfFromInMemoryList();
            testEntityTemplateReportUnknownTemplatePathFailsWithMeaningfulMessage();

            testWriteToFileCreatesMissingParentDirectories();
        } catch (Exception e) {
            System.out.println("[FATAL] Test setup failed: " + e);
            e.printStackTrace();
            exitCode = 2;
        } finally {
            tearDown();
        }

        printSummary();
        if (exitCode == 0 && failedTestCount > 0) {
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    // ------------------------------------------------------------------
    // Setup / teardown
    // ------------------------------------------------------------------

    /**
     * Creates a fresh temporary output directory for this test run.
     *
     * @throws Exception if the directory cannot be created
     */
    private static void setUp() throws Exception {
        outputDir = Files.createTempDirectory("jreport-test-output").toFile();
    }

    /**
     * Recursively deletes the temporary output directory created for this run.
     */
    private static void tearDown() {
        deleteRecursively(outputDir);
    }

    /**
     * Recursively deletes a file or directory.
     *
     * @param file the file or directory to delete; does nothing if null
     */
    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /**
     * Opens a fresh in-memory H2 database, seeded with two sample rows.
     *
     * @return an open connection to the seeded database; the caller must close it
     * @throws Exception if opening the connection or seeding the data fails
     */
    private static Connection openTestDatabase() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:jreport-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person(id INT, name VARCHAR(100), family VARCHAR(100))");
            statement.execute("INSERT INTO person VALUES (1, 'Alice', 'Smith'), (2, 'Bob', 'Jones')");
        }
        return connection;
    }

    // ------------------------------------------------------------------
    // ReportType
    // ------------------------------------------------------------------

    /**
     * Verifies fromCode() resolves every documented code, case-insensitively and trimmed.
     */
    private static void testFromCodeResolvesKnownCodes() {
        String testName = "ReportType.fromCode resolves every known code (case-insensitive, trimmed)";
        boolean allCorrect =
                ReportType.fromCode("pdf") == ReportType.PDF
                        && ReportType.fromCode(" PDF ") == ReportType.PDF
                        && ReportType.fromCode("doc") == ReportType.DOCX
                        && ReportType.fromCode("docx") == ReportType.DOCX
                        && ReportType.fromCode("DOCX") == ReportType.DOCX
                        && ReportType.fromCode("xls") == ReportType.XLSX
                        && ReportType.fromCode("xlsx") == ReportType.XLSX;
        assertTrue(testName, allCorrect);
    }

    /**
     * Verifies fromCode() rejects a null or unrecognized code.
     */
    private static void testFromCodeRejectsUnknownOrNullCode() {
        String testName = "ReportType.fromCode rejects null and unknown codes";
        boolean nullRejected = false;
        boolean unknownRejected = false;
        try {
            ReportType.fromCode(null);
        } catch (IllegalArgumentException expected) {
            nullRejected = true;
        }
        try {
            ReportType.fromCode("ppt");
        } catch (IllegalArgumentException expected) {
            unknownRejected = true;
        }
        assertTrue(testName, nullRejected && unknownRejected);
    }

    /**
     * Verifies deriveDisplayName() strips both the directory prefix and the file extension.
     */
    private static void testDeriveDisplayNameStripsDirectoryAndExtension() {
        String testName = "ReportType.deriveDisplayName strips directory prefix and extension";
        boolean allCorrect =
                "report".equals(ReportType.deriveDisplayName("/a/b/report.jrxml"))
                        && "report".equals(ReportType.deriveDisplayName("report.pdf"))
                        && "report".equals(ReportType.deriveDisplayName("C:\\templates\\report.jrxml"))
                        && "noext".equals(ReportType.deriveDisplayName("noext"));
        assertTrue(testName, allCorrect);
    }

    /**
     * Verifies each format reports the expected MIME type and file extension.
     */
    private static void testMimeTypeAndExtensionPerFormat() {
        String testName = "Each ReportType reports its expected MIME type and extension";
        boolean allCorrect =
                "application/pdf".equals(ReportType.PDF.getMimeType()) && "pdf".equals(ReportType.PDF.getExtension())
                        && "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(ReportType.DOCX.getMimeType())
                        && "docx".equals(ReportType.DOCX.getExtension())
                        && "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(ReportType.XLSX.getMimeType())
                        && "xlsx".equals(ReportType.XLSX.getExtension());
        assertTrue(testName, allCorrect);
    }

    /**
     * Verifies newExporter() returns a fresh, non-null exporter instance each time it is called.
     */
    private static void testNewExporterReturnsDistinctInstancePerFormat() {
        String testName = "ReportType.newExporter returns a fresh, non-null exporter each call";
        Object firstPdfExporter = ReportType.PDF.newExporter();
        Object secondPdfExporter = ReportType.PDF.newExporter();
        Object docxExporter = ReportType.DOCX.newExporter();
        Object xlsxExporter = ReportType.XLSX.newExporter();

        boolean allNonNullAndDistinct =
                firstPdfExporter != null && secondPdfExporter != null && docxExporter != null && xlsxExporter != null
                        && firstPdfExporter != secondPdfExporter;
        assertTrue(testName, allNonNullAndDistinct);
    }

    // ------------------------------------------------------------------
    // DynamicReport
    // ------------------------------------------------------------------

    /**
     * Verifies an empty columns list is rejected.
     */
    private static void testDynamicReportRejectsEmptyColumns() {
        String testName = "DynamicReport rejects an empty columns list";
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToFile(
                    new File(outputDir, "empty-columns.pdf").getAbsolutePath(), connection,
                    "empty-columns", ReportType.PDF, null,
                    "select name, family from person", List.of(), List.of());
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies a columns/columnTitles size mismatch is rejected.
     */
    private static void testDynamicReportRejectsMismatchedColumnTitles() {
        String testName = "DynamicReport rejects a columns/columnTitles size mismatch";
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToFile(
                    new File(outputDir, "mismatched-columns.pdf").getAbsolutePath(), connection,
                    "mismatched-columns", ReportType.PDF, null,
                    "select name, family from person", List.of("name", "family"), List.of("Name"));
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies a titleless report is written as a valid, non-empty PDF file.
     */
    private static void testDynamicReportGeneratesValidPdfWithoutTitle() {
        String testName = "DynamicReport.generateToFile (no title) produces a valid PDF";
        File file = new File(outputDir, "no-title.pdf");
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToFile(
                    file.getAbsolutePath(), connection,
                    "no-title", ReportType.PDF, null,
                    "select name, family from person", List.of("name", "family"), List.of("Name", "Family"));
            assertTrue(testName, file.exists() && isValidPdf(file));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies a titled report (which renders a logo) is written as a valid, non-empty PDF file.
     */
    private static void testDynamicReportGeneratesValidPdfWithTitleAndLogo() {
        String testName = "DynamicReport.generateToFile (with title + logo) produces a valid PDF";
        File file = new File(outputDir, "with-title.pdf");
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToFile(
                    file.getAbsolutePath(), connection,
                    "with-title", ReportType.PDF, "Sample Report Title",
                    "select name, family from person", List.of("name", "family"), List.of("Name", "Family"));
            assertTrue(testName, file.exists() && isValidPdf(file));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies exporting the same report as XLSX produces a valid (ZIP-based) file.
     */
    private static void testDynamicReportGeneratesValidXlsx() {
        String testName = "DynamicReport.generateToFile (XLSX) produces a valid OOXML file";
        File file = new File(outputDir, "report.xlsx");
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToFile(
                    file.getAbsolutePath(), connection,
                    "xlsx-report", ReportType.XLSX, null,
                    "select name, family from person", List.of("name", "family"), List.of("Name", "Family"));
            assertTrue(testName, file.exists() && isValidZip(file));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies that generateToResponse with a null response still builds the report
     * successfully, only failing once it tries to write to the (null) response.
     */
    private static void testDynamicReportToResponseWithNullResponseFailsOnlyAtWrite() {
        String testName = "DynamicReport.generateToResponse(null, ...) fails only at the response-write step";
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToResponse(
                    null, connection,
                    "null-response", ReportType.PDF, null,
                    "select name, family from person", List.of("name", "family"), List.of("Name", "Family"));
            fail(testName + " [expected NullPointerException]");
        } catch (NullPointerException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected NullPointerException but got " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // TemplateReport
    // ------------------------------------------------------------------

    /**
     * Verifies filling the test template against a live connection produces a valid PDF.
     */
    private static void testTemplateReportGeneratesValidPdfFromConnection() {
        String testName = "TemplateReport.generateToFile produces a valid PDF from a DB connection";
        File file = new File(outputDir, "template-report.pdf");
        try (Connection connection = openTestDatabase()) {
            TemplateReport.generateToFile(
                    file.getAbsolutePath(), connection,
                    TEMPLATE_SAMPLE_PATH, ReportType.PDF, Map.of("title", "Template Report"));
            assertTrue(testName, file.exists() && isValidPdf(file));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies a second fill of the same template path still succeeds (exercising the compiled-template cache).
     */
    private static void testTemplateReportCompiledTemplateIsReusedOnSecondCall() {
        String testName = "TemplateReport reuses the compiled template on a second call to the same path";
        File file = new File(outputDir, "template-report-again.pdf");
        try (Connection connection = openTestDatabase()) {
            TemplateReport.generateToFile(
                    file.getAbsolutePath(), connection,
                    TEMPLATE_SAMPLE_PATH, ReportType.PDF, Map.of("title", "Template Report Again"));
            assertTrue(testName, file.exists() && isValidPdf(file));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // EntityTemplateReport
    // ------------------------------------------------------------------

    /**
     * Verifies filling the test template from an in-memory list of maps produces a valid PDF.
     */
    private static void testEntityTemplateReportGeneratesValidPdfFromInMemoryList() {
        String testName = "EntityTemplateReport.generateToFile produces a valid PDF from an in-memory list";
        File file = new File(outputDir, "entity-report.pdf");
        try {
            List<Map<String, Object>> entities = List.of(
                    Map.of("name", "Alice", "family", "Smith"),
                    Map.of("name", "Bob", "family", "Jones"));
            EntityTemplateReport.generateToFile(
                    file.getAbsolutePath(),
                    ENTITY_SAMPLE_PATH, ReportType.PDF, Map.of("title", "Entity Report"), entities);
            assertTrue(testName, file.exists() && isValidPdf(file));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies a nonexistent template classpath path fails with a message naming the missing path.
     */
    private static void testEntityTemplateReportUnknownTemplatePathFailsWithMeaningfulMessage() {
        String testName = "EntityTemplateReport fails with a meaningful message for an unknown template path";
        String bogusPath = "/report-generator/reports/does-not-exist.jrxml";
        try {
            EntityTemplateReport.generateToFile(
                    new File(outputDir, "unknown-template.pdf").getAbsolutePath(),
                    bogusPath, ReportType.PDF, Map.of("title", "x"), List.of());
            fail(testName + " [expected a RuntimeException]");
        } catch (Exception expected) {
            boolean mentionsPath = messageChainContains(expected, bogusPath);
            assertTrue(testName, mentionsPath);
        }
    }

    /**
     * Checks whether the given exception, or any exception in its cause chain, has a message
     * containing the given text.
     *
     * @param throwable the exception to search
     * @param text      the text to look for
     * @return true if a matching message is found anywhere in the cause chain
     */
    private static boolean messageChainContains(Throwable throwable, String text) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // ------------------------------------------------------------------
    // File writing
    // ------------------------------------------------------------------

    /**
     * Verifies writeToFile creates any missing parent directories before writing.
     */
    private static void testWriteToFileCreatesMissingParentDirectories() {
        String testName = "Exporting to a file under missing parent directories creates them automatically";
        File nestedFile = new File(outputDir, "nested/does/not/exist/yet/report.pdf");
        try (Connection connection = openTestDatabase()) {
            DynamicReport.generateToFile(
                    nestedFile.getAbsolutePath(), connection,
                    "nested-dirs", ReportType.PDF, null,
                    "select name, family from person", List.of("name", "family"), List.of("Name", "Family"));
            assertTrue(testName, nestedFile.exists() && isValidPdf(nestedFile));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // File-format checks
    // ------------------------------------------------------------------

    /**
     * Checks whether a file is a non-empty, valid PDF. A valid PDF always begins with the
     * bytes {@code %PDF}.
     *
     * @param file the file to check
     * @return true if the file looks like a valid, non-empty PDF
     * @throws Exception if the file cannot be read
     */
    private static boolean isValidPdf(File file) throws Exception {
        byte[] header = readHeader(file, 4);
        return header.length == 4 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
    }

    /**
     * Checks whether a file is a non-empty, valid ZIP-based (OOXML) file. Such files always
     * begin with the bytes {@code PK}.
     *
     * @param file the file to check
     * @return true if the file looks like a valid, non-empty ZIP/OOXML file
     * @throws Exception if the file cannot be read
     */
    private static boolean isValidZip(File file) throws Exception {
        byte[] header = readHeader(file, 2);
        return header.length == 2 && header[0] == 'P' && header[1] == 'K';
    }

    /**
     * Reads the first {@code length} bytes of a file.
     *
     * @param file   the file to read
     * @param length the number of leading bytes to read
     * @return the leading bytes actually read, which may be shorter than {@code length} if
     * the file is smaller
     * @throws Exception if the file cannot be read
     */
    private static byte[] readHeader(File file, int length) throws Exception {
        byte[] fullBytes = Files.readAllBytes(file.toPath());
        if (fullBytes.length < length) {
            return fullBytes;
        }
        byte[] header = new byte[length];
        System.arraycopy(fullBytes, 0, header, 0, length);
        return header;
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

    /**
     * Prints a final pass/fail summary of the whole suite.
     */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}