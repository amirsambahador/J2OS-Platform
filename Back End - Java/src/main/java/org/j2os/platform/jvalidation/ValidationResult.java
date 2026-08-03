package org.j2os.platform.jvalidation;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of running a {@link Validator}: whether the target object was
 * valid, and the full list of errors recorded against it (empty if valid).
 * Produced by {@link Validator#validate()} and {@link Validator#validateOrThrow()}.
 *
 * @param errors every error recorded during validation, in the order the
 *               corresponding rules were evaluated; empty if the target was valid
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public record ValidationResult(List<Error> errors) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Canonical constructor; wraps {@code errors} in an unmodifiable list
     * so the result cannot be mutated after it is returned.
     *
     * @param errors every error recorded during validation
     */
    public ValidationResult(List<Error> errors) {
        this.errors = Collections.unmodifiableList(errors);
    }

    /**
     * Returns whether validation succeeded, i.e. no errors were recorded.
     *
     * @return {@code true} if {@link #errors()} is empty
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Returns the number of errors recorded.
     *
     * @return the size of {@link #errors()}
     */
    public int errorCount() {
        return errors.size();
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + isValid() + ", errors=" + errors + '}';
    }

    /**
     * A single validation failure: which field failed, a machine-readable
     * code identifying the rule, a human-readable message, an optional
     * localization key, and the offending value.
     */
    public static final class Error implements Serializable {
        private static final long serialVersionUID = 1L;

        /** The name of the field this error applies to, as derived by {@link Validator}. */
        private final String field;

        /** A machine-readable code identifying which rule failed (e.g. {@code "REQUIRED"}, {@code "MIN_LENGTH"}). */
        private final String code;

        /** The value that failed validation. */
        private final Object invalidValue;

        /** The human-readable message for this error; replaceable via {@link Validator.Field#message(String)}. */
        private String message;

        /** An optional localization key for this error, set via {@link Validator.Field#messageKey(String)}. */
        private String messageKey;

        /**
         * Creates a new validation error.
         *
         * @param field        the name of the field that failed
         * @param code         the machine-readable code identifying the rule
         * @param message      the human-readable message
         * @param invalidValue the value that failed validation
         */
        public Error(String field, String code, String message, Object invalidValue) {
            this.field = field;
            this.code = code;
            this.message = message;
            this.invalidValue = invalidValue;
        }

        /**
         * Returns the name of the field this error applies to.
         *
         * @return the field name
         */
        public String getField() {
            return field;
        }

        /**
         * Returns the machine-readable code identifying which rule failed.
         *
         * @return the error code
         */
        public String getCode() {
            return code;
        }

        /**
         * Returns the human-readable message for this error.
         *
         * @return the error message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Replaces this error's message. Package-private: only
         * {@link Validator.Field#message(String)} is meant to call this.
         *
         * @param message the new message text
         */
        void setMessage(String message) {
            this.message = message;
        }

        /**
         * Returns this error's localization key, if one was set.
         *
         * @return the message key, or {@code null} if none was set
         */
        public String getMessageKey() {
            return messageKey;
        }

        /**
         * Sets this error's localization key. Package-private: only
         * {@link Validator.Field#messageKey(String)} is meant to call this.
         *
         * @param messageKey the localization key to set
         */
        void setMessageKey(String messageKey) {
            this.messageKey = messageKey;
        }

        /**
         * Returns the value that failed validation.
         *
         * @return the invalid value
         */
        public Object getInvalidValue() {
            return invalidValue;
        }

        @Override
        public String toString() {
            return "Error{field='" + field + "', code='" + code + "', message='" + message + "'" +
                    (messageKey != null ? ", messageKey='" + messageKey + "'" : "") +
                    ", invalidValue=" + invalidValue + '}';
        }
    }
}
