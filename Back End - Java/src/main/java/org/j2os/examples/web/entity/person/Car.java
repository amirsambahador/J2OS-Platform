package org.j2os.examples.web.entity.person;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class Car {
    private String name;
    private Factory factory;
}
