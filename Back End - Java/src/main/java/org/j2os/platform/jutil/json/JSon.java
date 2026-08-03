package org.j2os.platform.jutil.json;

import lombok.experimental.UtilityClass;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Static-style JSON helper utility built on top of Jackson's
 * {@link ObjectMapper}.
 * <p>
 * This class is annotated with Lombok's {@link UtilityClass}, which makes
 * every member implicitly {@code static}, generates a private constructor,
 * and marks the class {@code final} - so it can be used directly as
 * {@code JSon.read(...)}, {@code JSon.write(...)}, etc. without needing to
 * instantiate it.
 * <p>
 * A single shared {@link #OBJECT_MAPPER} instance is reused across all
 * calls, since {@code ObjectMapper} is thread-safe for read/write
 * operations once configured.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@UtilityClass
public class JSon {

    /**
     * Shared Jackson mapper used for all serialization and deserialization
     * performed by this class. Uses default configuration.
     */
    private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Deserializes a JSON string into an instance of the given type.
     *
     * @param jsonString the JSON text to parse
     * @param type       the target class to deserialize into
     * @param <T>        the target type
     * @return an instance of {@code type} populated from {@code jsonString}
     * @throws JacksonException if {@code jsonString} is not
     *                                                   valid JSON or cannot be mapped
     *                                                   to {@code type}
     */
    public <T> T read(String jsonString, Class<T> type) {
        return OBJECT_MAPPER.readValue(jsonString, type);
    }

    /**
     * Serializes an object to its JSON string representation.
     *
     * @param instance the object to serialize
     * @return the JSON representation of {@code instance}
     * @throws JacksonException if {@code instance} cannot be serialized
     */
    public String write(Object instance) {
        return OBJECT_MAPPER.writeValueAsString(instance);
    }

    /**
     * Reads the raw text value of a (possibly nested) field from a JSON
     * string, following the given path of field names one level at a time.
     * <p>
     * Unlike {@link #readFieldAsString(String, String...)}, this returns the
     * scalar text content of the field (e.g. for a JSON string field, the
     * value without surrounding quotes). If the field is missing at any
     * point in the path, an empty string is returned instead of failing.
     *
     * @param jsonString the JSON text to read from
     * @param fieldNames the path of field names to follow, applied in order
     * @return the text value of the resolved field, or {@code ""} if any
     *         field in the path is missing
     * @throws JacksonException if {@code jsonString} is not valid JSON
     */
    public String readFieldAsText(String jsonString, String... fieldNames) {
        JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
        for (String fieldName : fieldNames) {
            rootNode = rootNode.path(fieldName);
        }
        return rootNode.asString("");
    }

    /**
     * Reads the JSON representation of a (possibly nested) field from a
     * JSON string, following the given path of field names one level at a
     * time.
     * <p>
     * Unlike {@link #readFieldAsText(String, String...)}, this returns the
     * node's own JSON text form (e.g. for a JSON string field, the value
     * including surrounding quotes; for an object or array field, its full
     * JSON representation).
     *
     * @param jsonString the JSON text to read from
     * @param fieldNames the path of field names to follow, applied in order
     * @return the JSON text of the resolved field, or the JSON text of a
     *         "missing node" if any field in the path is missing
     * @throws JacksonException if {@code jsonString} is not valid JSON
     */
    public String readFieldAsString(String jsonString, String... fieldNames) {
        JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
        for (String fieldName : fieldNames) {
            rootNode = rootNode.path(fieldName);
        }
        return rootNode.toString();
    }

    /**
     * Reads the raw text value of a field from the array element at the
     * given index in a JSON array string.
     *
     * @param jsonString the JSON array text to read from
     * @param index      the zero-based index of the element within the array
     * @param fieldName  the name of the field to read from that element
     * @return the text value of the field, or {@code ""} if there is no
     *         element at {@code index} or the field is missing on that
     *         element
     * @throws JacksonException if {@code jsonString} is not valid JSON
     */
    public String readFieldArrayAsText(String jsonString, int index, String fieldName) {
        JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
        JsonNode itemNode = rootNode.path(index);
        return itemNode.path(fieldName).asString("");
    }

    /**
     * Reads the JSON representation of a field from the array element at
     * the given index in a JSON array string.
     *
     * @param jsonString the JSON array text to read from
     * @param index      the zero-based index of the element within the array
     * @param fieldName  the name of the field to read from that element
     * @return the JSON text of the field, or the JSON text of a
     *         "missing node" if there is no element at {@code index} or the
     *         field is missing on that element
     * @throws JacksonException if {@code jsonString} is not valid JSON
     */
    public String readFieldArrayAsString(String jsonString, int index, String fieldName) {
        JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
        JsonNode itemNode = rootNode.path(index);
        return itemNode.path(fieldName).toString();
    }
}