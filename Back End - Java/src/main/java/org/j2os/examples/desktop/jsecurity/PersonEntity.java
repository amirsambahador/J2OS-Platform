package org.j2os.examples.desktop.jsecurity;

import lombok.Getter;
import lombok.Setter;

/**
 * Minimal example entity used to demonstrate {@link org.j2os.platform.jsecurity.access.RequestAccessControl}.
 * <p>
 * Every field is a wrapper type ({@code String}/{@code Integer}), which
 * {@code RequestAccessControl} requires of any field it may need to null out.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@Getter
@Setter
public class PersonEntity {

    /** This person's name. */
    private String name;

    /** This person's family (last) name. */
    private String family;

    /** This person's age, in years. */
    private Integer age;

    /** The school this person is associated with. */
    private String school;
}