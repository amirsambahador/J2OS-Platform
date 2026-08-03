package org.j2os.platform.page2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Post-processes a {@code getResult()} map (as produced by {@link PageDataEntity},
 * {@link PageDataJPQL}, {@link PageDataList}, or {@link PageDataSQL}) by removing, blanking,
 * masking, replacing, or adding fields on each row, working on the row's JSON-like
 * {@code Map<String, Object>} representation rather than the original entity.
 * <p>
 * Typical usage: wrap a result map, chain any combination of {@link #remove}, {@link #empty},
 * {@link #mask}, and {@link #put}, then call {@link #getResult()} exactly once to obtain the
 * transformed result.
 * <p>
 * Each row in the result's {@code "rows"} list is converted to its own, independent
 * {@code Map<String, Object>} representation before rules are applied (via Jackson for rows
 * that are entities/POJOs, or via a defensive copy for rows that are already a {@code Map} -
 * e.g. results from {@link PageDataSQL}/{@link PageDataJPQL}, whose {@code "rows"} entries are
 * already {@code Map<String, Object>}), so the original entity type is not retained in the
 * output and no row-level mutation performed by this class is ever visible through the row
 * object passed to a {@code put} transformer.
 * <p>
 * <b>Not thread-safe</b> and <b>single-use</b>: rules accumulate via chained calls, and
 * {@link #getResult()} may only be called once per instance — a second call throws
 * {@link IllegalStateException}. Create a new instance per result/request.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>Field paths passed to {@link #remove}, {@link #empty}, {@link #mask}, and {@link #put}
 *       are dot-separated but are not validated against any whitelist (unlike the field names
 *       used by the {@code PageData*} query classes) — they are only used to navigate/build
 *       nested maps.</li>
 *   <li>Both {@link #put(String, Class, BiFunction)} overloads treat a {@code null} return value from
 *       the transformer the same way: the field is set to {@code null} (or added, if absent),
 *       exactly like {@link #put(String, Function)} — a transformer never has to return a
 *       sentinel to blank a field, {@code null} always means "set this field to null".
 *       {@link #put(String, Class, BiFunction)}'s existing-value conversion, and any exception
 *       thrown by a transformer function, are caught and logged at {@code WARN} level rather
 *       than propagated — a failing transformer silently leaves that field's rule unapplied
 *       instead of failing the whole request.</li>
 *   <li>Rules are applied in the order they were added, per row; a later rule can see the
 *       effect of an earlier rule on the same field, but rules on different fields do not see
 *       each other's row-level side effects beyond what each transformer function itself reads
 *       from the map.</li>
 *   <li>If {@code result} has no {@code "rows"} entry, or it isn't a {@link List}, all rules are
 *       skipped and {@code result} is returned unchanged (rather than throwing) — this can mask
 *       a caller passing the wrong kind of result map.</li>
 *   <li>A {@code null} entry within {@code result}'s {@code "rows"} list (e.g. from a caller-
 *       supplied JPQL/SQL query with an outer join that can produce a null row) is passed
 *       through to the output unchanged, with no rule applied to it, rather than throwing.</li>
 * </ul>
 *
 * @param <T> the row/entity type the result's {@code "rows"} list originally contained
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class PageDataResultFilter<T> {

    /** Logger used to record transformer failures that are caught rather than propagated. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PageDataResultFilter.class);

    /** Shared mapper used to convert each row to/from its {@code Map<String, Object>} representation. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The placeholder value written by {@link #mask(String)}. */
    private static final String DEFAULT_MASK = "********";

    /** The {@code getResult()}-shaped map being post-processed. */
    private final Map<String, Object> result;

    /** Rules accumulated so far, applied in this order to every row. */
    private final List<RuleEntry> rules = new ArrayList<>();

    /** Whether {@link #getResult()} has already been called on this instance. */
    private boolean consumed = false;

    /**
     * Creates a new filter wrapping the given result map.
     *
     * @param result the {@code getResult()}-shaped map to post-process
     * @throws NullPointerException if {@code result} is null
     */
    public PageDataResultFilter(Map<String, Object> result) {
        this.result = Objects.requireNonNull(result);
    }

    /**
     * Removes a (possibly dotted, nested) field from a map, doing nothing if any intermediate
     * segment is missing or not itself a map.
     *
     * @param map  the map to remove the field from
     * @param path the dot-separated field path
     */
    @SuppressWarnings("unchecked")
    private static void removeNested(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return;
            }
        }
        current.remove(parts[parts.length - 1]);
    }

    /**
     * Sets a (possibly dotted, nested) field on a map to the given value, creating any missing
     * intermediate maps along the way.
     *
     * @param map   the map to set the field on
     * @param path  the dot-separated field path
     * @param value the value to set
     */
    @SuppressWarnings("unchecked")
    private static void setNested(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<>();
                current.put(parts[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    /**
     * Reads a (possibly dotted, nested) field's current value from a map.
     *
     * @param map  the map to read from
     * @param path the dot-separated field path
     * @return the value at that path, or {@code null} if any segment is missing or not itself a map
     */
    private static Object getNested(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Registers a rule that removes the given field from every row.
     *
     * @param field the (possibly dotted, nested) field path to remove
     * @return this instance, for chaining
     */
    public PageDataResultFilter<T> remove(String field) {
        rules.add(new RuleEntry(field, Rule.remove()));
        return this;
    }

    /**
     * Registers a rule that replaces the given field's value with an empty string on every row.
     *
     * @param field the (possibly dotted, nested) field path to blank
     * @return this instance, for chaining
     */
    public PageDataResultFilter<T> empty(String field) {
        rules.add(new RuleEntry(field, Rule.empty()));
        return this;
    }

    /**
     * Registers a rule that replaces the given field's value with a fixed mask string on every row.
     *
     * @param field the (possibly dotted, nested) field path to mask
     * @return this instance, for chaining
     */
    public PageDataResultFilter<T> mask(String field) {
        rules.add(new RuleEntry(field, Rule.mask()));
        return this;
    }

    /**
     * Registers a rule that replaces the given field's value using a function of the row entity
     * and the field's current value (converted to {@code targetClass} first). A {@code null}
     * value returned by {@code transformer} sets the field to {@code null}, the same as
     * {@link #put(String, Function)} — there is no way for a transformer to signal "leave this
     * field unchanged" other than not registering the rule at all.
     *
     * @param field       the (possibly dotted, nested) field path to transform
     * @param targetClass the type to convert the field's current value to before calling {@code transformer}
     * @param transformer the function computing the new field value from the row entity and current value
     * @param <V>         the field's value type
     * @return this instance, for chaining
     */
    public <V> PageDataResultFilter<T> put(String field, Class<V> targetClass,
                                           BiFunction<T, V, V> transformer) {
        rules.add(new RuleEntry(field, Rule.putBiTransform(targetClass, transformer)));
        return this;
    }

    /**
     * Registers a rule that sets (or adds) the given field's value using a function of the row
     * entity alone.
     *
     * @param field       the (possibly dotted, nested) field path to set
     * @param transformer the function computing the field value from the row entity
     * @return this instance, for chaining
     */
    public PageDataResultFilter<T> put(String field, Function<T, Object> transformer) {
        rules.add(new RuleEntry(field, Rule.addField(transformer)));
        return this;
    }


    /**
     * Applies every registered rule to every row and returns the transformed result map.
     * <p>
     * Each non-null row is first converted to its own, independent {@code Map<String, Object>}
     * representation before rules are applied; if the result's {@code "rows"} entry is missing
     * or not a {@link List}, the result is returned unchanged and no rules are applied.
     *
     * @return the same map instance passed to the constructor, with its {@code "rows"} entry
     *         replaced by the transformed rows (unless there was nothing to transform)
     * @throws IllegalStateException if this method has already been called on this instance
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getResult() {
        if (consumed) {
            throw new IllegalStateException(
                    "PageDataResultFilter.getResult() has already been called on this instance; "
                            + "create a new instance for each result/request instead of reusing this one");
        }
        consumed = true;

        Object rowsObj = result.get("rows");
        if (!(rowsObj instanceof List<?> list)) {
            return result;
        }

        List<Object> newRows = new ArrayList<>(list.size());

        for (Object entityObj : list) {
            if (entityObj == null) {
                // A caller-supplied JPQL/SQL query with an outer join can legitimately produce
                // a null row; pass it through untouched rather than failing the whole request.
                newRows.add(null);
                continue;
            }

            T item = (T) entityObj;

            // Rows from PageDataSQL/PageDataJPQL are already Map<String,Object> - convertValue
            // on a Map source/target pair commonly returns the same reference rather than a
            // copy, which would let a rule on one field see another field's in-progress edits
            // through `item` itself. A defensive copy keeps `map` (what rules mutate) and
            // `item` (what's passed to put()'s transformer functions) independent, regardless
            // of which PageData* class produced the row.
            Map<String, Object> map = (item instanceof Map)
                    ? new LinkedHashMap<>((Map<String, Object>) item)
                    : MAPPER.convertValue(item, Map.class);

            for (RuleEntry entry : rules) {
                String fieldPath = entry.fieldPath();
                Rule rule = entry.rule();

                switch (rule.action()) {
                    case REMOVE -> removeNested(map, fieldPath);
                    case EMPTY -> setNested(map, fieldPath, "");
                    case MASK -> setNested(map, fieldPath, DEFAULT_MASK);
                    case BI_TRANSFORM, ADD_FIELD -> {
                        // Both rule kinds now set unconditionally, including a null result -
                        // a transformer returning null always means "set this field to null",
                        // for BI_TRANSFORM exactly as it already did for ADD_FIELD. If
                        // computeTransformedValue caught an exception (rather than the
                        // transformer returning null on purpose), it already logged a WARN
                        // and returned null here, so the field is set to null in that case too.
                        Object newValue = computeTransformedValue(item, map, fieldPath, rule);
                        setNested(map, fieldPath, newValue);
                    }
                }
            }
            newRows.add(map);
        }

        result.put("rows", newRows);
        return result;
    }

    /**
     * Computes a BI_TRANSFORM or ADD_FIELD rule's new value for one row, converting the
     * field's current value to the rule's target class first if applicable. Any exception is
     * caught and logged rather than propagated.
     *
     * @param item      the original row entity
     * @param map       the row's current map representation
     * @param fieldPath the field path the rule applies to
     * @param rule      the rule to evaluate
     * @return the computed value, or {@code null} if computing it failed or the rule has no transformer
     */
    @SuppressWarnings("unchecked")
    private Object computeTransformedValue(T item, Map<String, Object> map,
                                           String fieldPath, Rule rule) {
        try {
            if (rule.biTransformer() != null) {
                Object current = getNested(map, fieldPath);
                if (current != null && rule.targetClass() != null) {
                    current = MAPPER.convertValue(current, rule.targetClass());
                }
                return ((BiFunction<T, Object, Object>) rule.biTransformer()).apply(item, current);
            } else if (rule.transformer() != null) {
                return ((Function<T, Object>) rule.transformer()).apply(item);
            }
        } catch (Exception e) {
            LOGGER.warn("PageDataResultFilter: transform failed for field '{}'", fieldPath, e);
        }
        return null;
    }

    private enum Action {
        REMOVE, EMPTY, MASK, BI_TRANSFORM, ADD_FIELD
    }

    private record Rule(
            Action action,
            Function<?, Object> transformer,
            BiFunction<?, ?, Object> biTransformer,
            Class<?> targetClass
    ) {
        static Rule remove() {
            return new Rule(Action.REMOVE, null, null, null);
        }

        static Rule empty() {
            return new Rule(Action.EMPTY, null, null, null);
        }

        static Rule mask() {
            return new Rule(Action.MASK, null, null, null);
        }

        static <U, V> Rule putBiTransform(Class<V> targetClass, BiFunction<U, V, V> biTransformer) {
            @SuppressWarnings("unchecked")
            BiFunction<?, ?, Object> casted = (BiFunction<?, ?, Object>) biTransformer;
            return new Rule(Action.BI_TRANSFORM, null, casted, targetClass);
        }

        static <U> Rule addField(Function<U, Object> transformer) {
            return new Rule(Action.ADD_FIELD, transformer, null, null);
        }
    }

    private record RuleEntry(String fieldPath, Rule rule) {
    }
}