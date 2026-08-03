package org.j2os.examples.desktop.jcrux;

import org.j2os.platform.jcrux.client.JCruxClient;
import org.j2os.platform.jcrux.share.JCruxObject;

/**
 * Demonstrates basic client-side usage of {@link JCruxClient}: connecting to a
 * container, creating a remote object, invoking a method on it, mutating a
 * field, and listing the container's contents.
 * <p>
 * Assumes {@link ServerExample} is already running and has published a
 * container named {@code container1} with autosave enabled.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ClientExample {

    /**
     * Runs the client example.
     *
     * @param args not used
     * @throws Exception if any remote call fails
     */
    public static void main(String[] args) throws Exception {
        JCruxClient client = new JCruxClient();
        client.connect("localhost", "container1", "token1");

        // Create a remote Person object; the constructor arguments are sent
        // to the server, which builds the object and returns its id.
        String personId = client.create("org.j2os.examples.desktop.jcrux.share.Person", "Amirsam", 30);
        System.out.println("Created remote object with id: " + personId);

        // Invoke a method on the remote object by id.
        client.invoke(personId, "introduceAs", "Sam");

        // Mutate a field on the remote object by id.
        client.setField(personId, "age", 31);
        System.out.println("Set age to 31. Since container1 has autosave enabled, the change is persisted automatically.");

        // Note: if this container had autosave disabled, an explicit
        // client.save() call would be required to persist the change, and
        // client.load() would need to be called to pick up changes made by
        // other clients.

        for (JCruxObject object : client.list()) {
            System.out.println(object.getObjectId() + " -> " + object.getObjectType());
        }
    }
}