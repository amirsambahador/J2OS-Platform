package org.j2os.examples.web.entity.person;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Location {
    private String geoAddress;
}
