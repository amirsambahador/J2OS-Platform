package org.j2os.examples.web.api;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.j2os.examples.web.entity.human.Human;
import org.j2os.examples.web.entity.human.Information;
import org.j2os.examples.web.repository.HumanRepository;
import org.j2os.platform.page2.PageDataEntity;
import org.j2os.platform.page2.PageDataJPQL;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Demonstrates two page2 approaches side by side: {@link PageDataEntity} (generates JPQL from a
 * JPA entity class, no query to write yourself) in {@link #getHuman}, and {@link PageDataJPQL}
 * (caller-supplied raw JPQL, needed once the query involves a join/relation like
 * {@code Information -> Human}) in {@link #getInformation}.
 */
@RestController
@RequiredArgsConstructor
public class PageJPQLAndEntityRelationalExample {

    /**
     * Only used by the commented-out {@code PageDataList} alternative inside {@link #getHuman} -
     * kept as a reference for fetching through the repository instead of a raw
     * {@link EntityManager}.
     */
    private final HumanRepository humanRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns a paginated/searchable/sortable list of {@link Human} records, generated
     * automatically from the entity class by {@link PageDataEntity}.
     *
     * @param frontEndParameters the page2 request parameters (paging/search/sort)
     * @return the page2 result
     * @throws Exception if the query fails
     */
    @GetMapping("/getHuman")
    public Object getHuman(@RequestParam Map<String, Object> frontEndParameters) throws Exception {

        // Alternative: the same result via PageDataList over the repository's own findAll(),
        // instead of PageDataEntity generating JPQL directly from the entity class. Left here
        // for reference, not used by default since PageDataEntity is more efficient (avoids
        // loading every row into memory first).
        // PageDataList pageDataList = new PageDataList();
        // pageDataList.searchAndSortOn("humanId", "name", "family");
        // return pageDataList.getResult(humanRepository.findAll(), frontEndParameters);

        PageDataEntity pageDataEntity = new PageDataEntity(entityManager);
        pageDataEntity.searchAndSortOn("humanId", "name", "family");
        return pageDataEntity.getResult(Human.class, frontEndParameters);
    }

    /**
     * Returns a paginated/searchable/sortable list of {@link Information} records belonging to
     * one {@link Human}, via a hand-written JPQL join - {@link PageDataEntity} can't express
     * this on its own since it only generates a query over a single entity's own fields.
     *
     * @param frontEndParameters the page2 request parameters (paging/search/sort), plus the
     *                           {@code humanId} to filter by
     * @return the page2 result
     */
    @GetMapping("/getInformation")
    public Object getInformation(@RequestParam Map<String, Object> frontEndParameters) {
        PageDataJPQL pageDataJPQL = new PageDataJPQL(entityManager);
        var jpqlCount = "select count(info) from Information info where info.human.humanId = :humanId";
        var jpql = "select info from Information info where info.human.humanId = :humanId";

        HashMap<String, Object> jpqlParameter = new HashMap<>();
        jpqlParameter.put("humanId", frontEndParameters.get("humanId"));

        pageDataJPQL.searchAndSortOn("informationId", "content");

        return pageDataJPQL.getResult(
                jpql,
                jpqlCount,
                "info",
                Information.class,
                jpqlParameter,
                frontEndParameters
        );
    }
}