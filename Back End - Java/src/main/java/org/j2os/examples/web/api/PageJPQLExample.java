package org.j2os.examples.web.api;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.j2os.examples.web.common.handler.ErrorHandler;
import org.j2os.examples.web.entity.wiki.Wiki;
import org.j2os.platform.page2.PageDataJPQL;
import org.j2os.platform.page2.PageDataResultFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Demonstrates {@link PageDataJPQL} over {@link Wiki}: a plain query in {@link #getWikiFilter},
 * combined with {@link PageDataResultFilter} to post-process a field; and a parameterized query
 * with manual error handling in {@link #getWiki}.
 */
@RestController
@RequiredArgsConstructor
public class PageJPQLExample {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns a paginated/searchable/sortable list of {@link Wiki} records, with the
     * {@code content} field post-processed on the way out.
     *
     * @param params the page2 request parameters (paging/search/sort)
     * @return the page2 result, with {@code content} replaced on every row
     */
    @GetMapping("/getWikiFilter")
    @SuppressWarnings("unchecked")
    public Object getWikiFilter(@RequestParam Map<String, Object> params) {
        PageDataJPQL pageDataJPQL = new PageDataJPQL(entityManager);
        pageDataJPQL.searchAndSortOn("wikiId", "content", "title", "persianPublishDate");
        var result = pageDataJPQL.getResult("select o from Wiki o", "select count(o) from Wiki o", "o", Wiki.class, null, params);

        PageDataResultFilter<Wiki> filter = new PageDataResultFilter<>(result);
        filter.put("content", wiki -> wiki.getContent() + " (edited)");
        return filter.getResult();
    }

    /**
     * Returns a paginated/searchable/sortable list of {@link Wiki} records via a parameterized
     * JPQL query, with errors caught and handed to {@link ErrorHandler} instead of propagating.
     *
     * @param params   the page2 request parameters (paging/search/sort)
     * @param response the HTTP response, passed to {@link ErrorHandler} for error reporting
     * @return the page2 result, or whatever {@link ErrorHandler#getMessage} returns on failure
     */
    @GetMapping("/getWiki")
    public Object getWiki(@RequestParam Map<String, Object> params, HttpServletResponse response) {
        try {
            // This map holds the parameters the JPQL query needs. Left empty here since the
            // query below has none, but populated the same way if it did, e.g.:
            // Map<String, Object> map = new HashMap<>(Map.of("n", "%Amirsam%"));
            // (for an exact match, wrap the search term in quotes in the search box; multiple
            // terms can be searched at once, e.g. "Amirsam" "Bahador")
            Map<String, Object> map = new HashMap<>();
            PageDataJPQL dynamicData = new PageDataJPQL(entityManager);
            return dynamicData
                    .searchAndSortOn("wikiId", "persianPublishDate", "title", "userPublisher")
                    .getResult("SELECT entity FROM Wiki entity", "select count(entity) from Wiki entity", "entity", Wiki.class, map, params);
        } catch (Exception e) {
            return ErrorHandler.getMessage(e, response);
        }
    }
}