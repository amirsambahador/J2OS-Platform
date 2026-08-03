package org.j2os.platform.page2;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a paginated, filterable, searchable "datagrid" result on top of a caller-supplied
 * raw SQL query, by splicing in a free-text search predicate, sort, and paging, and binding
 * named ({@code :name}) parameters positionally via JDBC.
 * <p>
 * Typical usage: instantiate with a {@link Connection}, call
 * {@link #searchAndSortOn(String...)}, then call {@link #getResult(String, Map, Map)} with
 * the caller-authored SQL, any base query parameters, and the incoming grid parameters
 * ({@code page}, {@code rows}, {@code sort}, {@code order}, {@code q}).
 * <p>
 * <b>Not thread-safe.</b> Search/sort state ({@code searchFields}) accumulates via chained
 * calls, so a new instance must be created for every request/query — never share or reuse one
 * instance (e.g. as a singleton Spring bean) across requests.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>{@code pageParams} must include a {@code "sort"} key; it is mandatory, not optional,
 *       and a missing key throws {@link IllegalArgumentException} rather than being defaulted.</li>
 *   <li>{@code page}/{@code rows}, if present in {@code pageParams}, must be parseable as
 *       integers; a non-numeric value throws {@link IllegalArgumentException} with a clear
 *       message (an explicit {@code null} value for any grid parameter is treated the same as
 *       the key being absent, and falls back to the default).</li>
 *   <li>Only fields previously registered via {@link #searchAndSortOn(String...)} can be sorted
 *       on; sorting by an unregistered field throws {@link RuntimeException}.</li>
 *   <li>The caller-supplied {@code sql} string is trusted, developer-authored input — unlike
 *       field names (which are validated against a whitelist), the base query text itself is
 *       never parsed or sanitized, so it must never be built from raw end-user input.</li>
 *   <li>By team convention, the caller-supplied {@code sql} should not include its own
 *       {@code ORDER BY} — sort always comes from {@code pageParams}, which this class appends
 *       itself. The search predicate is spliced in before the earliest top-level (i.e. not
 *       nested inside a subquery's own parentheses) of any {@code GROUP BY}/{@code HAVING}/
 *       {@code ORDER BY} clause, so the generated SQL stays syntactically valid even if the
 *       caller's query does include one, and even if the caller's query itself contains a
 *       subquery (e.g. inside a derived table or a {@code WHERE ... IN (...)}) that has its own
 *       nested {@code GROUP BY}/{@code ORDER BY}/{@code HAVING} — the predicate is only ever
 *       spliced in at the top level of the query, never inside a parenthesized subquery.</li>
 *   <li>{@code sqlParams} must not use any of this class's internally reserved parameter names
 *       ({@code search_0}, {@code search_1}, ..., {@code __limit}, {@code __offset}) — doing so
 *       throws {@link IllegalArgumentException}.</li>
 *   <li>Named-parameter detection skips occurrences inside single-quoted string literals
 *       (standard {@code ''} escaping is handled), and a {@code (?<!:):name} negative lookbehind
 *       keeps dialect-specific {@code ::type} casts (e.g. PostgreSQL) from being misparsed as
 *       named parameters — but any other quoting style (e.g. double-quoted identifiers containing
 *       a colon), and any occurrence inside a SQL comment ({@code --} or {@code /* *&#47;}), is
 *       not specially handled and can still be misparsed as a named parameter.</li>
 *   <li>Paging is appended as a literal {@code LIMIT :__limit OFFSET :__offset} clause, which
 *       assumes the target database supports standard {@code LIMIT}/{@code OFFSET} syntax
 *       (true for H2, PostgreSQL, MySQL; not for every SQL dialect).</li>
 *   <li>The total count is computed by wrapping the (search-filtered) query as a derived
 *       table — {@code SELECT COUNT(*) FROM (...) AS dd_count(dd_col_1, dd_col_2, ...)} — which
 *       requires the target database to support subqueries in the {@code FROM} clause, and
 *       issues a second full query per page fetch. The derived table is given an explicit,
 *       internally generated column alias list (sized to the content query's own column count)
 *       rather than inheriting the caller's {@code SELECT} list's own column names/labels — see
 *       the next item for why, and the dialect-support caveat that comes with it.</li>
 *   <li>Result rows are keyed by JDBC column label ({@link ResultSetMetaData#getColumnLabel}). If
 *       the caller's SQL selects two or more columns that end up with the same label (whether
 *       from an unaliased join on tables that both have an {@code id} column, or from the
 *       caller's own alias colliding with a generated disambiguated label), every column still
 *       gets a distinct key in the result row: colliding labels are disambiguated by appending
 *       {@code _2}, {@code _3}, etc. in column order, checked against every label already
 *       assigned so far (not just the first occurrence of that exact label) — so one column's
 *       value can never silently overwrite another's. This works end-to-end, including for the
 *       count query, precisely because that query never re-derives labels from the caller's
 *       {@code SELECT} list at all (see the previous item) — a derived table whose column list
 *       inherits a duplicate name is rejected outright by most databases (including H2), even
 *       when that name is never referenced, which the explicit alias list avoids entirely. That
 *       alias-list derived-table syntax itself is supported by H2 and PostgreSQL, and by MySQL
 *       from 8.0.19 onward — an older MySQL target would need this adapted to a dialect it
 *       supports.</li>
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
public class PageDataSQL {

    /** Matches a {@code :name}-style named parameter, but not a {@code ::} type-cast (negative lookbehind for a preceding colon). */
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");

    /** Matches a {@code WHERE} keyword, used to decide whether to splice the search predicate in with {@code AND} or {@code WHERE}. */
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);

    /** Matches a {@code GROUP BY} keyword, used to find where to splice in the search predicate. */
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile("\\bGROUP\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    /** Matches an {@code ORDER BY} keyword, used to find where to splice in the search predicate. */
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    /** Matches a {@code HAVING} keyword, used to find where to splice in the search predicate. */
    private static final Pattern HAVING_PATTERN = Pattern.compile("\\bHAVING\\b", Pattern.CASE_INSENSITIVE);

    /** The JDBC connection used to execute the supplied SQL queries. */
    private final Connection connection;

    /**
     * Fields the free-text search ({@code q}) is matched against; also the only fields eligible
     * as a sort field (registered exclusively via {@link #searchAndSortOn(String...)}).
     * A {@link LinkedHashSet} so that registering the same field twice (accidentally or
     * otherwise) never produces a duplicate search expression - insertion order is preserved
     * for deterministic generated SQL.
     */
    private final Set<String> searchFields = new LinkedHashSet<>();

    /**
     * Creates a new instance bound to the given connection.
     *
     * @param connection the JDBC connection used to execute the supplied SQL queries
     * @throws NullPointerException if {@code connection} is null
     */
    public PageDataSQL(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
    }

    // ---------------- Search Fields ----------------

    /**
     * Registers which fields the free-text search ({@code q} in {@code pageParams}) is matched
     * against, and makes them eligible as a sort field. Registering the same field more than
     * once (in one call or across calls) has no additional effect beyond the first registration.
     *
     * @param fields the field names to register; each validated against the field name whitelist
     * @return this instance, for chaining
     * @throws IllegalArgumentException if any field is null, empty, or fails the field name whitelist
     */
    public PageDataSQL searchAndSortOn(String... fields) {
        for (String f : fields) {
            PageDataSupport.ensureValidFieldName(f);
            searchFields.add(f);
        }
        return this;
    }

    // ---------------- Core Get Result ----------------

    /**
     * Runs the given SQL query, augmented with a free-text search predicate, sort, and paging
     * derived from {@code pageParams}.
     *
     * @param sql        the caller-authored SQL query, e.g. {@code "SELECT NAME, FAMILY FROM PERSON"}
     * @param sqlParams  base named parameters referenced by {@code sql}, or {@code null} for none;
     *                   must not use any internally reserved name
     * @param pageParams the incoming grid parameters; must include {@code "sort"}, and may
     *                   include {@code "order"} ({@code "ASC"}/{@code "DESC"}), {@code "page"},
     *                   {@code "rows"}, and {@code "q"} (free-text search); {@code null} is treated as empty
     * @return a map with keys {@code total}, {@code rows}, {@code page}, {@code size}, and {@code search}
     * @throws IllegalArgumentException if {@code pageParams} has no {@code "sort"} entry, if
     *                                   {@code "page"}/{@code "rows"} is present but not a valid integer, or if
     *                                   {@code sqlParams} uses a reserved parameter name
     * @throws RuntimeException          if the requested sort field was not registered via
     *                                   {@link #searchAndSortOn(String...)}, or if query execution fails
     */
    public Map<String, Object> getResult(
            String sql,
            Map<String, Object> sqlParams,
            Map<String, Object> pageParams
    ) {
        try {
            return doGetResult(sql, sqlParams, pageParams);
        } catch (SQLException e) {
            throw new RuntimeException("PageDataSQL: query execution failed", e);
        }
    }

    /**
     * Implements {@link #getResult}, allowed to throw the checked {@link SQLException} directly.
     *
     * @param sql        the caller-authored SQL query
     * @param sqlParams  base named parameters referenced by {@code sql}, or {@code null} for none
     * @param gridParams the incoming grid parameters, or {@code null} to treat as empty
     * @return a map with keys {@code total}, {@code rows}, {@code page}, {@code size}, and {@code search}
     * @throws SQLException if executing either query fails
     */
    private Map<String, Object> doGetResult(
            String sql,
            Map<String, Object> sqlParams,
            Map<String, Object> gridParams
    ) throws SQLException {

        Objects.requireNonNull(sql, "sql must not be null");
        Map<String, Object> baseParams = sqlParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sqlParams);
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

        int pageIndex = Math.max(page - 1, 0);
        size = Math.max(size, 1);

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
                    // COALESCE guards against a null field producing a null CAST result, which
                    // would otherwise make the field never match the search regardless of intent.
                    // ESCAPE '\' is declared unconditionally, so it applies to the whole pattern
                    // regardless of match mode - an exact-match token has its own \/%/_ fully
                    // escaped by escapeLikeToken(), and a plain-mode token has its own \ escaped
                    // by escapeLikeBackslashOnly() (so a user-typed backslash, e.g. searching a
                    // Windows path, can never leave a dangling or misinterpreted escape character
                    // in the generated SQL) while its %/_ still act as live wildcards.
                    exprs.add("LOWER(COALESCE(CAST(" + field + " AS VARCHAR), '')) LIKE :" + paramName + " ESCAPE '\\'");
                    baseParams.put(paramName, exactMatch
                            ? PageDataSupport.escapeLikeToken(token)
                            : "%" + PageDataSupport.escapeLikeBackslashOnly(token) + "%");
                }
            }
            searchPredicate = "(" + String.join(" OR ", exprs) + ")";
        }

        String filteredSql = appendPredicate(sql, searchPredicate);

        // sortField was already validated above to be a registered search field, so it is
        // always safe to sort on here.
        sortOrder = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        String contentSql = filteredSql + " ORDER BY " + sortField + " " + sortOrder;

        Map<String, Object> contentParams = new LinkedHashMap<>(baseParams);
        contentSql = applyPaging(contentSql, contentParams, pageIndex, size);

        List<Map<String, Object>> content;
        int contentColumnCount;
        BoundStatement contentBound = bind(contentSql, contentParams);
        try (PreparedStatement ps = connection.prepareStatement(contentBound.sql())) {
            bindValues(ps, contentBound.values());
            try (ResultSet rs = ps.executeQuery()) {
                // Captured before mapResultSet() consumes the ResultSet, and used below to
                // build the derived-table column alias list for the count query - see the
                // "Column labels are never a problem for the count query" class-level javadoc
                // note for why this is needed.
                contentColumnCount = rs.getMetaData().getColumnCount();
                content = mapResultSet(rs);
            }
        }

        // ------------------- Count query: derived-table wrap -------------------
        // The derived table is given an explicit column alias list (dd_col_1, dd_col_2, ...)
        // rather than inheriting the caller's own SELECT list's column names/labels. This is
        // what lets a caller-authored query whose SELECT list produces two or more columns with
        // the same label (e.g. an unaliased self-join on tables that share a column name) still
        // work: without an explicit alias list here, most databases - including H2 - reject a
        // derived table whose (inherited) column list contains a duplicate name, even though
        // that name is never actually referenced (the outer query only does COUNT(*)). See the
        // class-level javadoc for the supported-database caveat on this syntax.
        String countSql = "SELECT COUNT(*) FROM (" + filteredSql + ") AS dd_count("
                + buildDerivedTableColumnAliasList(contentColumnCount) + ")";
        long total;
        BoundStatement countBound = bind(countSql, baseParams);
        try (PreparedStatement ps = connection.prepareStatement(countBound.sql())) {
            bindValues(ps, countBound.values());
            try (ResultSet rs = ps.executeQuery()) {
                total = rs.next() ? rs.getLong(1) : 0L;
            }
        }

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

    // ---------------- SQL assembly helpers ----------------

    /**
     * Splices a predicate into a SQL string, inserting it before the earliest top-level (i.e.
     * not nested inside a subquery's own parentheses) occurrence of any {@code GROUP BY}/
     * {@code HAVING}/{@code ORDER BY} clause (or at the end, if none are present at the top
     * level), and combining it with {@code AND} if the query already has a top-level
     * {@code WHERE} clause or with a new {@code WHERE} otherwise. {@code predicate} here already
     * comes pre-wrapped in its own parens by the caller (see {@link #doGetResult}), unlike
     * {@link PageDataJPQL}'s raw OR-joined predicate — the actual splicing logic itself is
     * shared with {@link PageDataJPQL} via {@link PageDataSupport#spliceTopLevelPredicate}.
     *
     * @param sql       the SQL to splice the predicate into
     * @param predicate the predicate to splice in, or {@code null} to leave {@code sql} unchanged
     * @return the resulting SQL, or the original {@code sql} unchanged if {@code predicate} is null
     */
    private String appendPredicate(String sql, String predicate) {
        return PageDataSupport.spliceTopLevelPredicate(
                sql, predicate, false, WHERE_PATTERN, GROUP_BY_PATTERN, HAVING_PATTERN, ORDER_BY_PATTERN);
    }

    /**
     * Appends a {@code LIMIT}/{@code OFFSET} clause for the given page, registering the two
     * reserved parameter names it binds. The offset is computed as a {@code long} (unlike JPA's
     * {@code Query.setFirstResult(int)}, JDBC parameter binding is not limited to {@code int}),
     * so a very large {@code pageIndex * size} cannot silently wrap around to a small/negative
     * offset.
     *
     * @param sql       the SQL to append paging to
     * @param params    the parameter map to add the paging values to
     * @param pageIndex the zero-based page index
     * @param size      the page size
     * @return the SQL with a trailing {@code LIMIT :__limit OFFSET :__offset} clause
     * @throws IllegalArgumentException if {@code params} already contains {@code __limit} or {@code __offset}
     */
    private String applyPaging(String sql, Map<String, Object> params, int pageIndex, int size) {
        requireNoReservedNameCollision(params, "__limit");
        requireNoReservedNameCollision(params, "__offset");
        params.put("__limit", (long) size);
        params.put("__offset", (long) pageIndex * (long) size);
        return sql + " LIMIT :__limit OFFSET :__offset";
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
                    "sqlParams must not contain the reserved parameter name '" + reservedName
                            + "' (used internally by PageDataSQL for paging/search)");
        }
    }

    /**
     * Builds a comma-separated, guaranteed-unique column alias list ({@code dd_col_1, dd_col_2,
     * ...}) for the count query's derived table (see {@link #doGetResult}), sized to the content
     * query's own column count. These aliases are internal to the count query alone - never
     * referenced by name, only counted via {@code COUNT(*)} - so their exact names don't matter,
     * only that there are exactly {@code columnCount} of them and no two collide with each other.
     *
     * @param columnCount the number of columns to generate aliases for
     * @return the column alias list, e.g. {@code "dd_col_1, dd_col_2, dd_col_3"}
     */
    private String buildDerivedTableColumnAliasList(int columnCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) sb.append(", ");
            sb.append("dd_col_").append(i);
        }
        return sb.toString();
    }

    // ---------------- Named -> positional parameter binding ----------------

    /**
     * Rewrites every {@code :name} occurrence in a SQL string (outside of string literals) into
     * a positional {@code ?} placeholder, and collects the corresponding values in order.
     *
     * @param namedSql the SQL string with named parameters
     * @param params   the values for each named parameter
     * @return the rewritten SQL plus the ordered list of bound values
     * @throws IllegalArgumentException if a named parameter has no corresponding entry in {@code params}
     */
    private BoundStatement bind(String namedSql, Map<String, Object> params) {
        List<int[]> literalSpans = findStringLiteralSpans(namedSql);

        StringBuilder positionalSql = new StringBuilder();
        List<Object> values = new ArrayList<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(namedSql);
        int last = 0;
        while (matcher.find()) {
            if (isWithinLiteral(matcher.start(), literalSpans)) {
                continue;
            }
            String name = matcher.group(1);
            if (!params.containsKey(name)) {
                throw new IllegalArgumentException(
                        "PageDataSQL: missing value for named parameter ':" + name + "'");
            }
            positionalSql.append(namedSql, last, matcher.start());
            positionalSql.append('?');
            values.add(params.get(name));
            last = matcher.end();
        }
        positionalSql.append(namedSql.substring(last));
        return new BoundStatement(positionalSql.toString(), values);
    }

    /**
     * Finds the character spans of every single-quoted string literal in a SQL string, so that
     * {@code :name}-shaped substrings inside them are not mistaken for named parameters.
     * Handles the standard {@code ''} escaped-quote convention.
     *
     * @param sql the SQL string to scan
     * @return the {@code [start, end)} character span of each string literal found, in order
     */
    private List<int[]> findStringLiteralSpans(String sql) {
        List<int[]> spans = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'') {
                int start = i;
                i++;
                while (i < n) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                            i += 2; // an escaped single quote; the literal continues
                            continue;
                        }
                        i++; // end of the literal
                        break;
                    }
                    i++;
                }
                spans.add(new int[]{start, i});
            } else {
                i++;
            }
        }
        return spans;
    }

    /**
     * Checks whether a character position falls within any of the given spans.
     *
     * @param pos   the character position to check
     * @param spans the {@code [start, end)} spans to check against
     * @return true if {@code pos} falls within any span
     */
    private boolean isWithinLiteral(int pos, List<int[]> spans) {
        for (int[] span : spans) {
            if (pos >= span[0] && pos < span[1]) return true;
        }
        return false;
    }

    /**
     * Binds a list of values onto a prepared statement, in positional order.
     *
     * @param ps     the prepared statement to bind onto
     * @param values the values to bind, in order
     * @throws SQLException if binding any value fails
     */
    private void bindValues(PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setObject(i + 1, values.get(i));
        }
    }

    /**
     * Converts every row of a result set into a map keyed by column label, preserving column
     * order. Every column, in order, is assigned a key that is guaranteed unique among all
     * columns of the result set: the column's own label is used if it has not already been
     * assigned to an earlier column, otherwise {@code _2}, {@code _3}, etc. is appended
     * (checked against every label already assigned so far - including other columns'
     * generated, disambiguated labels - not just the first occurrence of that exact label) so
     * that no two columns can ever end up sharing a key, even if the caller's own SQL aliases a
     * column to a name (e.g. {@code id_2}) that collides with a label this method would
     * otherwise generate for a different column. See the class-level javadoc for how the count
     * query avoids ever needing this same disambiguation itself.
     *
     * @param rs the result set to convert
     * @return one map per row, in result set order
     * @throws SQLException if reading the result set fails
     */
    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        String[] uniqueLabels = new String[columnCount];
        Set<String> assignedLabels = new HashSet<>();
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            String candidate = label;
            int suffix = 2;
            while (!assignedLabels.add(candidate)) {
                candidate = label + "_" + suffix++;
            }
            uniqueLabels[i - 1] = candidate;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(uniqueLabels[i - 1], rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    // ---------------- ResultSet -> List<Map<String,Object>> ----------------

    /**
     * A SQL string with all named parameters rewritten to {@code ?} placeholders, plus the
     * values bound to each, in order.
     *
     * @param sql    the SQL with named parameters rewritten to {@code ?} placeholders
     * @param values the values bound to each placeholder, in order
     */
    private record BoundStatement(String sql, List<Object> values) {
    }
}