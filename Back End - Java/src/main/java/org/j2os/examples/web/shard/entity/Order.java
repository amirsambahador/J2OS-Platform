package org.j2os.examples.web.shard.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Table(name = "order_tbl")
@Entity
@Getter@Setter
public class Order {
    @Id
    private String orderId = UUID.randomUUID().toString();
    private String description;
}
