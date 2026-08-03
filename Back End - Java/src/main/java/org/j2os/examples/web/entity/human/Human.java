package org.j2os.examples.web.entity.human;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;

@Entity
@Data
@Accessors(chain = true)
@DynamicUpdate
@DynamicInsert
public class Human implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer humanId;
    private String name;
    private String family;
}
