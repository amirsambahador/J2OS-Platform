package org.j2os.test.page2;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.j2os.platform.page2.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain, dependency-free test suite for the {@code org.j2os.platform.page2} library
 * (no test framework such as JUnit is used). Run it directly with its {@link #main(String[])}
 * method; each test case reports PASS/FAIL to standard output and a summary is printed at the end.
 * <p>
 * {@link PageDataList} and {@link PageDataResultFilter} are tested purely in-memory.
 * {@link PageDataSQL} is tested against a real in-memory H2 database. {@link PageDataEntity}
 * and {@link PageDataJPQL} are tested against a real Hibernate/H2 JPA setup, bootstrapped
 * entirely in Java by {@link HibernateBootstrap} — there is no {@code persistence.xml}
 * anywhere in this suite. The {@code TestPerson} entity in this package's {@code entity}
 * subpackage was written only for this suite, since the project's real entities were not
 * supplied alongside the library source files this suite was written against.
 * <p>
 * <b>Classpath requirements:</b> the H2 database driver ({@code com.h2database:h2}) and
 * Hibernate ORM, in addition to {@code jakarta.persistence-api}.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Page2Test {

    /**
     * Total number of test cases executed so far.
     */
    private static int totalTestCount = 0;

    /**
     * Number of test cases that failed so far.
     */
    private static int failedTestCount = 0;

    /**
     * JDBC URL of the in-memory H2 database backing the {@link PageDataEntity}/{@link PageDataJPQL} tests.
     */
    private static final String JPA_JDBC_URL = "jdbc:h2:mem:page2-test;DB_CLOSE_DELAY=-1";

    /**
     * The JPA entity manager factory used by the {@link PageDataEntity}/{@link PageDataJPQL} tests.
     */
    private static EntityManagerFactory entityManagerFactory;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        int exitCode = 0;
        try {
            setUp();

            testPageDataListAndOrPrecedence();
            testPageDataListSearchTokenAndExactMatch();
            testPageDataListSortAscendingAndDescending();
            testPageDataListPaging();
            testPageDataListMissingSortThrows();
            testPageDataListUnregisteredSortFieldThrows();
            testPageDataListInvalidFieldNameThrows();
            testPageDataListUnsupportedOperatorThrows();
            testPageDataListIsNullAndIsNotNull();
            testPageDataListEqualsNullNeverMatches();
            testPageDataListLikeAndNotLikeWildcardSemantics();
            testPageDataListNestedFieldPath();
            testPageDataListInvalidRowsParamThrows();
            testPageDataListHugePageDoesNotOverflow();

            testPageDataResultFilterRemoveEmptyMaskPut();
            testPageDataResultFilterBiTransform();
            testPageDataResultFilterSecondCallThrows();
            testPageDataResultFilterMissingRowsReturnsUnchanged();
            testPageDataResultFilterNullRowPassesThroughUnchanged();

            testPageDataSqlBasicQueryWithSearchSortPaging();
            testPageDataSqlReservedParamNameCollisionThrows();
            testPageDataSqlDoesNotMisparseColonInsideStringLiteral();
            testPageDataSqlMissingSortThrows();
            testPageDataSqlPlainSearchTreatsWildcardsLive();
            testPageDataSqlExactMatchEscapesWildcardCharacters();
            testPageDataSqlPlainSearchWithBackslashDoesNotBreakQuery();
            testPageDataSqlSpliceIntoExistingWhereClause();
            testPageDataSqlSpliceBeforeTopLevelGroupByAndHaving();
            testPageDataSqlDoesNotSpliceIntoNestedSubqueryClause();
            testPageDataSqlDisambiguatesCollidingColumnLabels();
            testPageDataSqlUnregisteredSortFieldThrows();

            testPageDataEntityBasicQueryWithSearchSortPaging();
            testPageDataEntitySearchAcrossField();
            testPageDataEntityInvalidOperatorThrows();
            testPageDataEntityUnmanagedEntityClassThrows();

            testPageDataJpqlBasicQueryWithSearchSortPaging();
            testPageDataJpqlReservedParamNameCollisionThrows();
            testPageDataJpqlSpliceIntoExistingWhereClause();
            testPageDataJpqlUnregisteredSortFieldThrows();
        } catch (Exception e) {
            System.out.println("[FATAL] Test setup failed: " + e);
            e.printStackTrace();
            exitCode = 2;
        } finally {
            tearDown();
        }

        printSummary();
        if (exitCode == 0 && failedTestCount > 0) {
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    // ------------------------------------------------------------------
    // Setup / teardown
    // ------------------------------------------------------------------

    /**
     * Opens the shared JPA entity manager factory used by the {@link PageDataEntity}/{@link
     * PageDataJPQL} tests, built entirely in Java by {@link HibernateBootstrap} (no
     * {@code persistence.xml} involved).
     */
    private static void setUp() {
        entityManagerFactory = HibernateBootstrap.createEntityManagerFactory(JPA_JDBC_URL, TestPerson.class);
    }

    /**
     * Closes the shared JPA entity manager factory.
     */
    private static void tearDown() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    /**
     * Opens a fresh in-memory H2 connection, seeded with four sample rows, for the
     * {@link PageDataSQL} tests.
     *
     * @return an open connection to the seeded database; the caller must close it
     * @throws Exception if opening the connection or seeding the data fails
     */
    private static Connection openTestSqlDatabase() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:page2-sql-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE person(id INT, name VARCHAR(100), status VARCHAR(50))");
            statement.execute("INSERT INTO person VALUES " +
                    "(1, 'Alice', 'active'), " +
                    "(2, 'Bob', 'pending:review'), " +
                    "(3, 'Carol', 'active'), " +
                    "(4, 'Dave', 'disabled')");
        }
        return connection;
    }

    /**
     * Clears any previously inserted rows, then persists four sample {@link TestPerson} rows,
     * and returns a fresh entity manager with them committed.
     * <p>
     * The clear step is necessary because {@link #entityManagerFactory} (and the in-memory H2
     * database behind it) is shared across the whole suite rather than reset per test, so
     * without it, rows from earlier {@link PageDataEntity}/{@link PageDataJPQL} tests would
     * still be present and would throw off row-count assertions in later tests.
     *
     * @return an open entity manager, with exactly the four sample rows committed
     */
    private static EntityManager openTestJpaEntityManager() {
        EntityManager em = entityManagerFactory.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from TestPerson").executeUpdate();
        em.persist(new TestPerson("Alice", 30));
        em.persist(new TestPerson("Bob", 25));
        em.persist(new TestPerson("Carol", 40));
        em.persist(new TestPerson("Dave", 35));
        em.getTransaction().commit();
        return em;
    }

    // ------------------------------------------------------------------
    // PageDataList
    // ------------------------------------------------------------------

    /**
     * A minimal record used only by the {@link PageDataList} tests.
     */
    private record Item(int id, String name, String category) {
    }

    /**
     * Verifies {@code where(A).or(B).and(C)} evaluates as {@code A OR (B AND C)} (AND binds
     * tighter than OR), not a naive left-to-right fold.
     */
    private static void testPageDataListAndOrPrecedence() {
        String testName = "PageDataList combines AND/OR with standard precedence (AND binds tighter)";
        List<Item> items = List.of(
                new Item(1, "one", "A"),   // matches A alone (via OR)
                new Item(2, "two", "B"),   // category B, name != "two-and-b" -> should NOT match B AND name=two-and-b
                new Item(3, "three", "B")  // category B AND name == "three" -> should match via (B AND C)
        );

        PageDataList pageDataList = new PageDataList();
        Map<String, Object> result = pageDataList
                .searchAndSortOn("id", "name", "category")
                .where("category", "=", "A")
                .or("category", "=", "B")
                .and("name", "=", "three")
                .getResult(items, Map.of("sort", "id"));

        @SuppressWarnings("unchecked")
        List<Item> rows = (List<Item>) result.get("rows");
        boolean correct = rows.size() == 2
                && rows.stream().anyMatch(i -> i.id() == 1)
                && rows.stream().anyMatch(i -> i.id() == 3)
                && rows.stream().noneMatch(i -> i.id() == 2);
        assertTrue(testName, correct);
    }

    /**
     * Verifies free-text search matches substrings by default, and exact terms only when quoted.
     */
    private static void testPageDataListSearchTokenAndExactMatch() {
        String testName = "PageDataList search matches substrings by default and exact terms when quoted";
        List<Item> items = List.of(
                new Item(1, "Johnathan", "A"),
                new Item(2, "John", "B"));

        PageDataList substringSearch = new PageDataList();
        Map<String, Object> substringResult = substringSearch
                .searchAndSortOn("id", "name")
                .getResult(items, Map.of("sort", "id", "q", "john"));

        PageDataList exactSearch = new PageDataList();
        Map<String, Object> exactResult = exactSearch
                .searchAndSortOn("id", "name")
                .getResult(items, Map.of("sort", "id", "q", "\"John\""));

        boolean substringMatchesBoth = ((List<?>) substringResult.get("rows")).size() == 2;
        boolean exactMatchesOnlyOne = ((List<?>) exactResult.get("rows")).size() == 1;
        assertTrue(testName, substringMatchesBoth && exactMatchesOnlyOne);
    }

    /**
     * Verifies sorting ascending and descending both produce the expected order.
     */
    private static void testPageDataListSortAscendingAndDescending() {
        String testName = "PageDataList sorts ascending and descending correctly";
        List<Item> items = List.of(new Item(3, "c", "X"), new Item(1, "a", "X"), new Item(2, "b", "X"));

        PageDataList ascending = new PageDataList();
        Map<String, Object> ascResult = ascending.searchAndSortOn("id").getResult(items, Map.of("sort", "id", "order", "ASC"));
        PageDataList descending = new PageDataList();
        Map<String, Object> descResult = descending.searchAndSortOn("id").getResult(items, Map.of("sort", "id", "order", "DESC"));

        @SuppressWarnings("unchecked")
        List<Item> ascRows = (List<Item>) ascResult.get("rows");
        @SuppressWarnings("unchecked")
        List<Item> descRows = (List<Item>) descResult.get("rows");

        boolean correct = ascRows.get(0).id() == 1 && ascRows.get(2).id() == 3
                && descRows.get(0).id() == 3 && descRows.get(2).id() == 1;
        assertTrue(testName, correct);
    }

    /**
     * Verifies paging returns the correct page size and total count across pages.
     */
    private static void testPageDataListPaging() {
        String testName = "PageDataList pages correctly and reports the total across all pages";
        List<Item> items = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            items.add(new Item(i, "item-" + i, "X"));
        }

        PageDataList pageDataList = new PageDataList();
        Map<String, Object> page2 = pageDataList.searchAndSortOn("id")
                .getResult(items, Map.of("sort", "id", "page", "2", "rows", "10"));

        @SuppressWarnings("unchecked")
        List<Item> rows = (List<Item>) page2.get("rows");
        boolean correct = rows.size() == 10 && rows.get(0).id() == 11 && ((Long) page2.get("total")) == 25L;
        assertTrue(testName, correct);
    }

    /**
     * Verifies a missing 'sort' key throws IllegalArgumentException rather than NullPointerException.
     */
    private static void testPageDataListMissingSortThrows() {
        String testName = "PageDataList.getResult throws IllegalArgumentException when 'sort' is missing";
        try {
            new PageDataList().searchAndSortOn("id").getResult(List.of(new Item(1, "a", "A")), Map.of());
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies sorting by a field never registered via searchAndSortOn throws.
     */
    private static void testPageDataListUnregisteredSortFieldThrows() {
        String testName = "PageDataList.getResult throws when sorting by an unregistered field";
        try {
            new PageDataList().searchAndSortOn("id").getResult(List.of(new Item(1, "a", "A")), Map.of("sort", "name"));
            fail(testName + " [expected RuntimeException]");
        } catch (RuntimeException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies an unsafe/invalid field name is rejected.
     */
    private static void testPageDataListInvalidFieldNameThrows() {
        String testName = "PageDataList rejects an invalid/unsafe field name";
        try {
            new PageDataList().where("id; DROP TABLE x", "=", 1);
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies an unsupported operator is rejected when a condition is evaluated.
     */
    private static void testPageDataListUnsupportedOperatorThrows() {
        String testName = "PageDataList rejects an unsupported operator when conditions are evaluated";
        try {
            new PageDataList()
                    .searchAndSortOn("id")
                    .where("id", "BETWEEN", 1)
                    .getResult(List.of(new Item(1, "a", "A")), Map.of("sort", "id"));
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /**
     * A minimal item type with a nullable field, used only for the IS NULL / LIKE tests below.
     */
    private record NullableItem(int id, String category) {
    }

    /**
     * Verifies IS NULL and IS NOT NULL correctly partition rows with a null field, and that an
     * ordinary comparison operator (e.g. "=") never matches when compared against an explicit
     * null value (SQL three-valued-logic semantics).
     */
    private static void testPageDataListIsNullAndIsNotNull() {
        String testName = "PageDataList IS NULL / IS NOT NULL partition rows correctly";
        List<NullableItem> items = List.of(
                new NullableItem(1, "A"),
                new NullableItem(2, null),
                new NullableItem(3, "B"));

        PageDataList isNullQuery = new PageDataList();
        Map<String, Object> isNullResult = isNullQuery
                .searchAndSortOn("id")
                .where("category", "IS NULL", null)
                .getResult(items, Map.of("sort", "id"));

        PageDataList isNotNullQuery = new PageDataList();
        Map<String, Object> isNotNullResult = isNotNullQuery
                .searchAndSortOn("id")
                .where("category", "IS NOT NULL", null)
                .getResult(items, Map.of("sort", "id"));

        @SuppressWarnings("unchecked")
        List<NullableItem> nullRows = (List<NullableItem>) isNullResult.get("rows");
        @SuppressWarnings("unchecked")
        List<NullableItem> notNullRows = (List<NullableItem>) isNotNullResult.get("rows");

        boolean correct = nullRows.size() == 1 && nullRows.get(0).id() == 2
                && notNullRows.size() == 2;
        assertTrue(testName, correct);
    }

    /**
     * Verifies that comparing a field with "=" against an explicit null value never matches any
     * row - not even a row whose own field value is also null - mirroring SQL's three-valued
     * logic (use IS NULL instead).
     */
    private static void testPageDataListEqualsNullNeverMatches() {
        String testName = "PageDataList '=' against an explicit null value never matches, even a null field";
        List<NullableItem> items = List.of(new NullableItem(1, null), new NullableItem(2, "A"));

        PageDataList query = new PageDataList();
        Map<String, Object> result = query
                .searchAndSortOn("id")
                .where("category", "=", null)
                .getResult(items, Map.of("sort", "id"));

        @SuppressWarnings("unchecked")
        List<NullableItem> rows = (List<NullableItem>) result.get("rows");
        assertTrue(testName, rows.isEmpty());
    }

    /**
     * Verifies LIKE/NOT LIKE use SQL-style wildcard matching ('%'/'_' ) and that a null field
     * value matches neither LIKE nor NOT LIKE.
     */
    private static void testPageDataListLikeAndNotLikeWildcardSemantics() {
        String testName = "PageDataList LIKE/NOT LIKE use SQL wildcard semantics and never match a null field";
        List<NullableItem> items = List.of(
                new NullableItem(1, "Apple"),
                new NullableItem(2, "Banana"),
                new NullableItem(3, null));

        PageDataList likeQuery = new PageDataList();
        Map<String, Object> likeResult = likeQuery
                .searchAndSortOn("id")
                .where("category", "LIKE", "A%")
                .getResult(items, Map.of("sort", "id"));

        PageDataList notLikeQuery = new PageDataList();
        Map<String, Object> notLikeResult = notLikeQuery
                .searchAndSortOn("id")
                .where("category", "NOT LIKE", "A%")
                .getResult(items, Map.of("sort", "id"));

        @SuppressWarnings("unchecked")
        List<NullableItem> likeRows = (List<NullableItem>) likeResult.get("rows");
        @SuppressWarnings("unchecked")
        List<NullableItem> notLikeRows = (List<NullableItem>) notLikeResult.get("rows");

        // LIKE 'A%' matches only "Apple" (id 1); the null-category row (id 3) matches neither
        // LIKE nor NOT LIKE, so NOT LIKE should match only "Banana" (id 2), not id 3.
        boolean correct = likeRows.size() == 1 && likeRows.get(0).id() == 1
                && notLikeRows.size() == 1 && notLikeRows.get(0).id() == 2;
        assertTrue(testName, correct);
    }

    /** A minimal nested-object pair, used only for the dotted-field-path test below. */
    private record Address(String city) {
    }

    private record Person(int id, String name, Address address) {
    }

    /**
     * Verifies PageDataList can filter, search, and sort on a dotted/nested field path
     * (e.g. "address.city"), which the class-level javadoc explicitly documents as supported
     * via reflection.
     */
    private static void testPageDataListNestedFieldPath() {
        String testName = "PageDataList supports filtering, searching, and sorting on a nested dotted field path";
        List<Person> items = List.of(
                new Person(1, "Amir", new Address("Tehran")),
                new Person(2, "Sara", new Address("Berlin")),
                new Person(3, "Reza", new Address("Tehran")));

        PageDataList query = new PageDataList();
        Map<String, Object> result = query
                .searchAndSortOn("id", "address.city")
                .where("address.city", "=", "Tehran")
                .getResult(items, Map.of("sort", "address.city"));

        @SuppressWarnings("unchecked")
        List<Person> rows = (List<Person>) result.get("rows");
        boolean correct = rows.size() == 2
                && rows.stream().allMatch(p -> "Tehran".equals(p.address().city()));
        assertTrue(testName, correct);
    }

    /**
     * Verifies a non-numeric 'rows' grid parameter throws IllegalArgumentException with a clear
     * message, rather than a raw NumberFormatException.
     */
    private static void testPageDataListInvalidRowsParamThrows() {
        String testName = "PageDataList.getResult throws IllegalArgumentException for a non-numeric 'rows' param";
        try {
            new PageDataList().searchAndSortOn("id")
                    .getResult(List.of(new Item(1, "a", "A")), Map.of("sort", "id", "rows", "not-a-number"));
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies an absurdly large 'page' value (which would overflow int when multiplied by
     * 'rows') is clamped rather than wrapping to a small/negative offset - the result should
     * simply be an empty page, not an exception and not a wrapped-around page of real data.
     */
    private static void testPageDataListHugePageDoesNotOverflow() {
        String testName = "PageDataList clamps an overflow-inducing page number instead of wrapping around";
        List<Item> items = List.of(new Item(1, "a", "A"), new Item(2, "b", "A"));

        PageDataList query = new PageDataList();
        Map<String, Object> result = query.searchAndSortOn("id")
                .getResult(items, Map.of("sort", "id", "page", "999999999", "rows", "999999999"));

        @SuppressWarnings("unchecked")
        List<Item> rows = (List<Item>) result.get("rows");
        // A huge page*rows offset must simply skip past all data (empty page), never throw and
        // never wrap around to return page-1 data again.
        assertTrue(testName, rows.isEmpty());
    }

    // ------------------------------------------------------------------
    // PageDataResultFilter
    // ------------------------------------------------------------------

    /**
     * A minimal record used only by the {@link PageDataResultFilter} tests.
     */
    private record FilterTestItem(int id, String firstName, String lastName) {
    }

    /**
     * Verifies remove/empty/mask/put(simple) each transform rows as expected.
     */
    private static void testPageDataResultFilterRemoveEmptyMaskPut() {
        String testName = "PageDataResultFilter remove/empty/mask/put(simple) transform rows as expected";
        List<FilterTestItem> items = List.of(new FilterTestItem(1, "Amir", "Bahador"));
        Map<String, Object> raw = new HashMap<>();
        raw.put("rows", items);
        raw.put("total", 1L);

        PageDataResultFilter<FilterTestItem> filter = new PageDataResultFilter<>(raw);
        Map<String, Object> result = filter
                .remove("id")
                .empty("lastName")
                .mask("firstName")
                .put("fullName", item -> item.firstName() + " " + item.lastName())
                .getResult();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        Map<String, Object> row = rows.get(0);

        boolean correct = !row.containsKey("id")
                && "".equals(row.get("lastName"))
                && "********".equals(row.get("firstName"))
                && "Amir Bahador".equals(row.get("fullName"));
        assertTrue(testName, correct);
    }

    /**
     * Verifies put(field, targetClass, BiFunction) can read and transform the field's current value.
     */
    private static void testPageDataResultFilterBiTransform() {
        String testName = "PageDataResultFilter.put(BiFunction) reads and transforms the current field value";
        List<FilterTestItem> items = List.of(new FilterTestItem(1, "Amir", "Bahador"));
        Map<String, Object> raw = new HashMap<>();
        raw.put("rows", items);

        PageDataResultFilter<FilterTestItem> filter = new PageDataResultFilter<>(raw);
        Map<String, Object> result = filter
                .put("lastName", String.class, (item, currentLastName) -> currentLastName.toUpperCase())
                .getResult();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertTrue(testName, "BAHADOR".equals(rows.get(0).get("lastName")));
    }

    /**
     * Verifies a second call to getResult() on the same instance throws IllegalStateException.
     */
    private static void testPageDataResultFilterSecondCallThrows() {
        String testName = "PageDataResultFilter.getResult() throws on a second call";
        Map<String, Object> raw = new HashMap<>();
        raw.put("rows", List.of(new FilterTestItem(1, "Amir", "Bahador")));

        PageDataResultFilter<FilterTestItem> filter = new PageDataResultFilter<>(raw);
        filter.getResult();
        try {
            filter.getResult();
            fail(testName + " [expected IllegalStateException]");
        } catch (IllegalStateException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies that when the wrapped result map has no "rows" entry (or it isn't a List), all
     * rules are skipped and the map is returned unchanged rather than throwing.
     */
    private static void testPageDataResultFilterMissingRowsReturnsUnchanged() {
        String testName = "PageDataResultFilter returns the map unchanged when 'rows' is absent";
        Map<String, Object> raw = new HashMap<>();
        raw.put("total", 0L);

        PageDataResultFilter<FilterTestItem> filter = new PageDataResultFilter<>(raw);
        Map<String, Object> result = filter.remove("id").getResult();

        assertTrue(testName, result == raw && !result.containsKey("rows"));
    }

    /**
     * Verifies a null entry within the "rows" list (e.g. from an outer-joined caller query) is
     * passed through unchanged rather than throwing a NullPointerException.
     */
    private static void testPageDataResultFilterNullRowPassesThroughUnchanged() {
        String testName = "PageDataResultFilter passes a null row through unchanged instead of throwing";
        List<FilterTestItem> items = new ArrayList<>();
        items.add(new FilterTestItem(1, "Amir", "Bahador"));
        items.add(null);
        Map<String, Object> raw = new HashMap<>();
        raw.put("rows", items);

        try {
            PageDataResultFilter<FilterTestItem> filter = new PageDataResultFilter<>(raw);
            Map<String, Object> result = filter.mask("firstName").getResult();

            @SuppressWarnings("unchecked")
            List<Object> rows = (List<Object>) result.get("rows");
            assertTrue(testName, rows.size() == 2 && rows.get(1) == null);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // PageDataSQL
    // ------------------------------------------------------------------

    /**
     * Verifies a basic SQL query with search, sort, and paging returns the expected rows and total.
     */
    private static void testPageDataSqlBasicQueryWithSearchSortPaging() {
        String testName = "PageDataSQL basic query with search + sort + paging returns expected rows/total";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name", "status")
                    .getResult("SELECT id, name, status FROM person", null,
                            Map.of("sort", "id", "order", "DESC", "page", "1", "rows", "2", "q", "active"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            // "active" matches Alice(1) and Carol(3); DESC by id -> Carol first.
            boolean correct = ((Long) result.get("total")) == 2L
                    && rows.size() == 2
                    && "Carol".equals(rows.get(0).get("NAME"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies a caller-supplied sqlParams entry using a reserved name is rejected.
     */
    private static void testPageDataSqlReservedParamNameCollisionThrows() {
        String testName = "PageDataSQL rejects sqlParams using the reserved '__limit' name";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            pageDataSQL.searchAndSortOn("id", "name")
                    .getResult("SELECT id, name FROM person", Map.of("__limit", 5), Map.of("sort", "id"));
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies a colon-containing value inside a single-quoted SQL string literal (e.g.
     * {@code 'pending:review'}) is not misparsed as a named parameter.
     */
    private static void testPageDataSqlDoesNotMisparseColonInsideStringLiteral() {
        String testName = "PageDataSQL does not misparse a colon inside a string literal as a named parameter";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name")
                    .getResult("SELECT id, name FROM person WHERE status = 'pending:review'", null, Map.of("sort", "id"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            assertTrue(testName, rows.size() == 1 && "Bob".equals(rows.get(0).get("NAME")));
        } catch (Exception e) {
            fail(testName + " [unexpected exception (likely misparsed as a named parameter): " + e + "]");
        }
    }

    /**
     * Verifies a missing 'sort' key throws IllegalArgumentException.
     */
    private static void testPageDataSqlMissingSortThrows() {
        String testName = "PageDataSQL.getResult throws IllegalArgumentException when 'sort' is missing";
        try (Connection connection = openTestSqlDatabase()) {
            new PageDataSQL(connection).searchAndSortOn("id").getResult("SELECT id FROM person", null, Map.of());
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies that in plain (unquoted) search mode, '%' and '_' in the user's search term act
     * as live SQL wildcards rather than being escaped - i.e. searching "A_ice" (with a literal
     * underscore wildcard) still matches "Alice", the way a raw "LIKE '%A_ice%'" would.
     */
    private static void testPageDataSqlPlainSearchTreatsWildcardsLive() {
        String testName = "PageDataSQL plain-mode search treats '_' as a live single-char wildcard";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name", "status")
                    .getResult("SELECT id, name, status FROM person", null,
                            Map.of("sort", "id", "q", "A_ice"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            // "_" is a live wildcard, so "A_ice" matches "Alice" (A + any-1-char + ice).
            boolean correct = rows.size() == 1 && "Alice".equals(rows.get(0).get("NAME"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies that in exact-match (quoted) search mode, a literal '%' character in the term is
     * matched literally rather than as a wildcard - i.e. quoting "\"50%\"" only matches rows
     * containing the literal text "50%", not every row (which an unescaped '%' wildcard would).
     */
    private static void testPageDataSqlExactMatchEscapesWildcardCharacters() {
        String testName = "PageDataSQL exact-match search escapes literal '%' instead of treating it as a wildcard";
        try (Connection connection = openTestSqlDatabase();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO person VALUES (5, 'Fifty%OffPromo', 'active')");

            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name", "status")
                    .getResult("SELECT id, name, status FROM person", null,
                            Map.of("sort", "id", "q", "\"Fifty%OffPromo\""));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            // Exact match on the literal string containing '%' - must match exactly one row,
            // not every row (which it would if '%' were left as a live wildcard).
            boolean correct = rows.size() == 1 && "Fifty%OffPromo".equals(rows.get(0).get("NAME"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies that a plain-mode search term containing a literal backslash (e.g. a Windows-style
     * path) does not throw a SQL error and does not corrupt the generated ESCAPE '\' clause.
     */
    private static void testPageDataSqlPlainSearchWithBackslashDoesNotBreakQuery() {
        String testName = "PageDataSQL plain-mode search with a literal backslash does not break the query";
        try (Connection connection = openTestSqlDatabase();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO person VALUES (6, 'C:\\Users\\amir', 'active')");

            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name", "status")
                    .getResult("SELECT id, name, status FROM person", null,
                            Map.of("sort", "id", "q", "C:\\Users"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            boolean correct = rows.size() == 1 && rows.get(0).get("NAME").toString().contains("Users");
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception (likely a broken ESCAPE clause): " + e + "]");
        }
    }

    /**
     * Verifies the search predicate is combined with AND when the caller's SQL already has a
     * top-level WHERE clause (rather than being appended as a second, invalid WHERE).
     */
    private static void testPageDataSqlSpliceIntoExistingWhereClause() {
        String testName = "PageDataSQL splices the search predicate with AND into an existing WHERE clause";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name", "status")
                    .getResult("SELECT id, name, status FROM person WHERE status <> 'disabled'", null,
                            Map.of("sort", "id", "q", "alice"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            boolean correct = rows.size() == 1 && "Alice".equals(rows.get(0).get("NAME"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies the search predicate is spliced in *before* a top-level GROUP BY/HAVING, not
     * appended after it (which would be invalid SQL), when the caller's query includes one.
     */
    private static void testPageDataSqlSpliceBeforeTopLevelGroupByAndHaving() {
        String testName = "PageDataSQL splices the search predicate before a top-level GROUP BY/HAVING clause";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("status")
                    .getResult(
                            "SELECT status, COUNT(*) AS cnt FROM person GROUP BY status HAVING COUNT(*) >= 1",
                            null,
                            Map.of("sort", "status", "q", "active"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            // Query must still be valid SQL (no exception) and the search must actually filter
            // status down to just 'active' before grouping.
            boolean correct = rows.size() == 1 && "active".equals(rows.get(0).get("STATUS"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception (likely spliced after GROUP BY/HAVING): " + e + "]");
        }
    }

    /**
     * Verifies a GROUP BY/ORDER BY that only appears inside a nested subquery (e.g. within a
     * WHERE ... IN (...)) is NOT mistaken for a top-level clause - the search predicate must
     * still be spliced at the outer query's top level, not into the middle of the subquery.
     */
    private static void testPageDataSqlDoesNotSpliceIntoNestedSubqueryClause() {
        String testName = "PageDataSQL does not splice the search predicate into a nested subquery's own GROUP BY";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id", "name", "status")
                    .getResult(
                            "SELECT id, name, status FROM person WHERE status IN "
                                    + "(SELECT status FROM person GROUP BY status)",
                            null,
                            Map.of("sort", "id", "q", "alice"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            // If the predicate were wrongly spliced into the subquery's GROUP BY, this would
            // either throw or fail to filter correctly at the outer level.
            boolean correct = rows.size() == 1 && "Alice".equals(rows.get(0).get("NAME"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception (likely spliced into the nested subquery): " + e + "]");
        }
    }

    /**
     * Verifies that when a caller's SQL produces two columns with the same label (here, via the
     * caller's own alias explicitly colliding - "id AS dup, name AS dup" - rather than via a
     * join), both values survive in the result row under distinct, disambiguated keys instead of
     * one silently overwriting the other, and that getResult() succeeds end-to-end (the count
     * query's derived table no longer inherits the caller's colliding column labels - see
     * PageDataSQL's class-level javadoc).
     */
    private static void testPageDataSqlDisambiguatesCollidingColumnLabels() {
        String testName = "PageDataSQL disambiguates colliding column labels instead of overwriting values";
        try (Connection connection = openTestSqlDatabase()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            Map<String, Object> result = pageDataSQL
                    .searchAndSortOn("id")
                    .getResult(
                            "SELECT id AS dup, name AS dup FROM person WHERE id = 1",
                            null,
                            Map.of("sort", "id", "rows", "1"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            Map<String, Object> row = rows.get(0);
            // Both columns are aliased "dup" by the caller; both must survive under distinct
            // keys with their own distinct values ("1" and "Alice"), not one silently
            // overwriting the other under a single "DUP" key. The total must also come back
            // correctly (1), proving the count query itself succeeded rather than throwing.
            boolean correct = ((Long) result.get("total")) == 1L
                    && row.size() == 2
                    && "1".equals(String.valueOf(row.get("DUP")))
                    && "Alice".equals(row.get("DUP_2"));
            assertTrue(testName, correct);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail(testName + " [unexpected exception: " + e + " | root cause: " + cause + "]");
        }
    }

    /**
     * Verifies sorting by a field never registered via searchAndSortOn() throws for PageDataSQL
     * (already covered for PageDataList, but not for the SQL-backed class).
     */
    private static void testPageDataSqlUnregisteredSortFieldThrows() {
        String testName = "PageDataSQL.getResult throws when sorting by an unregistered field";
        try (Connection connection = openTestSqlDatabase()) {
            new PageDataSQL(connection).searchAndSortOn("id")
                    .getResult("SELECT id, name FROM person", null, Map.of("sort", "name"));
            fail(testName + " [expected RuntimeException]");
        } catch (RuntimeException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected RuntimeException but got " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // PageDataEntity
    // ------------------------------------------------------------------

    /**
     * Verifies a basic entity query with a filter, sort, and paging returns the expected
     * rows and total.
     */
    private static void testPageDataEntityBasicQueryWithSearchSortPaging() {
        String testName = "PageDataEntity basic query with filter + sort + paging returns expected rows/total";
        EntityManager em = openTestJpaEntityManager();
        try {
            PageDataEntity pageDataEntity = new PageDataEntity(em);
            Map<String, Object> result = pageDataEntity
                    .searchAndSortOn("id", "name", "age")
                    .where("age", ">", 25)
                    .getResult(TestPerson.class, Map.of("sort", "age", "order", "ASC", "rows", "10"));

            @SuppressWarnings("unchecked")
            List<TestPerson> rows = (List<TestPerson>) result.get("rows");
            // age > 25: Alice(30), Carol(40), Dave(35) -> 3 rows, ascending by age.
            boolean correct = ((Long) result.get("total")) == 3L
                    && rows.size() == 3
                    && rows.get(0).getAge() == 30
                    && rows.get(2).getAge() == 40;
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            em.close();
        }
    }

    /**
     * Verifies PageDataEntity's free-text 'q' search actually filters rows (a where()-only
     * filter test can look similar but never exercises this path).
     */
    private static void testPageDataEntitySearchAcrossField() {
        String testName = "PageDataEntity free-text 'q' search filters rows by a registered field";
        EntityManager em = openTestJpaEntityManager();
        try {
            PageDataEntity pageDataEntity = new PageDataEntity(em);
            Map<String, Object> result = pageDataEntity
                    .searchAndSortOn("id", "name", "age")
                    .getResult(TestPerson.class, Map.of("sort", "name", "order", "ASC", "q", "a"));

            @SuppressWarnings("unchecked")
            List<TestPerson> rows = (List<TestPerson>) result.get("rows");
            // "a" (case-insensitive substring) matches Alice, Carol, Dave, not Bob -> 3 rows.
            boolean correct = ((Long) result.get("total")) == 3L
                    && rows.size() == 3
                    && rows.stream().noneMatch(p -> "Bob".equals(p.getName()));
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            em.close();
        }
    }

    /**
     * Verifies an operator outside the fixed allowed set is rejected before any query runs.
     */
    private static void testPageDataEntityInvalidOperatorThrows() {
        String testName = "PageDataEntity rejects an operator outside the allowed set";
        EntityManager em = openTestJpaEntityManager();
        try {
            new PageDataEntity(em).where("age", "BETWEEN", 1);
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } finally {
            em.close();
        }
    }

    /**
     * Verifies PageDataEntity rejects an entity class not managed by the EntityManager's
     * persistence unit, per the documented {@link IllegalArgumentException} contract.
     */
    private static void testPageDataEntityUnmanagedEntityClassThrows() {
        String testName = "PageDataEntity.getResult throws for a class not managed by this persistence unit";
        EntityManager em = openTestJpaEntityManager();
        try {
            new PageDataEntity(em)
                    .searchAndSortOn("id")
                    .getResult(String.class, Map.of("sort", "id"));
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        } finally {
            em.close();
        }
    }

    // ------------------------------------------------------------------
    // PageDataJPQL
    // ------------------------------------------------------------------

    /**
     * Verifies a caller-supplied JPQL query with search, sort, and paging returns the expected rows and total.
     */
    private static void testPageDataJpqlBasicQueryWithSearchSortPaging() {
        String testName = "PageDataJPQL basic query with search + sort + paging returns expected rows/total";
        EntityManager em = openTestJpaEntityManager();
        try {
            PageDataJPQL pageDataJPQL = new PageDataJPQL(em);
            Map<String, Object> result = pageDataJPQL
                    .searchAndSortOn("id", "name", "age")
                    .getResult(
                            "select o from TestPerson o",
                            "select count(o) from TestPerson o",
                            "o", TestPerson.class, null,
                            Map.of("sort", "name", "order", "ASC", "q", "a"));

            @SuppressWarnings("unchecked")
            List<TestPerson> rows = (List<TestPerson>) result.get("rows");
            // "a" (case-insensitive substring) matches Alice, Carol, Dave (not Bob) -> 3, sorted by name.
            boolean correct = ((Long) result.get("total")) == 3L
                    && rows.size() == 3
                    && "Alice".equals(rows.get(0).getName());
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            em.close();
        }
    }

    /**
     * Verifies a caller-supplied queryParams entry using a reserved search_N name is rejected.
     */
    private static void testPageDataJpqlReservedParamNameCollisionThrows() {
        String testName = "PageDataJPQL rejects queryParams using a reserved 'search_0' name";
        EntityManager em = openTestJpaEntityManager();
        try {
            PageDataJPQL pageDataJPQL = new PageDataJPQL(em);
            pageDataJPQL.searchAndSortOn("id", "name")
                    .getResult(
                            "select o from TestPerson o",
                            "select count(o) from TestPerson o",
                            "o", TestPerson.class,
                            Map.of("search_0", "collide"),
                            Map.of("sort", "name", "q", "a"));
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        } finally {
            em.close();
        }
    }

    /**
     * Verifies the same top-level-vs-nested splice correctness for PageDataJPQL, whose splicing
     * logic is shared with PageDataSQL but exercised through JPQL syntax instead.
     */
    private static void testPageDataJpqlSpliceIntoExistingWhereClause() {
        String testName = "PageDataJPQL splices the search predicate with AND into an existing WHERE clause";
        EntityManager em = openTestJpaEntityManager();
        try {
            PageDataJPQL pageDataJPQL = new PageDataJPQL(em);
            Map<String, Object> result = pageDataJPQL
                    .searchAndSortOn("id", "name", "age")
                    .getResult(
                            "select o from TestPerson o where o.age > 20",
                            "select count(o) from TestPerson o where o.age > 20",
                            "o", TestPerson.class, null,
                            Map.of("sort", "name", "q", "alice"));

            @SuppressWarnings("unchecked")
            List<TestPerson> rows = (List<TestPerson>) result.get("rows");
            boolean correct = rows.size() == 1 && "Alice".equals(rows.get(0).getName());
            assertTrue(testName, correct);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            em.close();
        }
    }

    /**
     * Verifies sorting by a field never registered via searchAndSortOn() throws for PageDataJPQL
     * (already covered for PageDataList, but not for the JPQL-backed class).
     */
    private static void testPageDataJpqlUnregisteredSortFieldThrows() {
        String testName = "PageDataJPQL.getResult throws when sorting by an unregistered field";
        EntityManager em = openTestJpaEntityManager();
        try {
            new PageDataJPQL(em).searchAndSortOn("id")
                    .getResult("select o from TestPerson o", "select count(o) from TestPerson o",
                            "o", TestPerson.class, null, Map.of("sort", "name"));
            fail(testName + " [expected RuntimeException]");
        } catch (RuntimeException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected RuntimeException but got " + e + "]");
        } finally {
            em.close();
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (no external test framework)
    // ------------------------------------------------------------------

    /**
     * Records a passing test case if {@code condition} is true, otherwise records a failure.
     *
     * @param testName  the name of the test case, printed in the report
     * @param condition the condition that must be true for the test to pass
     */
    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        totalTestCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void fail(String testName) {
        totalTestCount++;
        failedTestCount++;
        System.out.println("[FAIL] " + testName);
    }

    /**
     * Prints a final pass/fail summary of the whole suite.
     */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}