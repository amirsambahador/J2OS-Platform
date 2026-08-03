package org.j2os.examples.web.repository;


import org.j2os.examples.web.entity.tree.Tree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface TreeRepository extends JpaRepository<Tree, Integer>, JpaSpecificationExecutor<Tree> {
    List<Tree> findAllByParentTree_TreeId(Integer parentCategoryId);
    boolean existsByParentTree_TreeId(Integer parentCategoryId);
    /*
    @Modifying
    @Transactional
    @NativeQuery("""
             DELETE FROM category WHERE category_id = :categoryId
            """)
    void deleteCategoryById(Integer categoryId);
    */
}