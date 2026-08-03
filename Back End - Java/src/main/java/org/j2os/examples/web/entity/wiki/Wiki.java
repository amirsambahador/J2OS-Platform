package org.j2os.examples.web.entity.wiki;

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
public class Wiki implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer wikiId;
    @Column(columnDefinition = "VARCHAR")
    private String content;
    private String title;
    private String persianPublishDate;
    private String userPublisher;
    private String rowBackgroundColor;
    private String rowTextColor;

}
