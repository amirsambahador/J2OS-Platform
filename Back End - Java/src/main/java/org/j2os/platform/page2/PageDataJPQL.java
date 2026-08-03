package org.j2os.platform.page2;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Builds a paginated, filterable, searchable "datagrid" result on top of a caller-supplied
 * JPQL query, by splicing in a free-text search predicate and an {@code ORDER BY} clause.
 * <p>
 * Typical usage: instantiate with an {@link EntityManager}, call
 * {@link #searchAndSortOn(String...)}, then call
 * {@link #getResult(String, String, String, Class, Map, Map)} with the caller-authored JPQL
 * (content and count variants), the entity alias used in that JPQL, the entity class, any
 * base query parameters, and the incoming grid parameters ({@code page}, {@code rows},
 * {@code sort}, {@code order}, {@code q}).
 * <p>
 * <b>Not thread-safe.</b> Search/sort state ({@code searchFields}) accumulates via chained
 * calls, so a new instance must be created for every request/query — never share or reuse one
 * instance (e.g. as a singleton Spring bean) across requests.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>{@code gridParams} must include a {@code "sort"} key; it is mandatory, not optional,
 *       and a missing key throws {@link IllegalArgumentException} rather than being defaulted.</li>
 *   <li>{@code page}/{@code rows}, if present in {@code gridParams}, must be parseable as
 *       integers; a non-numeric value throws {@link IllegalArgumentException} with a clear
 *       message (an explicit {@code null} value for any grid parameter is treated the same as
 *       the key being absent, and falls back to the default).</li>
 *   <li>Only fields previously registered via {@link #searchAndSortOn(String...)} can be sorted
 *       on; sorting by an unregistered field throws {@link RuntimeException}.</li>
 *   <li>The caller-supplied {@code jpql}/{@code countJpql} strings are trusted, developer-authored
 *       input — unlike field names (which are validated against a whitelist), the base query
 *       text itself is never parsed or sanitized, so it must never be built from raw end-user
 *       input.</li>
 *   <li>By team convention, the caller-supplied {@code jpql}/{@code countJpql} should not include
 *       its own {@code ORDER BY} — sort always comes from {@code gridParams}, which this class
 *       appends itself. If a caller's query does include one, the search predicate is spliced in
 *       before the earliest top-level (i.e. not nested inside a subquery's own parentheses) of
 *       any {@code GROUP BY}/{@code ORDER BY}/{@code HAVING} clause, so the generated JPQL stays
 *       syntactically valid, but the caller's own {@code ORDER BY} would then precede the one
 *       this class appends. A caller query containing its own subquery with a
 *       {@code GROUP BY}/{@code ORDER BY}/{@code HAVING} (e.g. inside a {@code WHERE ... IN (...)})
 *       is unaffected — the predicate is only ever spliced in at the top level of the query, never
 *       inside a parenthesized subquery.</li>
 *   <li>{@code queryParams} must not use any of this class's internally reserved parameter names
 *       ({@code search_0}, {@code search_1}, ...) — doing so throws {@link IllegalArgumentException}.</li>
 *   <li>Every call to {@link #getResult} issues two queries: the page of content, and a separate
 *       {@code COUNT} query for the total — there is no single-query mode.</li>
 *   <li>A quoted, exact-match {@code q} token has its own {@code %}/{@code _}/{@code \} escaped
 *       and matched literally; an unquoted, plain-mode token has only its own {@code \} escaped
 *       (so it can never break or hijack the query's {@code ESCAPE '\'} clause) — its
 *       {@code %}/{@code _} still function as live {@code LIKE} wildcards, same as writing them
 *       into a raw SQL search.</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class PageDataJPQL {

    /** Matches a {@code GROUP BY} keyword, used to find where to splice in the search predicate. */
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    /** Matches an {@code ORDER BY} keyword, used to find where to splice in the search predicate. */
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    /** Matches a {@code HAVING} keyword, used to find where to splice in the search predicate. */
    private static final Pattern HAVING_PATTERN = Pattern.compile("\\bHAVING\\b", Pattern.CASE_INSENSITIVE);

    /** Matches a {@code WHERE} keyword, used to decide whether to splice the search predicate in with {@code AND} or {@code WHERE}. */
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);

    /** The entity manager used to execute the supplied JPQL queries. */
    private final EntityManager entityManager;

    /**
     * Fields the free-text search ({@code q}) is matched against; also the only fields eligible
     * as a sort field (registered exclusively via {@link #searchAndSortOn(String...)} - unlike
     * {@link PageDataEntity}/{@link PageDataList}, this class has no separate {@code where}/
     * {@code and}/{@code or} that would register additional non-searchable fields, so a single
     * set covers both roles).
     * A {@link LinkedHashSet} so that registering the same field twice (accidentally or
     * otherwise) never produces a duplicate search expression - insertion order is preserved
     * for deterministic generated JPQL.
     */
    private final Set<String> searchFields = new LinkedHashSet<>();

    /**
     * Creates a new instance bound to the given entity manager.
     *
     * @param entityManager the entity manager used to execute the supplied JPQL queries
     */
    public PageDataJPQL(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ---------------- Search Fields ----------------

    /**
     * Registers which fields the free-text search ({@code q} in {@code gridParams}) is matched
     * against, and makes them eligible as a sort field. Registering the same field more than
     * once (in one call or across calls) has no additional effect beyond the first registration.
     *
     * @param fields the field names to register; each validated against the field name whitelist
     * @return this instance, for chaining
     * @throws IllegalArgumentException if any field is null, empty, or fails the field name whitelist
     */
    public PageDataJPQL searchAndSortOn(String... fields) {
        for (String f : fields) {
            PageDataSupport.ensureValidFieldName(f);
            searchFields.add(f);
        }
        return this;
    }

    /**
     * Runs the given content and count JPQL queries, augmented with a free-text search
     * predicate and sort/paging derived from {@code gridParams}.
     *
     * @param jpql                    the caller-authored content query, e.g. {@code "select o from Wiki o"}
     * @param countJpql               the caller-authored count query, e.g. {@code "select count(o) from Wiki o"}
     * @param entityAliasNameInQuery  the entity alias used in {@code jpql}/{@code countJpql}, e.g. {@code "o"}
     * @param entityClass             the entity type the content query returns
     * @param queryParams             base parameters referenced by {@code jpql}/{@code countJpql}, or {@code null} for none;
     *                                must not use any internally reserved {@code search_N} name
     * @param gridParams              the incoming grid parameters; must include {@code "sort"}, and may
     *                                include {@code "order"} ({@code "ASC"}/{@code "DESC"}), {@code "page"},
     *                                {@code "rows"}, and {@code "q"} (free-text search); {@code null} is treated as empty
     * @param <T>                     the entity type
     * @return a map with keys {@code total}, {@code rows}, {@code page}, {@code size}, and {@code search}
     * @throws NullPointerException     if {@code jpql} or {@code countJpql} is null
     * @throws IllegalArgumentException if {@code gridParams} has no {@code "sort"} entry, if
     *                                   {@code "page"}/{@code "rows"} is present but not a valid integer, or if
     *                                   {@code queryParams} uses a reserved {@code search_N} parameter name
     * @throws RuntimeException          if the requested sort field was not registered via {@link #searchAndSortOn(String...)}
     */
    public <T> Map<String, Object> getResult(
            String jpql,
            String countJpql,
            String entityAliasNameInQuery,
            Class<T> entityClass,
            Map<String, Object> queryParams,
            Map<String, Object> gridParams
    ) {
        Objects.requireNonNull(jpql, "jpql must not be null");
        Objects.requireNonNull(countJpql, "countJpql must not be null");

        Map<String, Object> baseParams = queryParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(queryParams);
        gridParams = gridParams == null ? new HashMap<>() : gridParams;

        Object sortValue = gridParams.get("sort");
        if (sortValue == null) {
            throw new IllegalArgumentException("gridParams must include a 'sort' field (sort is mandatory)");
        }
        String sortField = sortValue.toString();
        if (!searchFields.contains(sortField)) {
            throw new RuntimeException("%s is not sortable".formatted(sortField));
        }

        int page = PageDataSupport.parseIntParam(gridParams, "page", 1);
        int size = PageDataSupport.parseIntParam(gridParams, "rows", 10);
        String sortOrder = PageDataSupport.getStringParam(gridParams, "order", "ASC");
        String search = PageDataSupport.getStringParam(gridParams, "q", "").trim().toLowerCase(Locale.ROOT);

        // ------------------- Search -------------------
        String searchPredicate = null;
        if (!search.isEmpty() && !searchFields.isEmpty()) {
            boolean exactMatch = PageDataSupport.EXACT_MATCH_PATTERN.matcher(search).matches();
            String[] tokens = exactMatch
                    ? Arrays.stream(search.split("\"")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new)
                    : search.replace("\"", "").trim().split("\\s+");

            List<String> exprs = new ArrayList<>();
            int paramCounter = 0;
            for (String field : searchFields) {
                for (String token : tokens) {
                    String paramName = "search_" + paramCounter++;
                    requireNoReservedNameCollision(baseParams, paramName);
                    // COALESCE guards against providers where CONCAT(null, '') itself yields
                    // null rather than an empty string. ESCAPE '\' is declared unconditionally,
                    // so it applies to the whole pattern regardless of match mode - an
                    // exact-match token has its own \/%/_ fully escaped by escapeLikeToken(), and
                    // a plain-mode token has its own \ escaped by escapeLikeBackslashOnly() (so a
                    // user-typed backslash can never leave a dangling or misinterpreted escape
                    // character in the generated pattern) while its %/_ still act as live
                    // wildcards.
                    exprs.add("LOWER(COALESCE(CONCAT(" + entityAliasNameInQuery + "." + field + ", ''), '')) LIKE :" + paramName + " ESCAPE '\\'");
                    baseParams.put(paramName, exactMatch
                            ? PageDataSupport.escapeLikeToken(token)
                            : "%" + PageDataSupport.escapeLikeBackslashOnly(token) + "%");
                }
            }
            searchPredicate = String.join(" OR ", exprs);
        }

        String contentJpql = appendSearchClause(jpql, searchPredicate);
        String countFinalJpql = appendSearchClause(countJpql, searchPredicate);

        // sortField was already validated above to be a registered search field, so it is
        // always safe to sort on here.
        sortOrder = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        contentJpql += " ORDER BY " + entityAliasNameInQuery + "." + sortField + " " + sortOrder;

        // ------------------- Execute content query -------------------
        TypedQuery<T> query = entityManager.createQuery(contentJpql, entityClass);
        baseParams.forEach(query::setParameter);

        int pageIndex = Math.max(page - 1, 0);
        size = Math.max(size, 1);

        // JPA's Query.setFirstResult(int) only accepts an int, so a huge pageIndex*size can
        // overflow; compute in long and clamp to Integer.MAX_VALUE rather than silently
        // wrapping to a small/negative offset.
        long rawOffset = (long) pageIndex * (long) size;
        int offset = rawOffset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawOffset;
        query.setFirstResult(offset);
        query.setMaxResults(size);

        List<T> content = query.getResultList();

        // ------------------- Execute count query -------------------
        TypedQuery<Long> countQuery = entityManager.createQuery(countFinalJpql, Long.class);
        baseParams.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        // ------------------- Map Result -------------------
        // Report the actual (clamped) page/size that was applied, not the raw caller-supplied
        // value, so the response metadata always matches what was actually returned.
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("rows", content);
        result.put("page", pageIndex + 1);
        result.put("size", size);
        result.put("search", search);
        return result;
    }

    /**
     * Splices a search predicate into a JPQL string, inserting it before the earliest top-level
     * (i.e. not nested inside a subquery's own parentheses) occurrence of any
     * {@code GROUP BY}/{@code HAVING}/{@code ORDER BY} clause (or at the end, if none are
     * present at the top level), and combining it with {@code AND} if the query already has a
     * top-level {@code WHERE} clause or with a new {@code WHERE} otherwise. The predicate is an
     * OR-joined list of expressions, so it is additionally wrapped in its own parens when
     * spliced in (unlike {@link PageDataSQL}, whose caller-facing predicate string already comes
     * pre-wrapped) — the actual splicing logic itself is shared with {@link PageDataSQL} via
     * {@link PageDataSupport#spliceTopLevelPredicate}.
     *
     * @param jpql      the JPQL to splice the predicate into
     * @param predicate the search predicate to splice in, or {@code null} to leave {@code jpql} unchanged
     * @return the resulting JPQL, or the original {@code jpql} unchanged if {@code predicate} is null
     */
    private String appendSearchClause(String jpql, String predicate) {
        return PageDataSupport.spliceTopLevelPredicate(
                jpql, predicate, true, WHERE_PATTERN, GROUP_BY_PATTERN, HAVING_PATTERN, ORDER_BY_PATTERN);
    }

    /**
     * Guards against a caller-supplied parameter map using one of this class's internally
     * reserved parameter names.
     *
     * @param params       the caller-supplied parameter map to check
     * @param reservedName the reserved name that must not already be present
     * @throws IllegalArgumentException if {@code params} already contains {@code reservedName}
     */
    private void requireNoReservedNameCollision(Map<String, Object> params, String reservedName) {
        if (params.containsKey(reservedName)) {
            throw new IllegalArgumentException(
                    "queryParams must not contain the reserved parameter name '" + reservedName
                            + "' (used internally by PageDataJPQL for search)");
        }
    }
}
