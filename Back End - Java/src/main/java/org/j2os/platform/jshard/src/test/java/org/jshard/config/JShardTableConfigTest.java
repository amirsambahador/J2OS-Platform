package org.jshard.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JShardTableConfig}'s SQL-identifier validation.
 */
class JShardTableConfigTest {

    @Test
    void validNameAndColumn_isAccepted() {
        JShardTableConfig config = new JShardTableConfig("orders", "user_id");
        assertEquals("orders", config.name());
        assertEquals("user_id", config.shardingColumn());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void blankName_isRejected(String blank) {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig(blank, "user_id"));
    }

    @Test
    void nullName_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig(null, "user_id"));
    }

    @Test
    void nullShardingColumn_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig("orders", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {" orders", "orders ", " orders "})
    void leadingOrTrailingWhitespace_isRejected(String badName) {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig(badName, "user_id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sch.orders", "orders,archive", "orders'", "orders\"", "order name", "orders\tarchive"})
    void disallowedCharacters_areRejected(String badName) {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig(badName, "user_id"));
    }

    @Test
    void disallowedCharacters_inShardingColumn_areAlsoRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig("orders", "user.id"));
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig("orders", "user id"));
    }

    @Test
    void controlCharacters_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JShardTableConfig("orders\u0007", "user_id"));
    }

    @Test
    void recordEquality_isValueBased() {
        assertEquals(new JShardTableConfig("orders", "user_id"), new JShardTableConfig("orders", "user_id"));
        assertNotEquals(new JShardTableConfig("orders", "user_id"), new JShardTableConfig("orders", "order_id"));
    }

    @Test
    void toString_doesNotThrowAndContainsBothFields() {
        String s = new JShardTableConfig("orders", "user_id").toString();
        assertTrue(s.contains("orders"));
        assertTrue(s.contains("user_id"));
    }
}
