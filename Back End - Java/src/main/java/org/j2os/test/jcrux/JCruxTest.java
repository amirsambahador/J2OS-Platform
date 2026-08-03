package org.j2os.test.jcrux;

import org.j2os.platform.jcrux.client.JCruxClient;
import org.j2os.platform.jcrux.server.JCruxServer;
import org.j2os.platform.jcrux.share.JCruxObject;
import org.j2os.examples.desktop.jcrux.share.Person;

import java.io.File;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.NoSuchElementException;

/**
 * Plain, dependency-free test suite for the {@code org.j2os.platform.jcrux} library
 * (no test framework such as JUnit is used). Run it directly with its {@link #main(String[])}
 * method; each test case reports PASS/FAIL to standard output and a summary is printed at the end.
 * <p>
 * The suite starts a real RMI registry on {@link #RMI_REGISTRY_PORT} and exercises two
 * live {@link JCruxServer} containers through {@link JCruxClient}, exactly as a real
 * client/server deployment would.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JCruxTest {

    /**
     * Port the test RMI registry is started on; distinct from the 1099 used by ServerExample.
     */
    private static final int RMI_REGISTRY_PORT = 10990;
    /**
     * Fully qualified class name of the example domain object used for create()/invoke() tests.
     */
    private static final String PERSON_CLASS = "org.j2os.examples.desktop.jcrux.share.Person";
    /**
     * Total number of test cases executed so far.
     */
    private static int totalTestCount = 0;
    /**
     * Number of test cases that failed so far.
     */
    private static int failedTestCount = 0;
    /**
     * Server side of container 1 (autosave enabled).
     */
    private static JCruxServer container1Server;

    /**
     * Server side of container 2 (autosave disabled).
     */
    private static JCruxServer container2Server;

    /**
     * Backing file for container 1, in the system temp directory.
     */
    private static File container1File;

    /**
     * Backing file for container 2, in the system temp directory.
     */
    private static File container2File;

    /**
     * Client connected to container 1 with the correct token.
     */
    private static JCruxClient client1;

    /**
     * Client connected to container 2 with the correct token.
     */
    private static JCruxClient client2;

    /**
     * The registry started for this test run, kept so it can be cleaned up afterward.
     */
    private static Registry registry;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        int exitCode = 0;
        try {
            setUp();

            testCreateReturnsUsableId();
            testGetReturnsStoredObject();
            testInvokeReturnsMethodResult();
            testInvokeWithArgumentReturnsExpectedResult();
            testSetFieldThenGetFieldRoundTrips();
            testListReflectsStoredObjects();
            testRemoveThenGetThrows();
            testPutStoresPreConstructedObject();
            testCreateWithNullParameterThrowsIllegalArgumentException();
            testWrongTokenThrowsInvalidTokenException();
            testAutosaveWritesFileAfterMutatingCall();
            testExplicitSaveThenReloadInNewInstanceRestoresData();
            testSetAutoSaveDisablesAutomaticSaving();
            testMemoryUptimeAndThreadCountReturnSaneValues();
            testContainerFilePathMatchesConfiguredPath();
        } catch (Exception e) {
            System.out.println("[FATAL] Test setup failed: " + e);
            e.printStackTrace();
            exitCode = 2;
        } finally {
            tearDown();
        }

        printSummary();
        if (exitCode == 0 && failedTestCount > 0) {
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    // ------------------------------------------------------------------
    // Setup / teardown
    // ------------------------------------------------------------------

    /**
     * Starts a private RMI registry, constructs and binds two example containers,
     * and connects one client to each.
     *
     * @throws Exception if the registry, either container, or either client fails to start
     */
    private static void setUp() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        // Build paths only — the files must not exist yet, otherwise JCruxServer would
        // try to restore from them on construction and fail on an empty/invalid file.
        container1File = new File(tempDir, "jcrux-test-container-1-" + System.nanoTime() + ".dat");
        container2File = new File(tempDir, "jcrux-test-container-2-" + System.nanoTime() + ".dat");
        container1File.deleteOnExit();
        container2File.deleteOnExit();

        registry = LocateRegistry.createRegistry(RMI_REGISTRY_PORT);

        container1Server = new JCruxServer("token1", container1File.getAbsolutePath(), true);
        container2Server = new JCruxServer("token2", container2File.getAbsolutePath(), false);

        Naming.rebind(registryUrl("test-container-1"), container1Server);
        Naming.rebind(registryUrl("test-container-2"), container2Server);

        client1 = new JCruxClient();
        client1.connect("localhost:" + RMI_REGISTRY_PORT, "test-container-1", "token1");

        client2 = new JCruxClient();
        client2.connect("localhost:" + RMI_REGISTRY_PORT, "test-container-2", "token2");
    }

    /**
     * Unbinds and unexports both test containers, and deletes their backing files.
     */
    private static void tearDown() {
        try {
            if (registry != null) {
                try {
                    Naming.unbind(registryUrl("test-container-1"));
                } catch (Exception ignored) {
                }
                try {
                    Naming.unbind(registryUrl("test-container-2"));
                } catch (Exception ignored) {
                }
            }
            if (container1Server != null) {
                UnicastRemoteObject.unexportObject(container1Server, true);
            }
            if (container2Server != null) {
                UnicastRemoteObject.unexportObject(container2Server, true);
            }
        } catch (Exception e) {
            System.out.println("[WARN] Cleanup encountered an issue: " + e);
        } finally {
            if (container1File != null) container1File.delete();
            if (container2File != null) container2File.delete();
        }
    }

    /**
     * Builds the RMI URL for a service name on the test registry.
     *
     * @param serviceName the name a container is (or will be) bound under
     * @return the full {@code rmi://host:port/name} style URL used by {@link Naming}
     */
    private static String registryUrl(String serviceName) {
        return "//localhost:" + RMI_REGISTRY_PORT + "/" + serviceName;
    }

    // ------------------------------------------------------------------
    // create / get
    // ------------------------------------------------------------------

    /**
     * Verifies create() returns a non-null, non-empty id.
     */
    private static void testCreateReturnsUsableId() {
        String testName = "create() returns a usable, non-empty id";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            assertTrue(testName, id != null && !id.isEmpty());
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies get() returns an object with the same field values passed to create().
     */
    private static void testGetReturnsStoredObject() {
        String testName = "get() returns the object created with the given constructor arguments";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            Person person = (Person) client1.get(id);

            assertTrue(testName, "Amirsam".equals(person.getName()) && Integer.valueOf(30).equals(person.getAge()));
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // invoke
    // ------------------------------------------------------------------

    /**
     * Verifies invoke() with no arguments returns the expected method result.
     */
    private static void testInvokeReturnsMethodResult() {
        String testName = "invoke() with no arguments returns the expected method result";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            Object result = client1.invoke(id, "greet");

            assertTrue(testName, "Hello, my name is Amirsam and I'm 30 years old.".equals(result));
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies invoke() with an argument returns the expected method result.
     */
    private static void testInvokeWithArgumentReturnsExpectedResult() {
        String testName = "invoke() with an argument returns the expected method result";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            Object result = client1.invoke(id, "introduceAs", "Sam");

            assertTrue(testName, "You can call me Sam (Amirsam)".equals(result));
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // setField / getField
    // ------------------------------------------------------------------

    /**
     * Verifies a field set through setField() is visible through getField() and get().
     */
    private static void testSetFieldThenGetFieldRoundTrips() {
        String testName = "setField() followed by getField() round-trips the new value";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            client1.setField(id, "age", 31);

            Object age = client1.getField(id, "age");
            Person person = (Person) client1.get(id);

            assertTrue(testName, Integer.valueOf(31).equals(age) && Integer.valueOf(31).equals(person.getAge()));
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // list / remove / put
    // ------------------------------------------------------------------

    /**
     * Verifies list() reports the id and runtime type of a stored object.
     */
    private static void testListReflectsStoredObjects() {
        String testName = "list() reports the id and runtime type of stored objects";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);

            boolean found = false;
            for (JCruxObject entry : client1.list()) {
                if (id.equals(entry.getObjectId())) {
                    found = Person.class.getName().equals(entry.getObjectType());
                    break;
                }
            }

            assertTrue(testName, found);
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies that after remove(), get() on the same id throws NoSuchElementException.
     */
    private static void testRemoveThenGetThrows() {
        String testName = "get() throws NoSuchElementException after remove()";
        try {
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            client1.remove(id);

            try {
                client1.get(id);
                fail(testName + " [expected NoSuchElementException]");
            } catch (NoSuchElementException expected) {
                pass(testName);
            }
        } catch (Exception e) {
            fail(testName + " [unexpected exception during setup: " + e + "]");
        }
    }

    /**
     * Verifies put() stores an already-constructed object and get() returns the same data.
     */
    private static void testPutStoresPreConstructedObject() {
        String testName = "put() stores a pre-constructed object retrievable via get()";
        try {
            Person person = new Person("Mohammad", 28);
            String id = client1.put(person);
            Person retrieved = (Person) client1.get(id);

            assertTrue(testName, "Mohammad".equals(retrieved.getName()) && Integer.valueOf(28).equals(retrieved.getAge()));
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // Validation / error handling
    // ------------------------------------------------------------------

    /**
     * Verifies create() with a null constructor argument throws IllegalArgumentException.
     */
    private static void testCreateWithNullParameterThrowsIllegalArgumentException() {
        String testName = "create() with a null constructor argument throws IllegalArgumentException";
        try {
            client1.create(PERSON_CLASS, "Amirsam", null);
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        } catch (Exception e) {
            fail(testName + " [expected IllegalArgumentException but got " + e + "]");
        }
    }

    /**
     * Verifies a client connected with the wrong token receives an "Invalid token" failure.
     */
    private static void testWrongTokenThrowsInvalidTokenException() {
        String testName = "Calls made with the wrong token fail with an invalid-token error";
        try {
            JCruxClient wrongTokenClient = new JCruxClient();
            wrongTokenClient.connect("localhost:" + RMI_REGISTRY_PORT, "test-container-1", "not-the-real-token");

            try {
                wrongTokenClient.list();
                fail(testName + " [expected an exception]");
            } catch (Exception expected) {
                assertTrue(testName, expected.getMessage() != null && expected.getMessage().contains("Invalid token"));
            }
        } catch (Exception e) {
            fail(testName + " [unexpected exception during setup: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // Persistence: autosave / save / load
    // ------------------------------------------------------------------

    /**
     * Verifies that with autosave enabled, the backing file is written after a mutating call.
     */
    private static void testAutosaveWritesFileAfterMutatingCall() {
        String testName = "With autosave enabled, the backing file is written after a mutating call";
        try {
            long saveCountBefore = client1.saveCount();
            String id = client1.create(PERSON_CLASS, "Amirsam", 30);
            long saveCountAfter = client1.saveCount();

            assertTrue(testName, container1File.exists() && saveCountAfter > saveCountBefore);
            client1.remove(id);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies that explicitly saving container 2 (autosave disabled) and then constructing
     * a fresh, unbound {@link JCruxServer} pointed at the same file restores the same data.
     */
    private static void testExplicitSaveThenReloadInNewInstanceRestoresData() {
        String testName = "Explicit save() followed by restoring a new instance from the same file preserves data";
        String id = null;
        try {
            id = client2.create(PERSON_CLASS, "Reloaded", 40);
            client2.save();

            // Constructing a new JCruxServer against the same file triggers restoreIfFileExists().
            JCruxServer reloadedServer = new JCruxServer("reload-token", container2File.getAbsolutePath(), false);
            try {
                Object reloadedPerson = reloadedServer.get(id, "reload-token");
                assertTrue(testName, reloadedPerson instanceof Person && "Reloaded".equals(((Person) reloadedPerson).getName()));
            } finally {
                UnicastRemoteObject.unexportObject(reloadedServer, true);
            }
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            try {
                if (id != null) client2.remove(id);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that disabling autosave via setAutoSave(false) stops automatic saving on mutating calls.
     */
    private static void testSetAutoSaveDisablesAutomaticSaving() {
        String testName = "setAutoSave(false) stops automatic saving on subsequent mutating calls";
        String id = null;
        try {
            client1.setAutoSave(false);
            long saveCountBefore = client1.saveCount();
            id = client1.create(PERSON_CLASS, "NoAutosave", 20);
            long saveCountAfter = client1.saveCount();

            assertTrue(testName, saveCountAfter == saveCountBefore);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            try {
                if (id != null) client1.remove(id);
                // Restore autosave for any later tests that rely on it.
                client1.setAutoSave(true);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /**
     * Verifies freeMemory(), totalMemory(), uptime(), and jvmThreadCount() return sane, positive values.
     */
    private static void testMemoryUptimeAndThreadCountReturnSaneValues() {
        String testName = "freeMemory(), totalMemory(), uptime(), and jvmThreadCount() return sane values";
        try {
            long freeMemory = client1.freeMemory();
            long totalMemory = client1.totalMemory();
            long uptime = client1.uptime();
            int threadCount = client1.jvmThreadCount();

            assertTrue(testName, freeMemory > 0 && totalMemory > 0 && uptime >= 0 && threadCount > 0);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies containerFilePath() returns the exact path the container was constructed with.
     */
    private static void testContainerFilePathMatchesConfiguredPath() {
        String testName = "containerFilePath() returns the configured container file path";
        try {
            String path1 = client1.containerFilePath();
            String path2 = client2.containerFilePath();

            assertTrue(testName,
                    container1File.getAbsolutePath().equals(path1) && container2File.getAbsolutePath().equals(path2));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (no external test framework)
    // ------------------------------------------------------------------

    /**
     * Records a passing test case if {@code condition} is true, otherwise records a failure.
     *
     * @param testName  the name of the test case, printed in the report
     * @param condition the condition that must be true for the test to pass
     */
    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        totalTestCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void fail(String testName) {
        totalTestCount++;
        failedTestCount++;
        System.out.println("[FAIL] " + testName);
    }

    /**
     * Prints a final pass/fail summary of the whole suite.
     */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}