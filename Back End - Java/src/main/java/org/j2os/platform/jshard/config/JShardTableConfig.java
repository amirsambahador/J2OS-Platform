package org.j2os.platform.jshard.config;

/**
 * Identifies a table to be sharded and the column its sharding decisions
 * are based on.
 * <p>
 * Both {@code name} and {@code shardingColumn} are validated as safe SQL
 * identifiers at construction time: non-blank, no leading/trailing
 * whitespace, and free of characters ({@code .}, {@code ,}, quotes,
 * whitespace, control characters) that could interfere with how
 * ShardingSphere composes actual data node expressions
 * (see {@link org.j2os.platform.jshard.datasource.JShardDataSourceProvider}).
 *
 * @param name           the logical table name
 * @param shardingColumn the column whose value determines which shard a row belongs to
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public record JShardTableConfig(String name, String shardingColumn) {

    /**
     * Canonical constructor; validates both {@code name} and
     * {@code shardingColumn} as safe SQL identifiers.
     *
     * @param name           the logical table name
     * @param shardingColumn the sharding column name
     * @throws IllegalArgumentException if either value is null/blank, has
     *                                   leading/trailing whitespace, or
     *                                   contains a disallowed character
     */
    public JShardTableConfig(String name, String shardingColumn) {
        this.name = requireValidIdentifier(name, "name");
        this.shardingColumn = requireValidIdentifier(shardingColumn, "shardingColumn");
    }

    /**
     * Validates that a value is neither {@code null} nor blank.
     *
     * @param value     the value to validate
     * @param fieldName the field's name, used in the exception message
     * @return {@code value}, unchanged
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank
     */
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ShardingTableConfig." + fieldName + " must not be null/blank");
        }
        return value;
    }

    /**
     * Validates that a value is a safe SQL identifier: non-blank, no
     * leading/trailing whitespace, and free of {@code .}, {@code ,},
     * quotes, whitespace, and control characters.
     *
     * @param value     the value to validate
     * @param fieldName the field's name, used in exception messages
     * @return {@code value}, unchanged
     * @throws IllegalArgumentException if {@code value} fails any of the checks above
     */
    private static String requireValidIdentifier(String value, String fieldName) {
        requireNonBlank(value, fieldName);
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    "ShardingTableConfig." + fieldName + " must not have leading/trailing whitespace: '" + value + "'");
        }
        if (value.contains(".") || value.contains(",") || value.contains("'") || value.contains("\"")) {
            throw new IllegalArgumentException(
                    "ShardingTableConfig." + fieldName + " must not contain '.', ',', or a quote character: '" + value + "'");
        }
        for (char c : value.toCharArray()) {
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException(
                        "ShardingTableConfig." + fieldName + " must not contain whitespace or control characters: '" + value + "'");
            }
        }
        return value;
    }

    @Override
    public String toString() {
        return "ShardingTableConfig{name='" + name + "', shardingColumn='" + shardingColumn + "'}";
    }
}
