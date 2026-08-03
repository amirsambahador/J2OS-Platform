package org.j2os.examples.desktop.jsecurity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Minimal example entity used to extend the {@link ResponseAccessExample} demo with a
 * self-referencing nested field ({@code manager}, itself an {@code Employee}) — a shape
 * {@code Person}/{@code Car}/{@code Factory} doesn't cover, since {@code manager} may be
 * {@code null} (an employee with no manager) rather than always populated.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@Getter
@Setter
@Accessors(chain=true)
public class Employee {

    /** This employee's id. */
    private Integer id;

    /** This employee's name. */
    private String name;

    /** The department this employee belongs to. */
    private String department;

    /** This employee's manager, or {@code null} if they have none (e.g. the top of the org chart). */
    private Employee manager;
}