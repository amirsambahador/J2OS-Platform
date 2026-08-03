package org.j2os.examples.web.common.handler;

import jakarta.servlet.http.HttpServletResponse;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts an exception into a Persian, end-user-facing error message and a matching HTTP
 * status code, logging the full exception server-side first.
 * <p>
 * <b>Note:</b> the status codes used here (700, 701, 715, 723, 800) are outside the valid HTTP
 * status range (100-599) - {@link HttpServletResponse#setStatus} accepts any {@code int} without
 * validation, so this compiles and runs, but some HTTP clients, proxies, or browsers may not
 * handle out-of-range status codes correctly. This is existing behavior, not something this
 * cleanup pass changed - flagging it here since it's a real interoperability risk worth being
 * aware of.
 */
@UtilityClass
@Slf4j
public class ErrorHandler {

    /**
     * Maps a database table name to its Persian display name, for building a friendlier
     * "this row is still in use by X" message.
     */
    private static final Map<String, String> TABLE_TO_NAME = Map.of(
            "user_example", "کاربر",
            "car_example", "خودرو",
            "request", "سفارشات",
            "user", "کاربران",
            "response", "پیشنهادات"
    );

    /**
     * Matches a MySQL/MariaDB foreign-key constraint error's {@code `schema`.`table`} reference,
     * capturing the table name in group 2.
     */
    private static final Pattern CONSTRAINT_ERROR_TABLE_PATTERN = Pattern.compile("`([^`]+)`\\.`([^`]+)`");

    /**
     * Logs the exception and returns a Persian, end-user-facing message for it, having also set
     * a matching status code on the response.
     *
     * @param exception the exception to handle
     * @param response  the HTTP response to set the content type and status code on
     * @return the Persian error message to return to the caller
     */
    public static String getMessage(Throwable exception, HttpServletResponse response) {
        response.setContentType("text/html; charset=UTF-8");
        log.error(exception.getMessage(), exception);

        if (exception instanceof CannotCreateTransactionException) {
            response.setStatus(700);
            return "خاموش بودن و یا عدم پاسخ از سمت بانک اطلاعاتی";
        } else if (exception instanceof NullPointerException) {
            response.setStatus(701);
            return "اشکال زیر ساختی در ایجاد اجسام";
        } else if (exception instanceof DataIntegrityViolationException) {
            response.setStatus(715);
            var tableName = getConstraintErrorTableName(exception);
            if (Objects.isNull(tableName)) {
                return " محدودیت های قواعد پایگاه داده ها ";
            } else {
                return " ردیف موجود در " + tableName + "، مورد استفاده قرار گرفته است. حذف این ردیف تنها توسط مدیران پلتفرم امکان پذیر است، با پشتیبانی تماس بگیرید";
            }
        } else if (exception instanceof NumberFormatException) {
            response.setStatus(723);
            return "عدد وارد شده صحیح نمی باشد";
        } else {
            response.setStatus(800);
            return "نا مشخص";
        }
    }

    /**
     * Walks an exception's cause chain looking for a {@link SQLIntegrityConstraintViolationException},
     * and if found, extracts and returns the referenced table's name - translated to its Persian
     * display name via {@link #TABLE_TO_NAME} when one is registered, otherwise the raw table name.
     *
     * @param exception the exception to search
     * @return the (possibly translated) table name, or {@code null} if no
     *         {@link SQLIntegrityConstraintViolationException} was found in the cause chain, or
     *         its message didn't match the expected {@code `schema`.`table`} format
     */
    public static String getConstraintErrorTableName(Throwable exception) {
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof SQLIntegrityConstraintViolationException)) {
            cause = cause.getCause();
        }
        if (cause instanceof SQLIntegrityConstraintViolationException sqlIntegrityConstraintViolationException) {
            String message = sqlIntegrityConstraintViolationException.getMessage();
            Matcher matcher = CONSTRAINT_ERROR_TABLE_PATTERN.matcher(message);
            if (matcher.find()) {
                String table = matcher.group(2);
                return TABLE_TO_NAME.getOrDefault(table, table);
            }
        }
        return null;
    }
}