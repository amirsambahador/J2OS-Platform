package org.j2os.examples.web.repository;
import org.j2os.examples.web.entity.human.Human;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HumanRepository extends JpaRepository<Human, Integer> {

}