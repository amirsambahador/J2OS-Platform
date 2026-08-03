package org.j2os.examples.web.api;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

import org.j2os.examples.web.entity.human.Human;
import org.j2os.examples.web.repository.HumanRepository;
import org.j2os.examples.web.shard.entity.Order;
import org.j2os.examples.web.shard.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ShardAPI {
    @Resource
    private DataSource dataSource1;//no shard datasource


    @Resource(name = "shardDataSource")
    private DataSource dataSource2;//shard datasource


    @PersistenceContext
    private EntityManager entityManager1;//no shard entityManager


    @PersistenceContext(unitName = "shardPersistenceUnit")
    private EntityManager entityManager2;//shard entityManager


    private final HumanRepository humanRepository;//no shard


    private final OrderRepository orderRepository;//shard

    @GetMapping(value = "/shard")
    //@Transactional("shardTransactionManager")//shard transaction
    //@Transactional//no shard transaction
    public Object shard(String desc) {
        System.out.println(dataSource1);
        System.out.println(dataSource2);
        System.out.println(entityManager1);
        System.out.println(entityManager2);
        Order order = new Order();
        order.setDescription(desc);
        Human human = new Human();
        humanRepository.save(human);//no shard
        orderRepository.save(order);//shard
        return Map.of("order:", orderRepository.findAll(), "person", humanRepository.findAll());

    }
}
