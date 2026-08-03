package org.j2os.examples.web.entity.tree;


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
public class Tree implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer treeId;
    @ManyToOne
    private Tree parentTree;

    private String treeName;
}
