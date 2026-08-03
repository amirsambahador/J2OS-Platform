package org.j2os.examples.web.shard.repository;


import org.j2os.examples.web.shard.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,String> {

}
