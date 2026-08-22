package org.jshard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JShardConnectionConfig}: required-field validation,
 * optional pool-tuning parameters, and the log-safety guarantees of
 * {@link JShardConnectionConfig#toString()}.
 */
class JShardConnectionConfigTest {

    private static JShardConnectionConfig.Builder validBuilder() {
        return JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl("jdbc:postgresql://db-host:5432/mydb")
                .username("app_user")
                .password("s3cr3t")
                .poolSize(10);
    }

    // ---------------------------------------------------------------
    // of() / builder() happy path
    // ---------------------------------------------------------------

    @Test
    void of_withAllRequiredFields_buildsSuccessfully() {
        JShardConnectionConfig config = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://db-host:5432/mydb", "app_user", "s3cr3t", 10);
        assertEquals("org.postgresql.Driver", config.getDriverClassName());
        assertEquals("jdbc:postgresql://db-host:5432/mydb", config.getJdbcUrl());
        assertEquals("app_user", config.getUsername());
        assertEquals("s3cr3t", config.getPassword());
        assertEquals(10, config.getPoolSize());
    }

    @Test
    void builder_withOnlyRequiredFields_leavesOptionalFieldsNull() {
        JShardConnectionConfig config = validBuilder().build();
        assertNull(config.getConnectionTimeoutMs());
        assertNull(config.getIdleTimeoutMs());
        assertNull(config.getMaxLifetimeMs());
        assertNull(config.getMinimumIdle());
        assertNull(config.getValidationTimeoutMs());
        assertNull(config.getKeepaliveTimeMs());
    }

    @Test
    void builder_withOptionalPoolTuningFields_setsThemAll() {
        JShardConnectionConfig config = validBuilder()
                .connectionTimeoutMs(30_000)
                .idleTimeoutMs(600_000)
                .maxLifetimeMs(1_800_000)
                .minimumIdle(2)
                .validationTimeoutMs(5_000)
                .keepaliveTimeMs(300_000)
                .build();

        assertEquals(30_000L, config.getConnectionTimeoutMs());
        assertEquals(600_000L, config.getIdleTimeoutMs());
        assertEquals(1_800_000L, config.getMaxLifetimeMs());
        assertEquals(2, config.getMinimumIdle());
        assertEquals(5_000L, config.getValidationTimeoutMs());
        assertEquals(300_000L, config.getKeepaliveTimeMs());
    }

    // ---------------------------------------------------------------
    // Required-field validation
    // ---------------------------------------------------------------

    @Test
    void missingDriverClassName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardConnectionConfig.builder()
                        .jdbcUrl("jdbc:postgresql://db-host:5432/mydb")
                        .username("app_user")
                        .password("s3cr3t")
                        .poolSize(10)
                        .build());
    }

    @Test
    void blankJdbcUrl_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardConnectionConfig.builder()
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("   ")
                        .username("app_user")
                        .password("s3cr3t")
                        .poolSize(10)
                        .build());
    }

    @Test
    void nullUsername_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardConnectionConfig.builder()
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("jdbc:postgresql://db-host:5432/mydb")
                        .password("s3cr3t")
                        .poolSize(10)
                        .build());
    }

    @Test
    void blankPassword_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardConnectionConfig.builder()
                        .driverClassName("org.postgresql.Driver")
                        .jdbcUrl("jdbc:postgresql://db-host:5432/mydb")
                        .username("app_user")
                        .password("")
                        .poolSize(10)
                        .build());
    }

    @Test
    void zeroOrNegativePoolSize_throws() {
        assertThrows(IllegalArgumentException.class, () -> validBuilder().poolSize(0).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().poolSize(-5).build());
    }

    @Test
    void of_withBlankRequiredField_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardConnectionConfig.of("org.postgresql.Driver", "jdbc:postgresql://x", "", "pw", 10));
    }

    // ---------------------------------------------------------------
    // Log safety: toString() must never leak the password, and must
    // strip any query string (which may itself carry credentials/tokens)
    // from the JDBC URL.
    // ---------------------------------------------------------------

    @Test
    void toString_neverContainsThePassword() {
        JShardConnectionConfig config = validBuilder().password("super-secret-value").build();
        assertFalse(config.toString().contains("super-secret-value"));
    }

    @Test
    void toString_stripsQueryStringFromJdbcUrl() {
        JShardConnectionConfig config = JShardConnectionConfig.of(
                "org.postgresql.Driver",
                "jdbc:postgresql://db-host:5432/mydb?password=leaked&user=leaked",
                "app_user", "s3cr3t", 5);
        String s = config.toString();
        assertFalse(s.contains("leaked"), "Query string was not stripped from toString(): " + s);
        assertTrue(s.contains("jdbc:postgresql://db-host:5432/mydb"));
    }

    @Test
    void toString_masksLongUsername_keepingFirstAndLastCharacterOnly() {
        JShardConnectionConfig config = validBuilder().username("administrator").build();
        String s = config.toString();
        assertTrue(s.contains("a***r"), "Expected masked username 'a***r' in: " + s);
        assertFalse(s.contains("administrator"));
    }

    @Test
    void toString_masksShortUsername_fully() {
        JShardConnectionConfig config = validBuilder().username("ab").build();
        String s = config.toString();
        assertTrue(s.contains("**"));
        assertFalse(s.contains("ab,") ); // crude guard against the raw value leaking unmasked
    }

    @Test
    void toString_includesDriverAndPoolSize() {
        JShardConnectionConfig config = validBuilder().poolSize(17).build();
        String s = config.toString();
        assertTrue(s.contains("org.postgresql.Driver"));
        assertTrue(s.contains("17"));
    }

    // ---------------------------------------------------------------
    // equals()/hashCode()
    // ---------------------------------------------------------------

    @Test
    void equals_isTrueForIdenticalConfigs() {
        JShardConnectionConfig a = validBuilder().build();
        JShardConnectionConfig b = validBuilder().build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_isFalseWhenAFieldDiffers() {
        JShardConnectionConfig a = validBuilder().build();
        JShardConnectionConfig b = validBuilder().poolSize(11).build();
        assertNotEquals(a, b);
    }
}
