package org.j2os.platform.jcrux.share;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Lightweight, serializable descriptor of an object held in a JCrux container,
 * as returned by {@link JCruxRemote#list(String)}. Carries only the object's
 * id and runtime class name, not the object itself.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@Getter
@Setter
public class JCruxObject implements Serializable {

    /**
     * The id the described object is stored under in the container.
     */
    private String objectId;

    /**
     * The fully qualified runtime class name of the described object.
     */
    private String objectType;
}
