package org.j2os.platform.jsecurity.access;

import lombok.experimental.UtilityClass;
import org.j2os.platform.page2.PageDataResultFilter;

import java.util.*;

/**
 * Removes or blanks out restricted fields from an outgoing API response, by wrapping the
 * response shape in a {@link PageDataResultFilter} and applying {@link
 * PageDataResultFilter#remove} or {@link PageDataResultFilter#empty} per restricted field.
 * <p>
 * Three overloads cover the shapes a response commonly takes: a full {@code getResult()}-style
 * map (with a {@code "rows"} entry), a bare list of rows, or a single object.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>{@code restrictedFieldAction} is compared case-insensitively against exactly {@link
 *       #EMPTY}; any other value — including {@code null}, a typo, or {@link #REMOVE} itself —
 *       falls through to removing the field. {@link #REMOVE} is documentation of that default,
 *       not a value this class actually checks for.</li>
 *   <li>Field restriction ultimately goes through {@link PageDataResultFilter}, so its own
 *       limitations apply here too — in particular, each row is converted to a {@code Map<String,
 *       Object>} via Jackson, and the original object type is not retained in the returned rows.</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@UtilityClass
public class ResponseAccessControl {

    /** Value of {@code restrictedFieldAction} that blanks a restricted field to an empty string instead of removing it. */
    public final String EMPTY = "EMPTY";

    /** Documents the default behavior for any {@code restrictedFieldAction} other than {@link #EMPTY}; not itself compared against. */
    public final String REMOVE = "REMOVE";

    /**
     * Restricts fields on every row of a {@code getResult()}-shaped map (one with a {@code
     * "rows"} entry).
     *
     * @param page2ResultStructure the result map to restrict, or {@code null}
     * @param restrictedFields     the (possibly dotted, nested) field paths to restrict
     * @param restrictedFieldAction {@link #EMPTY} to blank restricted fields, or anything else to remove them
     * @return the same map, with restricted fields removed/blanked on every row, or {@code null} if the input was {@code null}
     */
    public Map<String, Object> apply(
            Map<String, Object> page2ResultStructure,
            Collection<String> restrictedFields,
            String restrictedFieldAction) {
        if (page2ResultStructure == null) {
            return null;
        }

        PageDataResultFilter<?> filter = new PageDataResultFilter<>(page2ResultStructure);
        applyFieldActions(filter, restrictedFields, restrictedFieldAction);
        return filter.getResult();
    }

    /**
     * Restricts fields on every item of a bare list of rows.
     *
     * @param rows                  the rows to restrict, or {@code null}
     * @param restrictedFields      the (possibly dotted, nested) field paths to restrict
     * @param restrictedFieldAction {@link #EMPTY} to blank restricted fields, or anything else to remove them
     * @return the restricted rows, each converted to a {@code Map<String, Object>}, or {@code null} if the input was {@code null}
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> apply(
            List<?> rows,
            Collection<String> restrictedFields,
            String restrictedFieldAction) {
        if (rows == null) {
            return null;
        }

        Map<String, Object> pageDataFilterStructure = new HashMap<>();
        pageDataFilterStructure.put("rows", rows);
        PageDataResultFilter<?> filter = new PageDataResultFilter<>(pageDataFilterStructure);

        if (!rows.isEmpty()) {
            applyFieldActions(filter, restrictedFields, restrictedFieldAction);
        }

        return (List<Map<String, Object>>) filter.getResult().get("rows");
    }

    /**
     * Restricts fields on a single object.
     *
     * @param object                the object to restrict, or {@code null}
     * @param restrictedFields      the (possibly dotted, nested) field paths to restrict
     * @param restrictedFieldAction {@link #EMPTY} to blank restricted fields, or anything else to remove them
     * @return the restricted object, converted to a {@code Map<String, Object>}, or {@code null} if the input was {@code null}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(
            Object object,
            Collection<String> restrictedFields,
            String restrictedFieldAction) {
        if (object == null) {
            return null;
        }

        Map<String, Object> pageDataFilterStructure = new HashMap<>();
        List<Object> rows = new ArrayList<>();
        rows.add(object);
        pageDataFilterStructure.put("rows", rows);
        PageDataResultFilter<?> filter = new PageDataResultFilter<>(pageDataFilterStructure);

        applyFieldActions(filter, restrictedFields, restrictedFieldAction);

        return ((List<Map<String, Object>>) filter.getResult().get("rows")).get(0);
    }

    /**
     * Registers a remove or empty rule on the given filter for every restricted field.
     *
     * @param filter                the filter to register rules on
     * @param restrictedFields      the (possibly dotted, nested) field paths to restrict, or {@code null}/empty for none
     * @param restrictedFieldAction {@link #EMPTY} (case-insensitive) to blank each field, or anything else to remove it
     */
    private void applyFieldActions(
            PageDataResultFilter<?> filter,
            Collection<String> restrictedFields,
            String restrictedFieldAction) {
        if (restrictedFields == null || restrictedFields.isEmpty()) {
            return;
        }
        for (String field : restrictedFields) {
            if (Objects.nonNull(restrictedFieldAction) && restrictedFieldAction.equalsIgnoreCase(EMPTY)) {
                filter.empty(field);
            } else {
                filter.remove(field);
            }
        }
    }
}