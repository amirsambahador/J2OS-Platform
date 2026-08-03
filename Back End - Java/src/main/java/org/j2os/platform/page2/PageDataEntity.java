package org.j2os.platform.page2;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.*;
import java.util.function.Function;

/**
 * Builds a paginated, filterable, searchable "datagrid" result over a JPA entity, by
 * generating JPQL from the entity's simple class name plus the configured filter and search
 * conditions.
 * <p>
 * Typical usage: instantiate with an {@link EntityManager}, call
 * {@link #searchAndSortOn(String...)} and any combination of {@link #where}/{@link #and}/
 * {@link #or}, then call {@link #getResult(Class, Map)} with the entity class and the
 * incoming grid parameters ({@code page}, {@code rows}, {@code sort}, {@code order}, {@code q}).
 * <p>
 * <b>Not thread-safe.</b> Filter/search state ({@code conditions}, {@code searchFields})
 * accumulates via chained calls, so a new instance must be created for every request/query —
 * never share or reuse one instance (e.g. as a singleton Spring bean) across requests.
 * <p>
 * <b>Sortable/searchable fields are opt-in only, via {@link #searchAndSortOn(String...)}.</b>
 * A field passed only to {@link #where}/{@link #and}/{@link #or} is used solely to build the
 * generated {@code WHERE} clause and is <em>not</em> added to the sort whitelist or the
 * free-text search — this is deliberate, so a developer can safely add a server-side/security
 * filter (e.g. {@code where("organizationId", "=", currentOrgId)}) without that field becoming
 * something an end user can request via the {@code sort} or {@code q} grid parameter. If a field
 * used in a filter should also be sortable and/or searchable, register it explicitly with
 * {@link #searchAndSortOn(String...)} as well.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>{@code gridParams} must not be {@code null} and must include a {@code "sort"} key;
 *       both are mandatory, not optional — a {@code null} {@code gridParams} or a missing
 *       {@code "sort"} entry throws {@link IllegalArgumentException} rather than being
 *       defaulted.</li>
 *   <li>{@code page}/{@code rows}, if present in {@code gridParams}, must be parseable as
 *       integers; a non-numeric value throws {@link IllegalArgumentException} with a clear
 *       message (an explicit {@code null} value for any grid parameter is treated the same as
 *       the key being absent, and falls back to the default).</li>
 *   <li>Only fields registered via {@link #searchAndSortOn(String...)} can be sorted on; sorting
 *       by an unregistered field — including one that was only used in {@code where}/{@code and}/
 *       {@code or} — throws {@link RuntimeException}.</li>
 *   <li>Field names are validated against a whitelist regex, and only the fixed set of
 *       operators in {@link #ALLOWED_OPERATORS} is accepted. The base entity query's
 *       {@code FROM} clause uses the JPA entity name resolved via
 *       {@code EntityManager.getMetamodel()} — not {@link Class#getSimpleName()} — so an entity
 *       declared with an explicit, different {@code @Entity(name = ...)} (or mapped only via
 *       {@code orm.xml}) is supported correctly; {@code entityClass} must still be a type
 *       actually managed by this {@code EntityManager}'s persistence unit, or
 *       {@link #getResult(Class, Map)} throws {@link IllegalArgumentException}.</li>
 *   <li>{@code where}/{@code and}/{@code or} on a nested (dotted) path work for
 *       single-valued associations ({@code @ManyToOne}/{@code @OneToOne}) and {@code @Embedded}
 *       fields, which JPQL resolves as an implicit join/property path — but not for
 *       collection-valued associations ({@code @OneToMany}/{@code @ManyToMany}), which JPQL
 *       requires an explicit {@code JOIN} for; a dotted path through a collection (e.g.
 *       {@code "orders.id"}) will fail at query time.</li>
 *   <li>Every call to {@link #getResult(Class, Map)} issues two queries: the page of content,
 *       and a separate {@code COUNT} query for the total — there is no single-query mode.</li>
 *   <li>The free-text search generates one {@code LIKE} expression per search field per search
 *       token, OR-ed together; a query with many search fields and a multi-word search string
 *       can therefore produce a fairly large generated JPQL {@code WHERE} clause. A quoted,
 *       exact-match {@code q} token has its own {@code %}/{@code _}/{@code \} escaped and matched
 *       literally; an unquoted, plain-mode token has only its own {@code \} escaped (so it can
 *       never break or hijack the query's {@code ESCAPE '\'} clause) — its {@code %}/{@code _}
 *       still function as live {@code LIKE} wildcards, same as writing them into a raw SQL
 *       search.</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class PageDataEntity {

    /** The fixed set of comparison operators accepted by {@code where}/{@code and}/{@code or}. */
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", "<>", ">", ">=", "<", "<=", "LIKE", "NOT LIKE", "IS NULL", "IS NOT NULL");

    /** The entity manager used to build and execute the generated JPQL queries. */
    private final EntityManager entityManager;

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
     * otherwise) never produces two identical {@code LIKE} expressions (and the colliding
     * generated parameter name that would result) - insertion order is preserved for
     * deterministic generated JPQL.
     */
    private final Set<String> searchFields = new LinkedHashSet<>();

    /**
     * Creates a new instance bound to the given entity manager.
     *
     * @param entityManager the entity manager used to build and execute the generated queries
     */
    public PageDataEntity(EntityManager entityManager) {
        this.entityManager = entityManager;
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
     * @param operator the comparison operator; must be one of {@link #ALLOWED_OPERATORS}
     * @param value    the value to compare the field against, bound as a named JPQL parameter
     * @return this instance, for chaining
     */
    public PageDataEntity where(String field, String operator, Object value) {
        return where(field, operator, value, null);
    }

    /**
     * Adds a filter condition combined with any existing conditions using AND.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field       the field to filter on; validated against the field name whitelist
     * @param operator    the comparison operator; must be one of {@link #ALLOWED_OPERATORS}
     * @param value       the value to compare the field against, bound as a named JPQL parameter
     * @param transformer an optional transform applied to {@code value} before binding, or {@code null} for none
     * @return this instance, for chaining
     * @throws IllegalArgumentException if {@code field} fails the field name whitelist, or {@code operator} is unsupported
     */
    public PageDataEntity where(String field, String operator, Object value, Function<Object, Object> transformer) {
        PageDataSupport.ensureValidFieldName(field);
        ensureValidOperator(operator);
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
     * @param operator the comparison operator; must be one of {@link #ALLOWED_OPERATORS}
     * @param value    the value to compare the field against, bound as a named JPQL parameter
     * @return this instance, for chaining
     */
    public PageDataEntity and(String field, String operator, Object value) {
        return and(field, operator, value, null);
    }

    /**
     * Adds a filter condition combined with any existing conditions using AND.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field       the field to filter on; validated against the field name whitelist
     * @param operator    the comparison operator; must be one of {@link #ALLOWED_OPERATORS}
     * @param value       the value to compare the field against, bound as a named JPQL parameter
     * @param transformer an optional transform applied to {@code value} before binding, or {@code null} for none
     * @return this instance, for chaining
     * @throws IllegalArgumentException if {@code field} fails the field name whitelist, or {@code operator} is unsupported
     */
    public PageDataEntity and(String field, String operator, Object value, Function<Object, Object> transformer) {
        PageDataSupport.ensureValidFieldName(field);
        ensureValidOperator(operator);
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
     * @param operator the comparison operator; must be one of {@link #ALLOWED_OPERATORS}
     * @param value    the value to compare the field against, bound as a named JPQL parameter
     * @return this instance, for chaining
     */
    public PageDataEntity or(String field, String operator, Object value) {
        return or(field, operator, value, null);
    }

    /**
     * Adds a filter condition combined with the other conditions using OR when the generated
     * JPQL is assembled.
     * <p>
     * This does <b>not</b> make {@code field} sortable or searchable — see the class-level
     * javadoc. Call {@link #searchAndSortOn(String...)} as well if that is also desired.
     *
     * @param field       the field to filter on; validated against the field name whitelist
     * @param operator    the comparison operator; must be one of {@link #ALLOWED_OPERATORS}
     * @param value       the value to compare the field against, bound as a named JPQL parameter
     * @param transformer an optional transform applied to {@code value} before binding, or {@code null} for none
     * @return this instance, for chaining
     * @throws IllegalArgumentException if {@code field} fails the field name whitelist, or {@code operator} is unsupported
     */
    public PageDataEntity or(String field, String operator, Object value, Function<Object, Object> transformer) {
        PageDataSupport.ensureValidFieldName(field);
        ensureValidOperator(operator);
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
    public PageDataEntity searchAndSortOn(String... fields) {
        for (String f : fields) {
            PageDataSupport.ensureValidFieldName(f);
            searchFields.add(f);
        }
        return this;
    }

    // ---------------- Core Get Result ----------------

    /**
     * Generates and runs a JPQL query (plus a matching {@code COUNT} query) for the given
     * entity class, according to the conditions and search fields configured on this instance
     * and the given grid parameters.
     *
     * @param entityClass a type managed as a JPA entity by this instance's {@code EntityManager};
     *                    its JPA entity name (resolved via {@code EntityManager.getMetamodel()},
     *                    honoring an explicit {@code @Entity(name = ...)} if present) is used as
     *                    the JPQL entity name
     * @param gridParams  the incoming grid parameters; must not be {@code null} and must include
     *                    {@code "sort"}, and may include {@code "order"} ({@code "ASC"}/{@code "DESC"}),
     *                    {@code "page"}, {@code "rows"}, and {@code "q"} (free-text search)
     * @param <T>         the entity type
     * @return a map with keys {@code total}, {@code rows}, {@code page}, {@code size}, and {@code search}
     * @throws IllegalArgumentException if {@code gridParams} is {@code null}, has no {@code "sort"}
     *                                   entry, if {@code "page"}/{@code "rows"} is present but not
     *                                   a valid integer, or if {@code entityClass} is not a type
     *                                   managed by this {@code EntityManager}'s persistence unit
     * @throws RuntimeException          if the requested sort field was not registered via
     *                                   {@link #searchAndSortOn(String...)}
     */
    public <T> Map<String, Object> getResult(Class<T> entityClass, Map<String, Object> gridParams) {
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

        SearchContext searchContext = buildSearchContext(gridParams);

        String jpql = buildJPQL(entityClass, gridParams, searchContext, false);
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        setQueryParameters(query, searchContext);
        int appliedSize = applyPaging(query, gridParams);

        List<T> content = query.getResultList();

        String countJpql = buildJPQL(entityClass, gridParams, searchContext, true);
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        setQueryParameters(countQuery, searchContext);
        long total = countQuery.getSingleResult();

        int appliedPage = PageDataSupport.parseIntParam(gridParams, "page", 1);
        if (appliedPage < 1) appliedPage = 1;

        return buildDataGridMap(content, total, appliedPage, appliedSize, gridParams);
    }

    // ---------------- Internal Helpers ----------------

    /**
     * Builds the free-text search expressions and their bound parameters from the incoming
     * {@code q} grid parameter, if present.
     *
     * @param gridParams the incoming grid parameters
     * @return the resulting search context, or an empty one if there is no search to apply
     */
    private SearchContext buildSearchContext(Map<String, Object> gridParams) {
        if (searchFields.isEmpty() || !gridParams.containsKey("q")) {
            return SearchContext.empty();
        }
        String searchValue = PageDataSupport.getStringParam(gridParams, "q", "").toLowerCase(Locale.ROOT).trim();
        if (searchValue.isEmpty()) {
            return SearchContext.empty();
        }

        boolean exactMatch = PageDataSupport.EXACT_MATCH_PATTERN.matcher(searchValue).matches();
        String[] tokens = exactMatch
                ? Arrays.stream(searchValue.split("\"")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new)
                : searchValue.replace("\"", "").trim().split("\\s+");

        List<String> expressions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        // A running, globally-unique counter (rather than a name derived from the field path)
        // guarantees distinct parameter names even when two registered fields would otherwise
        // collide after sanitization (e.g. "user.name" and "user_name" both becoming
        // "user_name") - the field's dots are still stripped for readability, but uniqueness no
        // longer depends on the field names themselves.
        int paramCounter = 0;
        for (String field : searchFields) {
            for (int i = 0; i < tokens.length; i++) {
                String paramName = "search_" + field.replace(".", "_") + "_" + paramCounter++;
                // COALESCE guards against providers where CONCAT(null, '') itself yields null
                // rather than an empty string, which would otherwise make LOWER(...) throw or
                // the LIKE silently never match a null field. ESCAPE '\' is declared
                // unconditionally, so it applies to the whole pattern regardless of match mode -
                // an exact-match token has its own \/%/_ fully escaped by escapeLikeToken(), and
                // a plain-mode token has its own \ escaped by escapeLikeBackslashOnly() (so a
                // user-typed backslash, e.g. searching a Windows path, can never leave a dangling
                // or misinterpreted escape character in the generated pattern) while its %/_
                // still act as live wildcards.
                expressions.add("LOWER(COALESCE(CONCAT(o." + field + ", ''), '')) LIKE :" + paramName + " ESCAPE '\\'");
                params.put(paramName, exactMatch
                        ? PageDataSupport.escapeLikeToken(tokens[i])
                        : "%" + PageDataSupport.escapeLikeBackslashOnly(tokens[i]) + "%");
            }
        }
        return new SearchContext(expressions, params);
    }

    /**
     * Assembles the JPQL for either the content query or the count query, combining the base
     * entity selection with the configured filter conditions, search expressions, and (for the
     * content query only) sort clause.
     *
     * @param entityClass    the JPA entity class being queried
     * @param gridParams     the incoming grid parameters
     * @param searchContext  the search expressions/parameters built by {@link #buildSearchContext}
     * @param isCount        true to build a {@code SELECT COUNT(o)} query instead of {@code SELECT o}
     * @return the assembled JPQL string
     */
    private String buildJPQL(Class<?> entityClass, Map<String, Object> gridParams, SearchContext searchContext, boolean isCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(isCount ? "SELECT COUNT(o) " : "SELECT o ");
        sb.append("FROM ").append(resolveEntityName(entityClass)).append(" o WHERE 1=1 ");

        if (!conditions.isEmpty()) {
            sb.append(" AND (");
            boolean first = true;
            for (FilterCondition cond : conditions) {
                if (!first) {
                    sb.append(" ").append(cond.getLogical()).append(" ");
                }
                sb.append("o.").append(cond.getField()).append(" ").append(cond.getOperator());
                if (!cond.isNullCheck()) {
                    sb.append(" :").append(cond.getParamName());
                }
                first = false;
            }
            sb.append(")");
        }

        if (!searchContext.expressions().isEmpty()) {
            sb.append(" AND (").append(String.join(" OR ", searchContext.expressions())).append(")");
        }

        if (!isCount && gridParams.containsKey("sort")) {
            String sortField = gridParams.get("sort").toString();
            String order = "DESC".equalsIgnoreCase(PageDataSupport.getStringParam(gridParams, "order", "ASC")) ? "DESC" : "ASC";
            // sortField was already validated in getResult() to be a registered searchAndSortOn
            // field, so it is always safe to sort on here.
            sb.append(" ORDER BY o.").append(sortField).append(" ").append(order);
        }

        return sb.toString();
    }

    /**
     * Resolves the JPA entity name for the {@code FROM} clause of the generated JPQL — the name
     * JPA itself uses to refer to the entity, not necessarily {@link Class#getSimpleName()}.
     * These differ whenever the entity is mapped with an explicit {@code @Entity(name = ...)}
     * (or an equivalent {@code orm.xml} mapping), so this is resolved through
     * {@code EntityManager.getMetamodel()} — the same source of truth the persistence provider
     * itself uses — rather than assumed to equal the Java class name.
     *
     * @param entityClass the JPA entity class being queried
     * @return the JPA entity name for {@code entityClass}
     * @throws IllegalArgumentException if {@code entityClass} is not a type managed by this
     *                                   {@code EntityManager}'s persistence unit
     */
    private String resolveEntityName(Class<?> entityClass) {
        try {
            return entityManager.getMetamodel().entity(entityClass).getName();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "PageDataEntity: '" + entityClass.getName() + "' is not a JPA entity managed "
                            + "by this EntityManager's persistence unit", e);
        }
    }

    /**
     * Binds every configured filter condition's value, plus every search parameter, onto a query.
     *
     * @param query         the query to bind parameters onto
     * @param searchContext the search parameters built by {@link #buildSearchContext}
     */
    private void setQueryParameters(TypedQuery<?> query, SearchContext searchContext) {
        for (FilterCondition cond : conditions) {
            if (cond.isNullCheck()) continue;
            query.setParameter(cond.getParamName(), cond.getTransformedValue());
        }
        searchContext.params().forEach(query::setParameter);
    }

    /**
     * Applies the {@code page}/{@code rows} grid parameters to a query as first-result/max-results.
     *
     * @param query      the query to apply paging to
     * @param gridParams the incoming grid parameters
     * @return the actual page size applied (after clamping an invalid/missing value to the default)
     */
    private int applyPaging(TypedQuery<?> query, Map<String, Object> gridParams) {
        int page = PageDataSupport.parseIntParam(gridParams, "page", 1) - 1;
        int size = PageDataSupport.parseIntParam(gridParams, "rows", 10);
        if (page < 0) page = 0;
        if (size < 1) size = 10;

        // JPA's Query.setFirstResult(int) only accepts an int, so a huge page*size can overflow;
        // compute in long and clamp to Integer.MAX_VALUE rather than silently wrapping to a
        // small/negative offset.
        long rawOffset = (long) page * (long) size;
        int offset = rawOffset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawOffset;

        query.setFirstResult(offset);
        query.setMaxResults(size);
        return size;
    }

    /**
     * Assembles the standard datagrid result map.
     *
     * @param content     the current page's items
     * @param total       the total number of matching items, across all pages
     * @param appliedPage the actual (already-clamped) 1-based page number applied to {@code content}
     * @param appliedSize the actual (already-clamped) page size applied to {@code content}
     * @param gridParams  the grid parameters the result was computed from (used only for {@code q})
     * @param <T>         the entity type
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

    /**
     * Validates an operator against the fixed set of supported operators.
     *
     * @param operator the operator to validate
     * @throws IllegalArgumentException if {@code operator} is null or not one of {@link #ALLOWED_OPERATORS}
     */
    private void ensureValidOperator(String operator) {
        if (operator == null || !ALLOWED_OPERATORS.contains(operator.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
    }

    // ---------------- Inner Types ----------------

    /** The free-text search expressions and their bound parameters for one {@link #getResult} call. */
    private record SearchContext(List<String> expressions, Map<String, Object> params) {
        /**
         * Returns an empty search context (no expressions, no parameters), used when there is
         * no free-text search to apply.
         *
         * @return an empty search context
         */
        static SearchContext empty() {
            return new SearchContext(List.of(), Map.of());
        }
    }

    /** A single filter condition accumulated by {@code where}/{@code and}/{@code or}. */
    private static class FilterCondition {

        /** How this condition combines with the preceding one: {@code "AND"} or {@code "OR"}. */
        private final String logical;

        /** The (possibly dotted, nested) field this condition tests. */
        private final String field;

        /** The comparison operator for this condition. */
        private final String operator;

        /** The raw value this condition compares the field against, before any transform. */
        private final Object value;

        /** An optional transform applied to {@link #value} before binding. */
        private final Function<Object, Object> transformer;

        /** The generated, unique JPQL bind-parameter name for this condition. */
        private final String paramName;

        /**
         * Creates a filter condition with a freshly generated, unique bind-parameter name.
         *
         * @param logical     how this condition combines with the preceding one ({@code "AND"} or {@code "OR"})
         * @param field       the field this condition tests
         * @param operator    the comparison operator
         * @param value       the raw value to compare against
         * @param transformer an optional transform applied to {@code value} before binding, or {@code null} for none
         */
        FilterCondition(String logical, String field, String operator, Object value, Function<Object, Object> transformer) {
            this.logical = logical;
            this.field = field;
            this.operator = operator;
            this.value = value;
            this.transformer = transformer;
            this.paramName = "p_" + UUID.randomUUID().toString().replace("-", "");
        }

        /**
         * Returns how this condition combines with the preceding one.
         *
         * @return {@code "AND"} or {@code "OR"}
         */
        String getLogical() {
            return logical;
        }

        /**
         * Returns the field this condition tests.
         *
         * @return the field name
         */
        String getField() {
            return field;
        }

        /**
         * Returns the comparison operator for this condition.
         *
         * @return the operator
         */
        String getOperator() {
            return operator;
        }

        /**
         * Checks whether this condition is a null-check operator ({@code IS NULL}/{@code IS NOT NULL}),
         * which takes no bound value.
         *
         * @return true if this condition is a null-check
         */
        boolean isNullCheck() {
            return operator.equalsIgnoreCase("IS NULL") || operator.equalsIgnoreCase("IS NOT NULL");
        }

        /**
         * Returns the value to bind, applying the transformer if one was supplied.
         *
         * @return the transformed value, or the raw value if no transformer was supplied
         */
        Object getTransformedValue() {
            return transformer != null ? transformer.apply(value) : value;
        }

        /**
         * Returns the generated JPQL bind-parameter name for this condition.
         *
         * @return the parameter name
         */
        String getParamName() {
            return paramName;
        }
    }
}
