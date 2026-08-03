package org.j2os.examples.desktop.jcrux;

import org.j2os.platform.jcrux.server.JCruxServer;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

/**
 * Starts an RMI registry and publishes two example JCrux containers on it:
 * one with autosave enabled and one with it disabled.
 * <p>
 * JCrux is a shared live-object container that lets multiple services
 * operate on one live, shared object by passing around only its id.
 * <p>
 * Usage constraints demonstrated by this example:
 * <ul>
 *     <li>Classes shared through JCrux (e.g. {@code Person}) must not use
 *     primitive fields and must not have null values.</li>
 *     <li>JCrux is only meant to run on trusted servers; clients are not
 *     allowed direct access to the server or client internals.</li>
 *     <li>Suitable for small data, up to about 1 GB per container.</li>
 *     <li>Enabling autosave ({@code true}) trades some performance for
 *     automatic persistence after every mutating call.</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ServerExample {

    /**
     * Starts the RMI registry and binds the two example containers to it.
     *
     * @param args not used
     * @throws Exception if the registry cannot be started, either container fails to
     *                    construct, or binding either container fails
     */
    public static void main(String[] args) throws Exception {
        // Container 1: autosave on — the file is updated automatically after every change.
        JCruxServer container1 = new JCruxServer("token1", "container1.dat", true);

        // Container 2: autosave off — the caller must invoke save() explicitly.
        JCruxServer container2 = new JCruxServer("token2", "container2.dat", false);

        LocateRegistry.createRegistry(1099);
        Naming.rebind("//localhost/container1", container1);
        Naming.rebind("//localhost/container2", container2);

        System.out.println("JCrux server is up on port 1099 (services: container1, container2)");
    }
}