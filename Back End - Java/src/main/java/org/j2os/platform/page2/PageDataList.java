package org.j2os.platform.page2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a paginated, filterable, searchable "datagrid" result from an in-memory
 * {@link List} of Java objects, using reflection to read (possibly nested) field values.
 * <p>
 * Typical usage: instantiate, call {@link #searchAndSortOn(String...)} and any combination
 * of {@link #where}/{@link #and}/{@link #or}, then call {@link #getResult(List, Map)} with
 * the data to filter and the incoming grid parameters ({@code page}, {@code rows},
 * {@code sort}, {@code order}, {@code q}).
 * <p>
 * <b>Not thread-safe.</b> Filter/search state ({@code conditions}, {@code searchFields})
 * accumulates via chained calls, so a new instance must be created for every request/query —
 * never share or reuse one instance (e.g. as a singleton Spring bean) across requests.
 * <p>
 * <b>Sortable/searchable fields are opt-in only, via {@link #searchAndSortOn(String...)}.</b>
 * A field passed only to {@link #where}/{@link #and}/{@link #or} is used solely to filter the
 * in-memory data and is <em>not</em> added to the sort whitelist or the free-text search — this
 * is deliberate, so a developer can safely add a server-side/security filter (e.g.
 * {@code where("organizationId", "=", currentOrgId)}) without that field becoming something an
 * end user can request via the {@code sort} or {@code q} grid parameter. If a field used in a
 * filter should also be sortable and/or searchable, register it explicitly with
 * {@link #searchAndSortOn(String...)} as well.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>{@code data} and {@code gridParams} must not be {@code null}; {@code gridParams} must
 *       also include a {@code "sort"} key — all three are mandatory, not optional, and a
 *       violation throws {@link IllegalArgumentException} rather than being defaulted.</li>
 *   <li>{@code page}/{@code rows}, if present in {@code gridParams}, must be parseable as
 *       integers; a non-numeric value throws {@link IllegalArgumentException} with a clear
 *       message (an explicit {@code null} value for any grid parameter is treated the same as
 *       the key being absent, and falls back to the default).</li>
 *   <li>Only fields registered via {@link #searchAndSortOn(String...)} can be sorted on; sorting
 *       by an unregistered field — including one that was only used in {@code where}/{@code and}/
 *       {@code or} — throws {@link RuntimeException}.</li>
 *   <li>Field names passed to {@code where}/{@code and}/{@code or}/{@code searchAndSortOn} are
 *       validated against a whitelist regex, but the sort direction resolution only recognizes
 *       {@code "DESC"} (case-insensitive) as descending — any other value, including invalid
 *       input, silently falls back to ascending.</li>
 *   <li>{@code LIKE}/{@code NOT LIKE} passed explicitly to {@code where}/{@code and}/{@code or}
 *       use SQL-style pattern matching: {@code %} matches any sequence of characters (including
 *       none) and {@code _} matches exactly one character, matched case-insensitively against the
 *       whole field value — the same semantics as {@link PageDataEntity}, {@link PageDataJPQL},
 *       and {@link PageDataSQL}. A caller that previously relied on this class's earlier
 *       plain-substring ({@code contains()}) behavior must now supply {@code %} explicitly (e.g.
 *       {@code "%term%"} for "contains") — this is a breaking behavior change from earlier
 *       versions of this class. A {@code null} field value never matches {@code LIKE} nor
 *       {@code NOT LIKE} (mirroring SQL's "unknown" comparison result, which excludes the row
 *       from both), matching {@link PageDataEntity}/{@link PageDataJPQL}/{@link PageDataSQL}.
 *       The free-text search ({@code q}) uses the same {@code %}/{@code _} wildcard rules for a
 *       plain (unquoted) token — matching {@link PageDataEntity}/{@link PageDataJPQL}/
 *       {@link PageDataSQL}'s (unescaped) {@code LIKE '%token%'} — but a quoted, exact-match
 *       {@code q} token is compared with plain {@code equals()}, so {@code %}/{@code _} inside
 *       quotes are always literal characters, never wildcards.</li>
 *   <li>Every comparison operator (including {@code >}, {@code >=}, {@code <}, and
 *       {@code <=}, not just {@code =}/{@code !=}/{@code <>}) against an explicit {@code null}
 *       value never matches, mirroring SQL's three-valued logic where a comparison against
 *       {@code NULL} is always "unknown" rather than true or false — use
 *       {@code "IS NULL"}/{@code "IS NOT NULL"} instead to test for nullness. This matches
 *       {@link PageDataEntity}, which binds {@code null} as an ordinary parameter and likewise
 *       never matches a row for any comparison operator.</li>
 *   <li>Combining {@code and}/{@code or} conditions follows standard boolean precedence (AND
 *       binds tighter than OR): {@code where(A).or(B).and(C)} evaluates as {@code A OR (B AND C)}.</li>
 *   <li>Reflection walks up the class hierarchy to find declared fields (so inherited fields
 *       are found), but any reflective access failure (missing field, security manager
 *       restriction, etc.) is caught and treated as a {@code null} field value rather than
 *       thrown, so a single unreadable field does not fail the whole request — the failure is
 *       still logged at {@code DEBUG} level so it is not entirely silent.</li>
 *   <li>Sorting relies on field values implementing {@link Comparable}; a non-comparable field
 *       value used as the sort field throws a {@link ClassCastException} at sort time.</li>
 *   <li>The entire input list is filtered, sorted, and only then paged in memory — there is no
 *       lazy/streaming evaluation, so this class is best suited to modestly sized in-memory
 *       collections, not large datasets (use {@link PageDataSQL}, {@link PageDataJPQL}, or
 *       {@link PageDataEntity} for those instead).</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class PageDataList {

    /** Logger used to record reflective field-access failures that are caught rather than propagated. */
    private static final Logger LOGGER = LoggerFactory.getLogger(PageDataList.class);

    /** Characters with special meaning inside a regex that must be escaped when translating a LIKE pattern. */
    private static final String REGEX_SPECIAL_CHARS = "\\.^$|?*+()[]{}";

    /** Accumulated filter conditions, applied in the order they were added. */
    private final List<FilterCondition> conditions = new ArrayList<>();

    /**
     * Fields the free-text search ({@code q}) is matched against, and the complete (and only)
     * whitelist of fields eligible for {@code sort} — registered exclusively via
     * {@link #searchAndSortOn(String...)}. A field used only in {@link #where}/{@link #and}/
     * {@link #or} is deliberately <em>not</em> added here (see class-level javadoc): filtering on
     * a field is not, by itself, a statement that the field is safe to expose to a caller-supplied
     * {@code sort} or {@code q} value.
     * <p>
     * A {@link LinkedHashSet} so that registering the same field twice (accidentally or
     * otherwise) via {@link #searchAndSortOn(String...)} never produces a duplicate search
     * expression — insertion order is preserved for deterministic search-term ordering.
     */
    private final Set<String> searchFields = new LinkedHashSet<>();

    /**
     * Finds a declared field by name, walking up the class hierarchy so inherited fields are
     * found as well as fields declared directly on {@code startClass}.
     *
     * @param startClass the class to start searching from
     * @param name       the field name to find
     * @return the matching field
     * @throws NoSuchFieldException if no field with that name is declared anywhere in the hierarchy
     */
    private static Field findField(Class<?> startClass, String name) throws NoSuchFieldException {
        for (Class<?> c = startClass; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Not on this class; check the superclass next.
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ---------------- Filter Methods ----------------

    /**
     * Adds a filter condition, equivalent to calling {@link #where(String, String, Object, Function)}
     * with no value transformer.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field    the field to filter on; validated against the field name whitelist
     * @param operator the comparison operator (e.g. {@code "="}, {@code "LIKE"}, {@code "IS NULL"})
     * @param value    the value to compare the field against
     * @return this instance, for chaining
     */
    public PageDataList where(String field, String operator, Object value) {
        return where(field, operator, value, null);
    }

    /**
     * Adds a filter condition combined with any existing conditions using AND.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field       the field to filter on; validated against the field name whitelist
     * @param operator    the comparison operator (e.g. {@code "="}, {@code "LIKE"}, {@code "IS NULL"})
     * @param value       the value to compare the field against
     * @param transformer an optional transform applied to {@code value} before comparison, or {@code null} for none
     * @return this instance, for chaining
     * @throws IllegalArgumentException if {@code field} is null, empty, or fails the field name whitelist
     */
    public PageDataList where(String field, String operator, Object value, Function<Object, Object> transformer) {
        PageDataSupport.ensureValidFieldName(field);
        conditions.add(new FilterCondition("AND", field, operator, value, transformer));
        return this;
    }

    /**
     * Adds a filter condition, equivalent to calling {@link #and(String, String, Object, Function)}
     * with no value transformer.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field    the field to filter on; validated against the field name whitelist
     * @param operator the comparison operator
     * @param value    the value to compare the field against
     * @return this instance, for chaining
     */
    public PageDataList and(String field, String operator, Object value) {
        return and(field, operator, value, null);
    }

    /**
     * Adds a filter condition combined with any existing conditions using AND.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field       the field to filter on; validated against the field name whitelist
     * @param operator    the comparison operator
     * @param value       the value to compare the field against
     * @param transformer an optional transform applied to {@code value} before comparison, or {@code null} for none
     * @return this instance, for chaining
     * @throws IllegalArgumentException if {@code field} is null, empty, or fails the field name whitelist
     */
    public PageDataList and(String field, String operator, Object value, Function<Object, Object> transformer) {
        PageDataSupport.ensureValidFieldName(field);
        conditions.add(new FilterCondition("AND", field, operator, value, transformer));
        return this;
    }

    /**
     * Adds a filter condition, equivalent to calling {@link #or(String, String, Object, Function)}
     * with no value transformer.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field    the field to filter on; validated against the field name whitelist
     * @param operator the comparison operator
     * @param value    the value to compare the field against
     * @return this instance, for chaining
     */
    public PageDataList or(String field, String operator, Object value) {
        return or(field, operator, value, null);
    }

    /**
     * Adds a filter condition combined with the immediately preceding condition using OR.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field       the field to filter on; validated against the field name whitelist
     * @param operator    the comparison operator
     * @param value       the value to compare the field against
     * @param transformer an optional transform applied to {@code value} before comparison, or {@code null} for none
     * @return this instance, for chaining
     * @throws IllegalArgumentException if {@code field} is null, empty, or fails the field name whitelist
     */
    public PageDataList or(String field, String operator, Object value, Function<Object, Object> transformer) {
        PageDataSupport.ensureValidFieldName(field);
        conditions.add(new FilterCondition("OR", field, operator, value, transformer));
        return this;
    }

    // ---------------- Search Fields ----------------

    /**
     * Registers which fields the free-text search ({@code q} in {@code gridParams}) is matched
     * against, and makes them eligible as a sort field. This is the <b>only</b> way a field
     * becomes sortable or participates in free-text search — registering the same field more
     * than once (in one call or across calls) has no additional effect beyond the first
     * registration.
     *
     * @param fields the field names to register; each validated against the field name whitelist
     * @return this instance, for chaining
     * @throws IllegalArgumentException if any field is null, empty, or fails the field name whitelist
     */
    public PageDataList searchAndSortOn(String... fields) {
        for (String f : fields) {
            PageDataSupport.ensureValidFieldName(f);
            searchFields.add(f);
        }
        return this;
    }

    // ---------------- Core Get Result ----------------

    /**
     * Filters, searches, sorts, and pages the given data according to the conditions and
     * search fields configured on this instance and the given grid parameters.
     *
     * @param data       the full, unfiltered list of items to page over; must not be {@code null}
     * @param gridParams the incoming grid parameters; must not be {@code null} and must include
     *                   {@code "sort"}, and may include {@code "order"} ({@code "ASC"}/{@code "DESC"}),
     *                   {@code "page"}, {@code "rows"}, and {@code "q"} (free-text search)
     * @param <T>        the type of item in {@code data}
     * @return a map with keys {@code total}, {@code rows}, {@code page}, {@code size}, and {@code search}
     * @throws IllegalArgumentException if {@code data} or {@code gridParams} is {@code null}, if
     *                                   {@code gridParams} has no {@code "sort"} entry, or if
     *                                   {@code "page"}/{@code "rows"} is present but not a valid integer
     * @throws RuntimeException          if the requested sort field was not registered via
     *                                   {@link #searchAndSortOn(String...)}
     */
    public <T> Map<String, Object> getResult(List<T> data, Map<String, Object> gridParams) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (gridParams == null) {
            throw new IllegalArgumentException("gridParams must not be null (sort is mandatory)");
        }
        Object sortValue = gridParams.get("sort");
        if (sortValue == null) {
            throw new IllegalArgumentException("gridParams must include a 'sort' field (sort is mandatory)");
        }
        String sortField = sortValue.toString();
        if (!searchFields.contains(sortField)) {
            throw new RuntimeException("%s is not sortable".formatted(sortField));
        }

        Stream<T> stream = data.stream();

        if (!conditions.isEmpty()) {
            stream = stream.filter(this::matchesConditions);
        }

        if (!searchFields.isEmpty() && gridParams.containsKey("q")) {
            String searchValue = PageDataSupport.getStringParam(gridParams, "q", "").toLowerCase(Locale.ROOT).trim();
            if (!searchValue.isEmpty()) {
                String[] tokens = tokenizeSearch(searchValue);
                boolean exactMatch = PageDataSupport.EXACT_MATCH_PATTERN.matcher(searchValue).matches();
                stream = stream.filter(item -> matchesSearch(item, tokens, exactMatch));
            }
        }

        List<T> allResults = stream.collect(Collectors.toList());
        long total = allResults.size();

        String order = "DESC".equalsIgnoreCase(PageDataSupport.getStringParam(gridParams, "order", "ASC")) ? "DESC" : "ASC";
        // sortField was already validated above to be a registered searchAndSortOn field, so it
        // is always safe to sort on here.
        @SuppressWarnings("unchecked")
        Comparator<T> cmp = Comparator.comparing(
                o -> (Comparable) getNestedFieldValue(o, sortField),
                Comparator.nullsLast(Comparator.naturalOrder()));
        if ("DESC".equals(order)) cmp = cmp.reversed();
        allResults.sort(cmp);

        int pageNumber = PageDataSupport.parseIntParam(gridParams, "page", 1);
        int size = PageDataSupport.parseIntParam(gridParams, "rows", 10);
        int pageIndex = pageNumber - 1;
        if (pageIndex < 0) pageIndex = 0;
        if (size < 1) size = 10;

        List<T> content = allResults.stream().skip((long) pageIndex * size).limit(size).collect(Collectors.toList());

        // Report the actual (clamped) page/size that was applied, not the raw caller-supplied
        // value, so the response metadata always matches what was actually returned.
        return buildDataGridMap(content, total, pageIndex + 1, size, gridParams);
    }

    // ---------------- Helpers ----------------

    /**
     * Splits a trimmed, lower-cased search value into search tokens, honoring the fully
     * double-quoted exact-match form (see {@link PageDataSupport#EXACT_MATCH_PATTERN}). For any other input,
     * literal {@code "} characters (e.g. from an unterminated or partially quoted query) are
     * stripped before splitting on whitespace, so a stray quote character never ends up embedded
     * in a generated search token.
     *
     * @param searchValue the trimmed, lower-cased search value
     * @return the resulting search tokens
     */
    private String[] tokenizeSearch(String searchValue) {
        boolean exactMatch = PageDataSupport.EXACT_MATCH_PATTERN.matcher(searchValue).matches();
        return exactMatch
                ? Arrays.stream(searchValue.split("\"")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new)
                : searchValue.replace("\"", "").trim().split("\\s+");
    }

    /**
     * Evaluates every configured condition against an item, combining consecutive AND
     * conditions into groups and OR-ing the groups together (so AND binds tighter than OR,
     * matching standard boolean operator precedence).
     *
     * @param item the item to test
     * @return true if the item matches the combined conditions
     */
    private boolean matchesConditions(Object item) {
        boolean overallResult = false;
        boolean groupResult = true;
        boolean firstCondition = true;
        for (FilterCondition cond : conditions) {
            boolean condResult = matchesCondition(item, cond);
            if (firstCondition) {
                groupResult = condResult;
                firstCondition = false;
            } else if ("OR".equalsIgnoreCase(cond.logical())) {
                overallResult = overallResult || groupResult;
                groupResult = condResult;
            } else {
                groupResult = groupResult && condResult;
            }
        }
        return overallResult || groupResult;
    }

    /**
     * Evaluates a single condition against an item.
     *
     * @param item the item to test
     * @param cond the condition to evaluate
     * @return true if the item's field value satisfies the condition
     * @throws IllegalArgumentException if the condition's operator is not one of the supported operators
     */
    private boolean matchesCondition(Object item, FilterCondition cond) {
        Object fieldValue = getNestedFieldValue(item, cond.field());
        String operator = cond.operator().toUpperCase(Locale.ROOT);

        // IS NULL / IS NOT NULL never need the (possibly transformer-bearing) comparison value,
        // so it is never evaluated for them - avoids calling a transformer on a value the caller
        // never intended to be read for a null-check condition.
        if (operator.equals("IS NULL")) return fieldValue == null;
        if (operator.equals("IS NOT NULL")) return fieldValue != null;

        Object value = cond.getTransformedValue();

        // Every operator here other than IS NULL/IS NOT NULL compares the field to an explicit
        // value; a comparison against NULL is always SQL's three-valued "unknown" - never true -
        // regardless of which operator or the field's own value, so none of them ever match when
        // value is null. This matches PageDataEntity, which binds null as an ordinary JPQL
        // parameter and gets the same "never matches" result from the database itself for every
        // comparison operator (not just =/!=/<>) - a null value bound to >, >=, <, or <= is just
        // as much "unknown" in SQL as one bound to =. Use IS NULL/IS NOT NULL to test for
        // nullness instead.
        if (value == null) {
            return false;
        }

        return switch (operator) {
            case "=" -> Objects.equals(fieldValue, value);
            case "!=", "<>" -> !Objects.equals(fieldValue, value);
            case ">" -> compare(fieldValue, value) > 0;
            case ">=" -> compare(fieldValue, value) >= 0;
            case "<" -> compare(fieldValue, value) < 0;
            case "<=" -> compare(fieldValue, value) <= 0;
            // LIKE/NOT LIKE use SQL-style pattern matching (% = any run of characters,
            // _ = exactly one character), case-insensitive - matching PageDataEntity/
            // PageDataJPQL/PageDataSQL semantics. value is already known non-null here (guarded
            // above); a null field value never matches either LIKE or NOT LIKE (mirrors SQL's
            // "unknown" result for a NULL operand, which excludes the row from both), instead of
            // NOT LIKE treating a null field as a match.
            case "LIKE" -> fieldValue != null && matchesLikePattern(fieldValue.toString(), value.toString());
            case "NOT LIKE" -> fieldValue != null && !matchesLikePattern(fieldValue.toString(), value.toString());
            default -> throw new IllegalArgumentException("Unsupported operator: " + cond.operator());
        };
    }

    /**
     * Tests whether {@code text} matches a SQL-style {@code LIKE} pattern, where {@code %}
     * matches any run of characters (including none) and {@code _} matches exactly one
     * character. Matching is case-insensitive and anchored to the whole string (as SQL's
     * {@code LIKE} is), not a substring search.
     *
     * @param text    the value to test
     * @param pattern the SQL-style LIKE pattern (may itself contain {@code %}/{@code _})
     * @return true if {@code text} matches {@code pattern}
     */
    private boolean matchesLikePattern(String text, String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '%' -> regex.append(".*");
                case '_' -> regex.append('.');
                default -> {
                    if (REGEX_SPECIAL_CHARS.indexOf(c) >= 0) regex.append('\\');
                    regex.append(c);
                }
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text).matches();
    }

    /**
     * Checks whether any registered search field of an item matches any of the given search
     * tokens: for a plain (non-exact) token, {@code %}/{@code _} in the token act as live
     * SQL-style wildcards against the field value (via {@link #matchesLikePattern}), the same as
     * the {@code LIKE '%token%'} the DB-backed classes generate; for a quoted, exact-match token,
     * the field value must equal the token exactly, character for character, with no wildcard
     * interpretation of {@code %}/{@code _} at all.
     *
     * @param item       the item to test
     * @param tokens     the search tokens to match against
     * @param exactMatch whether tokens must match exactly rather than as a wildcard pattern
     * @return true if at least one search field matches at least one token
     */
    private boolean matchesSearch(Object item, String[] tokens, boolean exactMatch) {
        for (String field : searchFields) {
            Object fieldValue = getNestedFieldValue(item, field);
            String text = fieldValue != null ? fieldValue.toString().toLowerCase(Locale.ROOT) : "";
            for (String token : tokens) {
                boolean matches = exactMatch ? text.equals(token) : matchesLikePattern(text, "%" + token + "%");
                if (matches) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reads a (possibly dotted, nested) field's value from an object via reflection.
     *
     * @param obj       the object to read from
     * @param fieldPath the field path, e.g. {@code "car.factory.name"}
     * @return the resolved value, or {@code null} if any segment is null or cannot be read
     */
    private Object getNestedFieldValue(Object obj, String fieldPath) {
        try {
            String[] parts = fieldPath.split("\\.");
            Object current = obj;
            for (String part : parts) {
                if (current == null) return null;
                Field field = findField(current.getClass(), part);
                field.setAccessible(true);
                current = field.get(current);
            }
            return current;
        } catch (Exception e) {
            // A single unreadable field (missing, inaccessible under a SecurityManager, etc.)
            // is treated as null rather than failing the whole request - but the failure is
            // still logged (at DEBUG rather than a louder level, since this is expected to be
            // rare and a caller filtering/sorting on a genuinely missing field would otherwise
            // get no signal at all as to why matches are always empty).
            LOGGER.debug("PageDataList: could not read field '{}' via reflection", fieldPath, e);
            return null;
        }
    }

    /**
     * Compares two values, treating {@code null} as less than any non-null value.
     *
     * @param a the first value
     * @param b the second value
     * @return a negative number, zero, or a positive number as {@code a} is less than, equal
     *         to, or greater than {@code b}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return ((Comparable) a).compareTo(b);
    }

    /**
     * Assembles the standard datagrid result map.
     *
     * @param content         the current page's items
     * @param total           the total number of matching items, across all pages
     * @param appliedPage     the actual (already-clamped) 1-based page number applied to {@code content}
     * @param appliedSize     the actual (already-clamped) page size applied to {@code content}
     * @param gridParams      the grid parameters the result was computed from (used only for {@code q})
     * @param <T>             the type of item in {@code content}
     * @return a map with keys {@code total}, {@code rows}, {@code page}, {@code size}, and {@code search}
     */
    private <T> Map<String, Object> buildDataGridMap(List<T> content, long total, int appliedPage, int appliedSize,
                                                     Map<String, Object> gridParams) {
        String search = PageDataSupport.getStringParam(gridParams, "q", "").trim().toLowerCase(Locale.ROOT);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("rows", content);
        result.put("page", appliedPage);
        result.put("size", appliedSize);
        result.put("search", search);
        return result;
    }

    // ---------------- Inner Class ----------------

    /**
     * A single filter condition accumulated by {@code where}/{@code and}/{@code or}.
     *
     * @param logical     how this condition combines with the preceding one: {@code "AND"} or {@code "OR"}
     * @param field       the (possibly dotted, nested) field this condition tests
     * @param operator    the comparison operator for this condition
     * @param value       the raw value this condition compares the field against, before any transform
     * @param transformer an optional transform applied to {@code value} before comparison, or {@code null} for none
     */
    private record FilterCondition(String logical, String field, String operator, Object value,
                                   Function<Object, Object> transformer) {

        /**
         * Returns the value to compare against, applying the transformer if one was supplied.
         *
         * @return the transformed value, or the raw value if no transformer was supplied
         */
        Object getTransformedValue() {
            return transformer != null ? transformer.apply(value) : value;
        }
    }
}