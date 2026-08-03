package org.j2os.platform.jvalidation;

/**
 * Thrown by {@link Validator#validateOrThrow()} when validation produced at
 * least one error. Carries the full {@link ValidationResult} so callers can
 * inspect every recorded error, not just the exception's message.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The full validation outcome (all errors) that triggered this exception. */
    private final ValidationResult result;

    /**
     * Creates a new exception wrapping the given failed validation result.
     * The exception's message summarizes the error count and lists the
     * errors.
     *
     * @param result the validation result that triggered this exception; must not be valid
     */
    public ValidationException(ValidationResult result) {
        super("Validation failed with " + result.errorCount() + " error(s): " + result.errors());
        this.result = result;
    }

    /**
     * Returns the full validation result that triggered this exception.
     *
     * @return the underlying {@link ValidationResult}
     */
    public ValidationResult getResult() {
        return result;
    }
}
