package org.j2os.platform.jreport.report;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.Exporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.j2os.platform.jreport.dynamic.DynamicReport;
import org.j2os.platform.jreport.jasper.EntityTemplateReport;
import org.j2os.platform.jreport.jasper.TemplateReport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * The export formats supported by the {@code org.j2os.platform.jreport} engines
 * ({@link DynamicReport}, {@link TemplateReport}, {@link EntityTemplateReport}), together
 * with the shared logic for exporting an already-filled {@link JasperPrint} either as an
 * HTTP attachment or as a file on disk.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public enum ReportType {

    /**
     * Portable Document Format export.
     */
    PDF(JRPdfExporter::new, "application/pdf", "pdf"),

    /**
     * Microsoft Word (OOXML) export.
     */
    DOCX(JRDocxExporter::new, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),

    /**
     * Microsoft Excel (OOXML) export.
     */
    XLSX(JRXlsxExporter::new, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    /**
     * Factory for a fresh exporter instance of this format; a new exporter is created per export.
     */
    private final Supplier<? extends Exporter> exporterSupplier;

    /**
     * The MIME type to send in the HTTP response for this format.
     */
    private final String mimeType;

    /**
     * The file extension (without a leading dot) associated with this format.
     */
    private final String extension;

    /**
     * Creates a report type.
     *
     * @param exporterSupplier factory for a fresh exporter instance of this format
     * @param mimeType         the MIME type to send in the HTTP response for this format
     * @param extension        the file extension (without a leading dot) associated with this format
     */
    ReportType(Supplier<? extends Exporter> exporterSupplier, String mimeType, String extension) {
        this.exporterSupplier = exporterSupplier;
        this.mimeType = mimeType;
        this.extension = extension;
    }

    /**
     * Resolves a report type from a short code such as {@code "pdf"}, {@code "doc"}, or
     * {@code "xlsx"}. Matching is case-insensitive and ignores surrounding whitespace.
     *
     * @param code the code to resolve
     * @return the matching report type
     * @throws IllegalArgumentException if {@code code} is null or does not match a known format
     */
    public static ReportType fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Type not supported: null");
        }
        switch (code.trim().toLowerCase()) {
            case "pdf":
                return PDF;
            case "doc":
            case "docx":
                return DOCX;
            case "xls":
            case "xlsx":
                return XLSX;
            default:
                throw new IllegalArgumentException("Type not supported: " + code);
        }
    }

    /**
     * Derives a display name for a report from a classpath or file path, by stripping any
     * directory prefix and file extension.
     *
     * @param path the path to derive a display name from
     * @return the file name with its directory prefix and extension removed
     */
    public static String deriveDisplayName(String path) {
        String name = path;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            name = name.substring(0, lastDot);
        }
        return name;
    }

    /**
     * Creates a fresh exporter instance for this format.
     *
     * @return a new exporter
     */
    public Exporter newExporter() {
        return exporterSupplier.get();
    }

    /**
     * Returns the MIME type to send in the HTTP response for this format.
     *
     * @return the MIME type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Returns the file extension (without a leading dot) associated with this format.
     *
     * @return the file extension
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Exports an already-filled report to an HTTP response as a downloadable attachment,
     * with this format's MIME type and a UTF-8-encoded file name.
     *
     * @param response    the response to write the exported report to
     * @param displayName the base file name (without extension) to expose to the browser
     * @param jasperPrint the filled report to export
     * @throws IOException if writing to the response fails
     */
    public void writeAsAttachment(HttpServletResponse response, String displayName, JasperPrint jasperPrint) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        export(jasperPrint, buffer, displayName);

        String fileName = displayName + "." + extension;
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType(mimeType);
        response.setContentLength(buffer.size());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        try (ServletOutputStream out = response.getOutputStream()) {
            buffer.writeTo(out);
        }
    }

    /**
     * Exports an already-filled report to a file on disk, creating any missing parent
     * directories first.
     *
     * @param filePath    the path to write the exported report to
     * @param displayName a name for the report, used in the error message if export fails
     * @param jasperPrint the filled report to export
     * @throws IOException if writing the file fails
     */
    public void writeToFile(String filePath, String displayName, JasperPrint jasperPrint) throws IOException {
        Path path = Path.of(filePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            export(jasperPrint, out, displayName);
        }
    }

    /**
     * Exports a filled report to the given output stream using this format's exporter.
     *
     * @param jasperPrint the filled report to export
     * @param out         the stream to write the exported bytes to
     * @param displayName a name for the report, used in the error message if export fails
     * @throws RuntimeException if the underlying JasperReports export fails
     */
    private void export(JasperPrint jasperPrint, OutputStream out, String displayName) {
        Exporter exporter = newExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        try {
            exporter.exportReport();
        } catch (JRException e) {
            throw new RuntimeException("Failed to export report " + displayName + " as " + this, e);
        }
    }
}
