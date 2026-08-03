package org.j2os.examples.web.repository;
import org.j2os.examples.web.entity.human.Information;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformationRepository extends JpaRepository<Information, Integer> {

}