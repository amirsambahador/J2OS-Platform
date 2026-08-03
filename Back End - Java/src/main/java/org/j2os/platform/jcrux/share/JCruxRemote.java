package org.j2os.platform.jcrux.share;

import java.rmi.Remote;
import java.util.List;

/**
 * RMI contract implemented by the JCrux server and consumed through {@link
 * org.j2os.platform.jcrux.client.JCruxClient}.
 * <p>
 * Every operation is authenticated with a per-container {@code token}, which
 * must be supplied as the last argument of each call.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public interface JCruxRemote extends Remote {

    /**
     * Serializes the entire container to disk.
     *
     * @param token the caller's authentication token
     * @throws Exception if the token is invalid, or if writing to disk fails
     */
    void save(String token) throws Exception;

    /**
     * Replaces the container's contents with what is currently stored on disk.
     *
     * @param token the caller's authentication token
     * @throws Exception if the token is invalid, or if reading from disk fails
     */
    void load(String token) throws Exception;

    /**
     * Lists every object currently held in the container.
     *
     * @param token the caller's authentication token
     * @return one {@link JCruxObject} per stored instance, carrying its id and runtime class name
     * @throws Exception if the token is invalid
     */
    List<JCruxObject> list(String token) throws Exception;

    /**
     * Instantiates a new object of the given class using the supplied constructor
     * arguments, and stores it in the container under a freshly generated id.
     *
     * @param classAddress the fully qualified name of the class to instantiate
     * @param params       the constructor arguments to use; each must be non-null
     * @param token        the caller's authentication token
     * @return the generated id under which the new instance was stored
     * @throws Exception if the token is invalid, the class cannot be resolved, no matching
     *                   constructor exists, or construction fails
     */
    String create(String classAddress, Object[] params, String token) throws Exception;

    /**
     * Stores an already-constructed object in the container under a freshly generated id.
     *
     * @param object the object to store
     * @param token  the caller's authentication token
     * @return the generated id under which the object was stored
     * @throws Exception if the token is invalid
     */
    String put(Object object, String token) throws Exception;

    /**
     * Retrieves the object stored under the given id.
     *
     * @param id    the id of the object to retrieve
     * @param token the caller's authentication token
     * @return the stored object
     * @throws Exception if the token is invalid, or if no object is stored under {@code id}
     */
    Object get(String id, String token) throws Exception;

    /**
     * Invokes a method, by name, on the object stored under the given id.
     *
     * @param id         the id of the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @param params     the arguments to pass to the method; each must be non-null
     * @param token      the caller's authentication token
     * @return the value returned by the invoked method
     * @throws Exception if the token is invalid, no object is stored under {@code id}, no matching
     *                   method exists, or the invocation itself throws
     */
    Object invoke(String id, String methodName, Object[] params, String token) throws Exception;

    /**
     * Removes the object stored under the given id, if present.
     *
     * @param id    the id of the object to remove
     * @param token the caller's authentication token
     * @throws Exception if the token is invalid
     */
    void remove(String id, String token) throws Exception;

    /**
     * Sets a field, by name, on the object stored under the given id.
     *
     * @param id        the id of the object to modify
     * @param attribute the name of the field to set
     * @param value     the value to assign to the field
     * @param token     the caller's authentication token
     * @throws Exception if the token is invalid, no object is stored under {@code id}, no such
     *                   field exists, or the assignment fails
     */
    void setField(String id, String attribute, Object value, String token) throws Exception;

    /**
     * Reads a field, by name, from the object stored under the given id.
     *
     * @param id        the id of the object to read from
     * @param attribute the name of the field to read
     * @param token     the caller's authentication token
     * @return the current value of the field
     * @throws Exception if the token is invalid, no object is stored under {@code id}, or no such
     *                   field exists
     */
    Object getField(String id, String attribute, String token) throws Exception;

    /**
     * Returns the amount of free memory in the JVM running the server.
     *
     * @param token the caller's authentication token
     * @return the current free memory, in bytes
     * @throws Exception if the token is invalid
     */
    long freeMemory(String token) throws Exception;

    /**
     * Returns the total amount of memory allocated to the JVM running the server.
     *
     * @param token the caller's authentication token
     * @return the current total memory, in bytes
     * @throws Exception if the token is invalid
     */
    long totalMemory(String token) throws Exception;

    /**
     * Enables or disables automatic saving of the container to disk after every mutating call.
     *
     * @param autosave the new autosave setting
     * @param token    the caller's authentication token
     * @throws Exception if the token is invalid
     */
    void setAutoSave(boolean autosave, String token) throws Exception;

    /**
     * Returns how long the server has been running.
     *
     * @param token the caller's authentication token
     * @return the number of milliseconds elapsed since the server was constructed
     * @throws Exception if the token is invalid
     */
    long uptime(String token) throws Exception;

    /**
     * Returns the number of live threads in the JVM running the server.
     *
     * @param token the caller's authentication token
     * @return the current JVM-wide thread count
     * @throws Exception if the token is invalid
     */
    int jvmThreadCount(String token) throws Exception;

    /**
     * Returns when the container was last saved to disk.
     *
     * @param token the caller's authentication token
     * @return the epoch millisecond timestamp of the most recent save, or 0 if never saved
     * @throws Exception if the token is invalid
     */
    long lastSaveTime(String token) throws Exception;

    /**
     * Returns how many times the container has been saved to disk.
     *
     * @param token the caller's authentication token
     * @return the total number of completed saves
     * @throws Exception if the token is invalid
     */
    long saveCount(String token) throws Exception;

    /**
     * Returns the file path the container is persisted to and restored from.
     *
     * @param token the caller's authentication token
     * @return the configured container file path
     * @throws Exception if the token is invalid
     */
    String containerFilePath(String token) throws Exception;
}
