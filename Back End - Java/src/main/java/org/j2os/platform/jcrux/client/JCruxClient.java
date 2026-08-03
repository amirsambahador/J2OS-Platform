package org.j2os.platform.jcrux.client;

import org.j2os.platform.jcrux.share.JCruxObject;
import org.j2os.platform.jcrux.share.JCruxRemote;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Client-side handle to a single JCrux container.
 * <p>
 * After {@link #connect(String, String, String)}, every other method
 * transparently forwards the stored authentication token to the remote
 * {@link JCruxRemote} container looked up during connection.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JCruxClient {

    /**
     * The authentication token supplied at {@link #connect(String, String, String)} and reused on every call.
     */
    private String token;

    /**
     * The remote container stub looked up during {@link #connect(String, String, String)}.
     */
    private JCruxRemote jCruxRemote;

    /**
     * Connects this client to a JCrux container published via RMI, and stores the
     * token to be used for all subsequent calls.
     *
     * @param ip      the host (optionally {@code host:port}) the RMI registry is running on
     * @param service the name the container is bound under in the RMI registry
     * @param token   the authentication token to use for this container on every subsequent call
     * @throws RemoteException       if the RMI lookup fails
     * @throws NotBoundException     if no container is bound under {@code service}
     * @throws MalformedURLException if {@code ip}/{@code service} do not form a valid RMI URL
     */
    public void connect(String ip, String service, String token) throws RemoteException, NotBoundException, MalformedURLException {
        this.token = token;
        jCruxRemote = (JCruxRemote) Naming.lookup("//" + ip + "/" + service);
    }

    /**
     * Serializes the entire container to disk.
     *
     * @throws Exception if the token is invalid, or if writing to disk fails
     */
    public void save() throws Exception {
        jCruxRemote.save(token);
    }

    /**
     * Replaces the container's contents with what is currently stored on disk.
     *
     * @throws Exception if the token is invalid, or if reading from disk fails
     */
    public void load() throws Exception {
        jCruxRemote.load(token);
    }

    /**
     * Lists every object currently held in the container.
     *
     * @return one {@link JCruxObject} per stored instance, carrying its id and runtime class name
     * @throws Exception if the token is invalid
     */
    public List<JCruxObject> list() throws Exception {
        return jCruxRemote.list(token);
    }

    /**
     * Instantiates a new object of the given class using the supplied constructor
     * arguments, and stores it in the container under a freshly generated id.
     *
     * @param classAddress the fully qualified name of the class to instantiate
     * @param params       the constructor arguments to use; each must be non-null
     * @return the generated id under which the new instance was stored
     * @throws Exception if the token is invalid, the class cannot be resolved, no matching
     *                   constructor exists, or construction fails
     */
    public String create(String classAddress, Object... params) throws Exception {
        return jCruxRemote.create(classAddress, params, token);
    }

    /**
     * Stores an already-constructed object in the container under a freshly generated id.
     *
     * @param object the object to store
     * @return the generated id under which the object was stored
     * @throws Exception if the token is invalid
     */
    public String put(Object object) throws Exception {
        return jCruxRemote.put(object, token);
    }

    /**
     * Retrieves the object stored under the given id.
     *
     * @param id the id of the object to retrieve
     * @return the stored object
     * @throws Exception if the token is invalid, or if no object is stored under {@code id}
     */
    public Object get(String id) throws Exception {
        return jCruxRemote.get(id, token);
    }

    /**
     * Invokes a method, by name, on the object stored under the given id.
     *
     * @param id         the id of the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @param params     the arguments to pass to the method; each must be non-null
     * @return the value returned by the invoked method
     * @throws Exception if the token is invalid, no object is stored under {@code id}, no matching
     *                   method exists, or the invocation itself throws
     */
    public Object invoke(String id, String methodName, Object... params) throws Exception {
        return jCruxRemote.invoke(id, methodName, params, token);
    }

    /**
     * Removes the object stored under the given id, if present.
     *
     * @param id the id of the object to remove
     * @throws Exception if the token is invalid
     */
    public void remove(String id) throws Exception {
        jCruxRemote.remove(id, token);
    }

    /**
     * Sets a field, by name, on the object stored under the given id.
     *
     * @param id        the id of the object to modify
     * @param attribute the name of the field to set
     * @param value     the value to assign to the field
     * @throws Exception if the token is invalid, no object is stored under {@code id}, no such
     *                   field exists, or the assignment fails
     */
    public void setField(String id, String attribute, Object value) throws Exception {
        jCruxRemote.setField(id, attribute, value, token);
    }

    /**
     * Reads a field, by name, from the object stored under the given id.
     *
     * @param id        the id of the object to read from
     * @param attribute the name of the field to read
     * @return the current value of the field
     * @throws Exception if the token is invalid, no object is stored under {@code id}, or no such
     *                   field exists
     */
    public Object getField(String id, String attribute) throws Exception {
        return jCruxRemote.getField(id, attribute, token);
    }

    /**
     * Returns the amount of free memory in the JVM running the server.
     *
     * @return the current free memory, in bytes
     * @throws Exception if the token is invalid
     */
    public long freeMemory() throws Exception {
        return jCruxRemote.freeMemory(token);
    }

    /**
     * Returns the total amount of memory allocated to the JVM running the server.
     *
     * @return the current total memory, in bytes
     * @throws Exception if the token is invalid
     */
    public long totalMemory() throws Exception {
        return jCruxRemote.totalMemory(token);
    }

    /**
     * Enables or disables automatic saving of the container to disk after every mutating call.
     *
     * @param autosave the new autosave setting
     * @throws Exception if the token is invalid
     */
    public void setAutoSave(boolean autosave) throws Exception {
        jCruxRemote.setAutoSave(autosave, token);
    }

    /**
     * Returns how long the server has been running.
     *
     * @return the number of milliseconds elapsed since the server was constructed
     * @throws Exception if the token is invalid
     */
    public long uptime() throws Exception {
        return jCruxRemote.uptime(token);
    }

    /**
     * Returns the number of live threads in the JVM running the server.
     *
     * @return the current JVM-wide thread count
     * @throws Exception if the token is invalid
     */
    public int jvmThreadCount() throws Exception {
        return jCruxRemote.jvmThreadCount(token);
    }

    /**
     * Returns when the container was last saved to disk.
     *
     * @return the epoch millisecond timestamp of the most recent save, or 0 if never saved
     * @throws Exception if the token is invalid
     */
    public long lastSaveTime() throws Exception {
        return jCruxRemote.lastSaveTime(token);
    }

    /**
     * Returns how many times the container has been saved to disk.
     *
     * @return the total number of completed saves
     * @throws Exception if the token is invalid
     */
    public long saveCount() throws Exception {
        return jCruxRemote.saveCount(token);
    }

    /**
     * Returns the file path the container is persisted to and restored from.
     *
     * @return the configured container file path
     * @throws Exception if the token is invalid
     */
    public String containerFilePath() throws Exception {
        return jCruxRemote.containerFilePath(token);
    }
}
