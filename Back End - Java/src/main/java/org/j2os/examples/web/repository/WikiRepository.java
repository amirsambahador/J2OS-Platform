package org.j2os.examples.web.repository;
import org.j2os.examples.web.entity.wiki.Wiki;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WikiRepository extends JpaRepository<Wiki, Integer> {

}