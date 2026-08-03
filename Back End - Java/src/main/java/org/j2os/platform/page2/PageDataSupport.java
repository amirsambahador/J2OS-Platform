package org.j2os.platform.page2;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Package-private helpers shared by the datagrid classes in this package ({@link PageDataEntity},
 * {@link PageDataJPQL}, {@link PageDataList}, {@link PageDataSQL}): field-name validation,
 * grid-parameter reading, free-text search token escaping, and locating the top-level insertion
 * point for a spliced-in search predicate. Not part of the public API of this package — factored
 * out purely so a fix to one of these pieces of logic only has to be made once instead of once
 * per class.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
final class PageDataSupport {

    /** Whitelist pattern a field name (including dotted nested paths) must match to be accepted. */
    static final Pattern FIELD_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");

    /** Pattern recognizing a fully double-quoted search query, e.g. {@code "term one" "term two"}, as an exact-match search. */
    static final Pattern EXACT_MATCH_PATTERN = Pattern.compile("^\"[^\"]+\"(?:\\s+\"[^\"]+\")*$");

    private PageDataSupport() {
        // Utility class; not instantiable.
    }

    /**
     * Validates a field name against the whitelist pattern.
     *
     * @param field the field name to validate
     * @throws IllegalArgumentException if {@code field} is null, empty, or does not match the whitelist pattern
     */
    static void ensureValidFieldName(String field) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("Field name is empty");
        }
        if (!FIELD_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid/unsafe field name: " + field);
        }
    }

    /**
     * Reads a grid parameter as a string, treating both an absent key and a key explicitly
     * mapped to {@code null} as "use the default" (unlike {@link Map#getOrDefault}, which only
     * falls back to the default when the key itself is absent).
     *
     * @param gridParams   the grid parameters to read from
     * @param key          the parameter name
     * @param defaultValue the value to use if the parameter is absent or {@code null}
     * @return the parameter's string value, or {@code defaultValue}
     */
    static String getStringParam(Map<String, Object> gridParams, String key, String defaultValue) {
        Object value = gridParams.get(key);
        return value == null ? defaultValue : value.toString();
    }

    /**
     * Reads a grid parameter as an integer, treating both an absent key and a key explicitly
     * mapped to {@code null} as "use the default".
     *
     * @param gridParams   the grid parameters to read from
     * @param key          the parameter name
     * @param defaultValue the value to use if the parameter is absent or {@code null}
     * @return the parsed integer value, or {@code defaultValue}
     * @throws IllegalArgumentException if the parameter is present but not a valid integer
     */
    static int parseIntParam(Map<String, Object> gridParams, String key, int defaultValue) {
        String raw = getStringParam(gridParams, key, null);
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "gridParams['" + key + "'] must be a valid integer, got: '" + raw + "'", e);
        }
    }

    /**
     * Escapes a quoted, exact-match search token's own {@code %}, {@code _}, and {@code \}
     * characters so they are matched literally by {@code LIKE ... ESCAPE '\'} instead of being
     * treated as SQL/JPQL wildcards — "exact match" means exact, so a user who quotes
     * {@code "50%"} gets rows containing the literal text {@code 50%}, not a wildcard match.
     * This must only be applied to a quoted, exact-match token; a plain (unquoted) token should
     * use {@link #escapeLikeBackslashOnly(String)} instead, so its own {@code %}/{@code _} keep
     * functioning as live wildcards for a fuzzy/power search.
     *
     * @param token the raw, exact-match search token
     * @return the token with {@code \}, {@code %}, and {@code _} backslash-escaped
     */
    static String escapeLikeToken(String token) {
        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Escapes only the {@code \} characters in a plain (non-exact-match) search token, leaving
     * {@code %} and {@code _} untouched so they still function as live SQL/JPQL wildcards.
     * <p>
     * Every generated {@code LIKE} expression in this package unconditionally declares
     * {@code ESCAPE '\'}, which makes {@code \} the escape character for the <em>entire</em>
     * pattern — not just for the parts built from {@link #escapeLikeToken(String)}. Without this
     * method, a plain-mode token containing a user-supplied {@code \} (e.g. a search for a
     * Windows path like {@code C:\Users}) would leave a dangling or misinterpreted escape
     * character in the generated pattern, which many databases (e.g. PostgreSQL) reject outright
     * with a SQL error, and which can otherwise silently change which rows match. Escaping the
     * token's own backslashes first keeps them literal regardless of how many the user typed or
     * where they fall, while {@code %}/{@code _} keep their wildcard meaning for plain-mode search.
     *
     * @param token the raw, plain-mode (non-exact-match) search token
     * @return the token with {@code \} backslash-escaped, {@code %}/{@code _} left as-is
     */
    static String escapeLikeBackslashOnly(String token) {
        return token.replace("\\", "\\\\");
    }

    /**
     * Finds the earliest position in {@code text} where any of the given patterns matches
     * <em>outside</em> of a single-quoted string literal and <em>at parenthesis depth zero</em>
     * (i.e. not inside a subquery or any other parenthesized expression), or the end of
     * {@code text} if no such match exists.
     * <p>
     * A naive first-match search (ignoring parens/literals) can find a {@code GROUP BY}/
     * {@code ORDER BY}/{@code HAVING} keyword that actually belongs to a subquery nested inside
     * the caller's {@code WHERE}/{@code FROM} clause (e.g.
     * {@code WHERE x IN (SELECT ... GROUP BY ...)}), and splice the search predicate into the
     * middle of that subquery instead of the intended, top-level position — breaking otherwise
     * valid, developer-authored SQL/JPQL. Restricting matches to paren-depth zero avoids this.
     * Single-quoted string literals (with standard {@code ''} escaping) are also skipped so a
     * keyword-shaped substring inside a literal is never mistaken for a real clause.
     * <p>
     * <b>Limitation:</b> SQL/JPQL comments ({@code --} to end of line, or {@code /* *&#47;})
     * are not specially handled — a pattern keyword that only appears inside a comment can still
     * be misidentified as a real clause. As with the similar limitation already documented for
     * named-parameter detection in {@link PageDataSQL}, the caller-supplied query text is
     * trusted, developer-authored input, so this is treated as an acceptable limitation rather
     * than a case worth the added complexity of a full SQL/JPQL comment tokenizer.
     * <p>
     * For performance, this scans {@code text} once, character by character, reusing one
     * {@link Matcher} per pattern (via {@link Matcher#region} + {@link Matcher#lookingAt}) rather
     * than compiling a fresh {@code Matcher} and re-searching from scratch at every position —
     * important for larger, more complex caller-authored queries.
     *
     * @param text     the SQL/JPQL text to search
     * @param patterns the patterns to look for (e.g. {@code GROUP BY}, {@code ORDER BY}, {@code HAVING})
     * @return the earliest top-level match start position, or {@code text.length()} if none is found
     */
    static int earliestTopLevelClauseStart(String text, Pattern... patterns) {
        int n = text.length();

        // One Matcher per pattern, created once and re-positioned via region()/lookingAt() at
        // each candidate index, instead of calling Pattern.matcher(text) (and re-scanning from
        // index i with find(i)) on every character - lookingAt() only has to check a match
        // anchored right at the region's start, which is exactly what's needed here.
        Matcher[] matchers = new Matcher[patterns.length];
        for (int p = 0; p < patterns.length; p++) {
            // useTransparentBounds(true) is required for correctness here: these patterns use
            // \b word-boundary anchors, and Matcher's default ("opaque") region bounds treat the
            // region's own start as if it were the start of the entire input for boundary
            // purposes - which would make \b match at a region start that sits mid-word (e.g.
            // right after 'Y' in "...MYGROUP BY...") purely because the matcher can't see the
            // preceding character. Transparent bounds let \b see the real surrounding text
            // outside the region, so it only matches at an actual word boundary.
            matchers[p] = patterns[p].matcher(text);
            matchers[p].useTransparentBounds(true);
        }

        int depth = 0;
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);

            if (c == '\'') {
                // Skip over the whole string literal (handling the standard '' escape) so a
                // keyword-shaped substring inside it is never treated as a real clause.
                i++;
                while (i < n) {
                    if (text.charAt(i) == '\'') {
                        if (i + 1 < n && text.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }

            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                if (depth > 0) depth--;
                i++;
                continue;
            }

            if (depth == 0) {
                for (Matcher m : matchers) {
                    m.region(i, n);
                    if (m.lookingAt()) {
                        return i;
                    }
                }
            }
            i++;
        }
        return n;
    }

    /**
     * Splices a predicate into a caller-authored SQL/JPQL string at the earliest top-level
     * position (see {@link #earliestTopLevelClauseStart}), combining it with {@code AND} if the
     * query already has a top-level {@code WHERE} clause, or with a new {@code WHERE} otherwise.
     * Shared by {@link PageDataJPQL} and {@link PageDataSQL}, whose splicing logic is otherwise
     * identical except for whether the predicate itself needs an extra wrapping pair of parens.
     *
     * @param text                 the SQL/JPQL text to splice the predicate into
     * @param predicate            the predicate to splice in, or {@code null} to leave {@code text} unchanged
     * @param wrapPredicateInParens whether to additionally wrap {@code predicate} itself in parens
     *                              when splicing (JPQL callers pass the raw OR-joined predicate and want
     *                              {@code AND (predicate)}; SQL callers already pre-wrap their predicate in
     *                              parens themselves and just want {@code AND predicate})
     * @param wherePattern         the pattern used to detect a pre-existing top-level {@code WHERE}
     * @param clauseTerminators    the patterns marking where a trailing clause begins (e.g.
     *                             {@code GROUP BY}/{@code HAVING}/{@code ORDER BY}), used to find the
     *                             splice point
     * @return the resulting text, or the original {@code text} unchanged if {@code predicate} is null
     */
    static String spliceTopLevelPredicate(String text, String predicate, boolean wrapPredicateInParens,
                                           Pattern wherePattern, Pattern... clauseTerminators) {
        if (predicate == null) return text;

        int insertAt = earliestTopLevelClauseStart(text, clauseTerminators);
        String head = text.substring(0, insertAt);
        String tail = text.substring(insertAt);

        // insertAt is a top-level (paren-depth-zero) position, so head's own parens are always
        // balanced - scanning head for a top-level WHERE (rather than a plain substring search)
        // avoids mistaking a WHERE that only appears inside a nested subquery for a real
        // top-level WHERE clause, which would otherwise wrongly splice in "AND ..." with no
        // preceding top-level WHERE to attach to.
        boolean hasTopLevelWhere = earliestTopLevelClauseStart(head, wherePattern) < head.length();

        String wrappedPredicate = wrapPredicateInParens ? "(" + predicate + ")" : predicate;
        String clause = hasTopLevelWhere ? " AND " + wrappedPredicate + " " : " WHERE " + wrappedPredicate + " ";
        return head + clause + tail;
    }
}
