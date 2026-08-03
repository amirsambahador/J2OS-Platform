package org.j2os.examples.web.entity.person;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class Person {
    private List<Location> locations;
    private Integer id;
    private String firstName;
    private String lastName;
    private Car car;
}
