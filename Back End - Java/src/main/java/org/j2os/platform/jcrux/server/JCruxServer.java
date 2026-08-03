package org.j2os.platform.jcrux.server;

import org.j2os.platform.jcrux.share.JCruxObject;
import org.j2os.platform.jcrux.share.JCruxRemote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RMI-exported server side of JCrux: a shared, live object container.
 * <p>
 * Objects placed in the container are stored under a generated id and can then
 * be fetched, have their methods invoked, or have their fields read/written by
 * any client holding that id and a valid token. Every mutating call is
 * token-authenticated. If {@code autosave} is enabled, the entire container is
 * persisted to {@link #containerFile} after each mutating call; otherwise the
 * caller must invoke {@link #save(String)} explicitly. On construction, an
 * existing container file (if any) is automatically restored.
 * <p>
 * <b>Security notice:</b> {@link #create} instantiates any class reachable on this
 * server's classpath, and {@link #invoke}/{@link #setField}/{@link #getField} can call any
 * method or read/write any field (including non-public ones) on an object already stored in
 * the container. This is deliberate — the class is a generic, dynamic object container — but
 * it means this server must never be exposed to an untrusted network; treat network access to
 * it the same as you would treat shell access to the host it runs on. Every authentication
 * failure and every {@link #create}/{@link #invoke} call is logged (see {@link #LOGGER}) so an
 * audit trail exists even though no allow-list is enforced.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JCruxServer extends UnicastRemoteObject implements JCruxRemote {

    private static final Logger LOGGER = LoggerFactory.getLogger(JCruxServer.class);

    /**
     * The token that must be supplied by callers to authenticate against this container.
     */
    private final String token;

    /**
     * Path of the file this container is persisted to and restored from.
     */
    private final String containerFile;
    /**
     * Guards concurrent access to {@link #containerFile} during save/load.
     */
    private final Object fileLock = new Object();
    /**
     * The live objects held by this container, keyed by their generated id.
     */
    private final Map<String, Object> container = new ConcurrentHashMap<>();
    /**
     * Caches resolved {@link Class} instances by fully qualified class name, to avoid repeated {@code Class.forName} lookups.
     */
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    /**
     * Caches resolved {@link Constructor} instances by class name + parameter-type signature.
     */
    private final Map<String, Constructor<?>> constructorCache = new ConcurrentHashMap<>();
    /**
     * Caches resolved {@link Method} instances by class name + method name + parameter-type signature.
     */
    private final Map<String, Method> methodCache = new ConcurrentHashMap<>();
    /**
     * Caches resolved {@link Field} instances by class name + field name.
     */
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();
    /**
     * The time this server instance was constructed, in epoch milliseconds.
     */
    private final long startTime = System.currentTimeMillis();
    /**
     * The number of times this container has been saved to disk.
     */
    private final AtomicLong saveNumber = new AtomicLong(0);
    /**
     * Whether the container is automatically saved to disk after every mutating call.
     */
    private volatile boolean autosave;
    /**
     * The time of the most recent successful save, in epoch milliseconds, or 0 if never saved.
     */
    private volatile long lastSaveTimestamp = 0;

    /**
     * Creates and exports a new JCrux server for a single container.
     * <p>
     * If a file already exists at {@code containerFile}, its contents are
     * loaded into the container immediately.
     *
     * @param token         the token callers must supply to authenticate against this container
     * @param containerFile the file path this container is persisted to and restored from
     * @param autosave      whether to automatically save the container to disk after every mutating call
     * @throws RemoteException if exporting the object fails, or if restoring an existing container file fails
     */
    public JCruxServer(String token, String containerFile, boolean autosave) throws RemoteException {
        this.token = token;
        this.containerFile = containerFile;
        this.autosave = autosave;
        restoreIfFileExists();
    }

    /**
     * Builds a stable, order-preserving cache key fragment from a list of parameter types.
     *
     * @param types the parameter types to encode
     * @return a comma-separated string of the given types' fully qualified names
     */
    private static String typeKey(Class<?>[] types) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> type : types) {
            sb.append(type.getName()).append(',');
        }
        return sb.toString();
    }

    /**
     * Derives the runtime type of each element in {@code params}, in order.
     *
     * @param params the parameter values to inspect; no element may be null
     * @return the runtime {@link Class} of each element, in the same order as {@code params}
     * @throws IllegalArgumentException if any element of {@code params} is null, since its
     *                                  runtime type cannot then be inferred
     */
    private static Class<?>[] typesOf(Object[] params) {
        Class<?>[] types = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                throw new IllegalArgumentException(
                        "Parameter at index " + i + " is null — JCrux can't infer its type from a null value");
            }
            types[i] = params[i].getClass();
        }
        return types;
    }

    /**
     * Loads the container's contents from {@link #containerFile} if that file already exists.
     * Does nothing if the file is not present yet.
     *
     * @throws RemoteException if the file exists but cannot be read or deserialized
     */
    @SuppressWarnings("unchecked")
    private void restoreIfFileExists() throws RemoteException {
        File file = new File(containerFile);
        if (!file.exists()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            container.putAll((Map<String, Object>) ois.readObject());
            LOGGER.info("JCruxServer: restored {} object(s) from {}", container.size(), containerFile);
        } catch (Exception e) {
            throw new RemoteException("Failed to auto-restore container from " + containerFile, e);
        }
    }

    /**
     * Serializes the entire container to {@link #containerFile}, overwriting any existing file.
     *
     * @param token the caller's authentication token; must match this container's token
     * @throws Exception if the token is invalid, or if writing to disk fails
     */
    public void save(String token) throws Exception {
        checkToken(token);
        synchronized (fileLock) {
            try (FileOutputStream fos = new FileOutputStream(containerFile);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(container);
            }
            lastSaveTimestamp = System.currentTimeMillis();
            saveNumber.incrementAndGet();
        }
    }

    /**
     * Replaces the container's contents with what is currently stored in {@link #containerFile}.
     *
     * @param token the caller's authentication token; must match this container's token
     * @throws Exception if the token is invalid, or if reading from disk fails
     */
    @SuppressWarnings("unchecked")
    public void load(String token) throws Exception {
        checkToken(token);
        Map<String, Object> loaded;
        synchronized (fileLock) {
            try (FileInputStream fis = new FileInputStream(containerFile);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                loaded = (Map<String, Object>) ois.readObject();
            }
        }
        container.clear();
        container.putAll(loaded);
    }

    /**
     * Lists every object currently held in the container.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return one {@link JCruxObject} per stored instance, carrying its id and runtime class name
     * @throws Exception if the token is invalid
     */
    public List<JCruxObject> list(String token) throws Exception {
        checkToken(token);
        List<JCruxObject> instanceList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : container.entrySet()) {
            JCruxObject instance = new JCruxObject();
            instance.setObjectId(entry.getKey());
            instance.setObjectType(entry.getValue().getClass().getName());
            instanceList.add(instance);
        }
        return instanceList;
    }

    /**
     * Instantiates a new object of the given class using the supplied constructor
     * arguments, and stores it in the container under a freshly generated id.
     *
     * @param classAddress the fully qualified name of the class to instantiate
     * @param params       the constructor arguments to use; each must be non-null so its runtime type can be inferred
     * @param token        the caller's authentication token; must match this container's token
     * @return the generated id under which the new instance was stored
     * @throws Exception if the token is invalid, the class cannot be resolved, no matching constructor
     *                   exists, or construction fails
     */
    public String create(String classAddress, Object[] params, String token) throws Exception {
        checkToken(token);
        LOGGER.info("JCruxServer: create requested for class '{}'", classAddress);
        Class<?> clazz = resolveClass(classAddress);
        Constructor<?> constructor = resolveConstructor(clazz, params);
        Object instance = constructor.newInstance(params);
        String id = UUID.randomUUID().toString();
        container.put(id, instance);
        LOGGER.info("JCruxServer: created instance of '{}' with id {}", classAddress, id);
        autosaveIfEnabled(token);
        return id;
    }

    /**
     * Stores an already-constructed object in the container under a freshly generated id.
     *
     * @param object the object to store
     * @param token  the caller's authentication token; must match this container's token
     * @return the generated id under which the object was stored
     * @throws Exception if the token is invalid
     */
    public String put(Object object, String token) throws Exception {
        checkToken(token);
        String id = UUID.randomUUID().toString();
        container.put(id, object);
        autosaveIfEnabled(token);
        return id;
    }

    /**
     * Retrieves the object stored under the given id.
     *
     * @param id    the id of the object to retrieve
     * @param token the caller's authentication token; must match this container's token
     * @return the stored object
     * @throws Exception if the token is invalid, or if no object is stored under {@code id}
     */
    public Object get(String id, String token) throws Exception {
        checkToken(token);
        return requireInstance(id);
    }

    /**
     * Invokes a method, by name, on the object stored under the given id.
     * <p>
     * The call is synchronized on the target object so concurrent invocations
     * on the same instance do not interleave.
     *
     * @param id         the id of the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @param params     the arguments to pass to the method; each must be non-null so its runtime type can be inferred
     * @param token      the caller's authentication token; must match this container's token
     * @return the value returned by the invoked method
     * @throws Exception if the token is invalid, no object is stored under {@code id}, no matching
     *                   method exists, or the invocation itself throws
     */
    public Object invoke(String id, String methodName, Object[] params, String token) throws Exception {
        checkToken(token);
        Object object = requireInstance(id);
        LOGGER.info("JCruxServer: invoke requested — id={}, class='{}', method='{}'",
                id, object.getClass().getName(), methodName);
        Method method = resolveMethod(object.getClass(), methodName, params);
        Object result;
        synchronized (object) {
            result = method.invoke(object, params);
        }
        autosaveIfEnabled(token);
        return result;
    }

    /**
     * Removes the object stored under the given id, if present.
     *
     * @param id    the id of the object to remove
     * @param token the caller's authentication token; must match this container's token
     * @throws Exception if the token is invalid
     */
    public void remove(String id, String token) throws Exception {
        checkToken(token);
        container.remove(id);
        autosaveIfEnabled(token);
    }

    /**
     * Sets a field, by name, on the object stored under the given id.
     * <p>
     * The call is synchronized on the target object so concurrent field
     * writes on the same instance do not interleave.
     *
     * @param id        the id of the object to modify
     * @param attribute the name of the field to set
     * @param value     the value to assign to the field
     * @param token     the caller's authentication token; must match this container's token
     * @throws Exception if the token is invalid, no object is stored under {@code id}, no such
     *                   field exists, or the assignment fails
     */
    public void setField(String id, String attribute, Object value, String token) throws Exception {
        checkToken(token);
        Object object = requireInstance(id);
        Field field = resolveField(object.getClass(), attribute);
        synchronized (object) {
            field.set(object, value);
        }
        autosaveIfEnabled(token);
    }

    /**
     * Reads a field, by name, from the object stored under the given id.
     *
     * @param id        the id of the object to read from
     * @param attribute the name of the field to read
     * @param token     the caller's authentication token; must match this container's token
     * @return the current value of the field
     * @throws Exception if the token is invalid, no object is stored under {@code id}, or no such
     *                   field exists
     */
    public Object getField(String id, String attribute, String token) throws Exception {
        checkToken(token);
        Object object = requireInstance(id);
        Field field = resolveField(object.getClass(), attribute);
        synchronized (object) {
            return field.get(object);
        }
    }

    /**
     * Returns the amount of free memory in the JVM running this server.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the value of {@link Runtime#freeMemory()} at call time
     * @throws Exception if the token is invalid
     */
    public long freeMemory(String token) throws Exception {
        checkToken(token);
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Returns the total amount of memory allocated to the JVM running this server.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the value of {@link Runtime#totalMemory()} at call time
     * @throws Exception if the token is invalid
     */
    public long totalMemory(String token) throws Exception {
        checkToken(token);
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Enables or disables automatic saving of the container to disk after every mutating call.
     *
     * @param autosave the new autosave setting
     * @param token    the caller's authentication token; must match this container's token
     * @throws Exception if the token is invalid
     */
    public void setAutoSave(boolean autosave, String token) throws Exception {
        checkToken(token);
        this.autosave = autosave;
    }

    /**
     * Returns how long this server instance has been running.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the number of milliseconds elapsed since this server was constructed
     * @throws Exception if the token is invalid
     */
    public long uptime(String token) throws Exception {
        checkToken(token);
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Returns the number of live threads in the JVM running this server.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the current JVM-wide thread count, as reported by {@link ThreadMXBean}
     * @throws Exception if the token is invalid
     */
    public int jvmThreadCount(String token) throws Exception {
        checkToken(token);
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return threadMXBean.getThreadCount();
    }

    /**
     * Returns when this container was last saved to disk.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the epoch millisecond timestamp of the most recent save, or 0 if never saved
     * @throws Exception if the token is invalid
     */
    public long lastSaveTime(String token) throws Exception {
        checkToken(token);
        return lastSaveTimestamp;
    }

    // ---- Helper ----

    /**
     * Returns how many times this container has been saved to disk.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the total number of completed saves
     * @throws Exception if the token is invalid
     */
    public long saveCount(String token) throws Exception {
        checkToken(token);
        return saveNumber.get();
    }

    /**
     * Returns the file path this container is persisted to and restored from.
     *
     * @param token the caller's authentication token; must match this container's token
     * @return the configured container file path
     * @throws Exception if the token is invalid
     */
    public String containerFilePath(String token) throws Exception {
        checkToken(token);
        return containerFile;
    }

    /**
     * Saves the container to disk if autosave is currently enabled; otherwise does nothing.
     *
     * @param token the caller's authentication token, forwarded to {@link #save(String)} if a save is performed
     * @throws Exception if a save is performed and it fails
     */
    private void autosaveIfEnabled(String token) throws Exception {
        if (autosave) {
            save(token);
        }
    }

    // ---- Reflection Cache ----

    /**
     * Verifies that the given token matches this container's token, using a constant-time
     * comparison so the check does not leak timing information about how much of the
     * token matched. Every failed attempt is logged at {@code WARN} level so repeated
     * failures (e.g. a brute-force attempt) leave an audit trail.
     *
     * @param token the token to check
     * @throws Exception if the token is null, or does not match
     */
    private void checkToken(String token) throws Exception {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                this.token.getBytes(StandardCharsets.UTF_8))) {
            LOGGER.warn("JCruxServer: authentication failed (invalid or missing token)");
            throw new Exception("Invalid token");
        }
    }

    /**
     * Looks up the object stored under the given id.
     *
     * @param id the id to look up
     * @return the stored object
     * @throws NoSuchElementException if no object is stored under {@code id}
     */
    private Object requireInstance(String id) {
        Object object = container.get(id);
        if (object == null) throw new NoSuchElementException("No instance for id " + id);
        return object;
    }

    /**
     * Resolves a class by its fully qualified name, using {@link #classCache} to avoid
     * repeated {@link Class#forName(String)} calls for the same name.
     *
     * @param classAddress the fully qualified class name to resolve
     * @return the resolved {@link Class}
     * @throws ClassNotFoundException if no class with that name can be found
     */
    private Class<?> resolveClass(String classAddress) throws ClassNotFoundException {
        Class<?> cached = classCache.get(classAddress);
        if (cached != null) return cached;
        Class<?> clazz = Class.forName(classAddress);
        classCache.put(classAddress, clazz);
        return clazz;
    }

    /**
     * Resolves the declared constructor of {@code clazz} matching the runtime types of
     * {@code params}, using {@link #constructorCache} to avoid repeated reflective lookups
     * for the same class/parameter-type combination.
     *
     * @param clazz  the class to find a constructor on
     * @param params the constructor arguments whose runtime types determine the signature to match
     * @return the resolved {@link Constructor}
     * @throws NoSuchMethodException if no matching constructor is declared
     */
    private Constructor<?> resolveConstructor(Class<?> clazz, Object[] params) throws NoSuchMethodException {
        Class<?>[] types = typesOf(params);
        String key = clazz.getName() + "#" + typeKey(types);
        Constructor<?> cached = constructorCache.get(key);
        if (cached != null) return cached;
        Constructor<?> constructor = clazz.getDeclaredConstructor(types);
        constructorCache.put(key, constructor);
        return constructor;
    }

    /**
     * Resolves the declared method named {@code methodName} on {@code clazz} matching the
     * runtime types of {@code params}, using {@link #methodCache} to avoid repeated reflective
     * lookups for the same class/method-name/parameter-type combination. The resolved method
     * is made accessible so non-public methods can also be invoked.
     *
     * @param clazz      the class to find the method on
     * @param methodName the name of the method to resolve
     * @param params     the method arguments whose runtime types determine the signature to match
     * @return the resolved, accessible {@link Method}
     * @throws NoSuchMethodException if no matching method is declared
     */
    private Method resolveMethod(Class<?> clazz, String methodName, Object[] params) throws NoSuchMethodException {
        Class<?>[] types = typesOf(params);
        String key = clazz.getName() + "#" + methodName + "#" + typeKey(types);
        Method cached = methodCache.get(key);
        if (cached != null) return cached;
        Method method = clazz.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        methodCache.put(key, method);
        return method;
    }

    /**
     * Resolves the declared field named {@code attribute} on {@code clazz}, using
     * {@link #fieldCache} to avoid repeated reflective lookups for the same
     * class/field-name combination. The resolved field is made accessible so
     * non-public fields can also be read or written.
     *
     * @param clazz     the class to find the field on
     * @param attribute the name of the field to resolve
     * @return the resolved, accessible {@link Field}
     * @throws NoSuchFieldException if no field with that name is declared
     */
    private Field resolveField(Class<?> clazz, String attribute) throws NoSuchFieldException {
        String key = clazz.getName() + "#" + attribute;
        Field cached = fieldCache.get(key);
        if (cached != null) return cached;
        Field field = clazz.getDeclaredField(attribute);
        field.setAccessible(true);
        fieldCache.put(key, field);
        return field;
    }
}