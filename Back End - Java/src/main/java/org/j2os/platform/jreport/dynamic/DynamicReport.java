package org.j2os.platform.jreport.dynamic;

import jakarta.servlet.http.HttpServletResponse;
import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.style.SimpleStyleBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.*;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JasperPrint;
import org.j2os.platform.jreport.report.ReportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;

import static net.sf.dynamicreports.report.builder.DynamicReports.*;

/**
 * Builds and exports simple tabular reports directly from a SQL query and a list of
 * columns, without requiring a hand-designed {@code .jrxml} template.
 * <p>
 * Every report uses the same fixed visual style (Vazirmatn font, right-to-left text
 * alignment, striped rows, and an optional logo/title header), and can be exported as
 * PDF, DOCX, or XLSX via {@link ReportType}.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class DynamicReport {

    /**
     * Logger used to record how long each report took to build and export.
     */
    private static final Logger log = LoggerFactory.getLogger(DynamicReport.class);

    /**
     * Font used throughout the report; must support the Persian/Arabic character set.
     */
    private static final String FONT_NAME = "Vazirmatn";

    /**
     * Page margin, in points, applied on all four sides.
     */
    private static final int PAGE_MARGIN = 20;

    /**
     * Background color of the column title (header) row.
     */
    private static final Color HEADER_BACKGROUND = new Color(0x2C, 0x3E, 0x50);

    /**
     * Text color of the column title (header) row.
     */
    private static final Color HEADER_TEXT = Color.WHITE;

    /**
     * Color of the border drawn around each data cell.
     */
    private static final Color BORDER_COLOR = new Color(0xDC, 0xE1, 0xE4);

    /**
     * Background color used to highlight every other (even) data row.
     */
    private static final Color EVEN_ROW_BACKGROUND = new Color(0xF4, 0xF6, 0xF7);

    /**
     * Builds a report from the given SQL query and columns, and writes it to an HTTP
     * response as a downloadable attachment.
     *
     * @param response     the response to write the exported report to
     * @param connection   an open JDBC connection the report's query is run against
     * @param reportName   a name for the report, used for logging and as the base file name
     * @param reportType   the export format (PDF, DOCX, or XLSX)
     * @param title        an optional report title shown with a logo in the header, or {@code null} for none
     * @param sql          the SQL query supplying the report's rows
     * @param columns      the result-set column names to render, in display order
     * @param columnTitles the header label for each column, in the same order as {@code columns}
     * @throws IOException if writing to the response fails
     */
    public static void generateToResponse(HttpServletResponse response, Connection connection,
                                          String reportName, ReportType reportType, String title,
                                          String sql, List<String> columns, List<String> columnTitles) throws IOException {
        long startedAt = System.currentTimeMillis();
        JasperPrint jasperPrint = build(connection, reportName, title, sql, columns, columnTitles);
        reportType.writeAsAttachment(response, reportName, jasperPrint);
        logElapsed(reportName, startedAt);
    }

    /**
     * Builds a report from the given SQL query and columns, and writes it to a file on disk.
     *
     * @param filePath     the path to write the exported report to; parent directories are created if needed
     * @param connection   an open JDBC connection the report's query is run against
     * @param reportName   a name for the report, used for logging
     * @param reportType   the export format (PDF, DOCX, or XLSX)
     * @param title        an optional report title shown with a logo in the header, or {@code null} for none
     * @param sql          the SQL query supplying the report's rows
     * @param columns      the result-set column names to render, in display order
     * @param columnTitles the header label for each column, in the same order as {@code columns}
     * @throws IOException if writing the file fails
     */
    public static void generateToFile(String filePath, Connection connection,
                                      String reportName, ReportType reportType, String title,
                                      String sql, List<String> columns, List<String> columnTitles) throws IOException {
        long startedAt = System.currentTimeMillis();
        JasperPrint jasperPrint = build(connection, reportName, title, sql, columns, columnTitles);
        reportType.writeToFile(filePath, reportName, jasperPrint);
        logElapsed(reportName, startedAt);
    }

    /**
     * Builds a fully rendered {@link JasperPrint} from the given SQL query and columns,
     * applying this class's fixed visual style.
     *
     * @param connection   an open JDBC connection the report's query is run against
     * @param reportName   a name for the report, used in the error message if building fails
     * @param title        an optional report title shown with a logo in the header, or {@code null} for none
     * @param sql          the SQL query supplying the report's rows
     * @param columns      the result-set column names to render, in display order
     * @param columnTitles the header label for each column, in the same order as {@code columns}
     * @return the rendered report, ready to be exported
     * @throws IllegalArgumentException if {@code columns} is empty, or if {@code columns} and
     *                                  {@code columnTitles} have different sizes
     * @throws RuntimeException         if building the report fails for any other reason
     */
    private static JasperPrint build(Connection connection, String reportName, String title,
                                     String sql, List<String> columns, List<String> columnTitles) {
        if (columns.isEmpty() || columns.size() != columnTitles.size()) {
            throw new IllegalArgumentException("columns and columnTitles must be non-empty and the same size");
        }

        StyleBuilder titleStyle = stl.style()
                .setFont(stl.font().setFontName(FONT_NAME).setFontSize(14).bold())
                .setTextAlignment(HorizontalTextAlignment.RIGHT, VerticalTextAlignment.MIDDLE)
                .setPadding(6);

        StyleBuilder columnTitleStyle = stl.style()
                .setFont(stl.font().setFontName(FONT_NAME).setFontSize(11).bold())
                .setTextAlignment(HorizontalTextAlignment.RIGHT, VerticalTextAlignment.MIDDLE)
                .setForegroundColor(HEADER_TEXT)
                .setBackgroundColor(HEADER_BACKGROUND)
                .setLeftPadding(8)
                .setRightPadding(8)
                .setTopPadding(5)
                .setBottomPadding(5);

        StyleBuilder rowStyle = stl.style()
                .setFont(stl.font().setFontName(FONT_NAME).setFontSize(11))
                .setTextAlignment(HorizontalTextAlignment.RIGHT, VerticalTextAlignment.MIDDLE)
                .setLeftPadding(8)
                .setRightPadding(8)
                .setTopPadding(4)
                .setBottomPadding(4)
                .setBorder(stl.pen(0.5f, LineStyle.SOLID).setLineColor(BORDER_COLOR));

        StyleBuilder footerStyle = stl.style()
                .setTopPadding(4)
                .setBottomPadding(4)
                .setFont(stl.font().setFontName(FONT_NAME).setFontSize(9))
                .setTextAlignment(HorizontalTextAlignment.CENTER, VerticalTextAlignment.MIDDLE);

        SimpleStyleBuilder evenRowStyle = stl.simpleStyle().setBackgroundColor(EVEN_ROW_BACKGROUND);

        @SuppressWarnings("rawtypes")
        TextColumnBuilder[] reportColumns = new TextColumnBuilder[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            reportColumns[i] = col.column(columnTitles.get(i), columns.get(i), type.stringType())
                    .setStyle(rowStyle);
        }

        JasperReportBuilder reportBuilder = report()
                .setPageFormat(PageType.A4, PageOrientation.PORTRAIT)
                .setPageMargin(margin(PAGE_MARGIN))
                .setColumnTitleStyle(columnTitleStyle)
                .setDetailEvenRowStyle(evenRowStyle)
                .highlightDetailEvenRows()
                .columns(reportColumns)
                .pageFooter(
                        cmp.text(new AbstractSimpleExpression<String>() {
                            @Override
                            public String evaluate(ReportParameters reportParameters) {
                                return "صفحه " + reportParameters.getPageNumber();
                            }
                        }).setStyle(footerStyle)
                )
                .setDataSource(sql, connection);

        if (title != null) {
            reportBuilder.title(
                    cmp.horizontalList()
                            .add(
                                    cmp.image(DynamicReport.class.getResourceAsStream("/report-generator/images/logo.png"))
                                            .setFixedDimension(94, 80),
                                    cmp.horizontalGap(10),
                                    cmp.text(title)
                                            .setStyle(titleStyle)
                            )
            );
        }

        try {
            return reportBuilder.toJasperPrint();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build report " + reportName, e);
        }
    }

    /**
     * Logs how long a report took to build and export, at info level.
     *
     * @param reportName the name of the report that was generated
     * @param startedAt  the epoch millisecond timestamp generation started at
     */
    private static void logElapsed(String reportName, long startedAt) {
        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("report '{}' generated in {} ms", reportName, elapsedMs);
    }
}
