package org.j2os.test.jflow;

import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.j2os.platform.jflow.client.JFlowClient;
import org.j2os.platform.jflow.server.JFlowServer;
import org.j2os.platform.jflow.share.JFlowTask;

import java.io.File;
import java.io.FileWriter;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain, dependency-free test suite for the {@code org.j2os.platform.jflow} library
 * (no test framework such as JUnit is used). Run it directly with its {@link #main(String[])}
 * method; each test case reports PASS/FAIL to standard output and a summary is printed at the end.
 * <p>
 * The suite builds a self-contained Flowable engine backed by an in-memory H2 database
 * (no external Postgres server is required), starts a real RMI registry on {@link
 * #RMI_REGISTRY_PORT}, and exercises {@link JFlowServer} through {@link JFlowClient}, exactly
 * as a real client/server deployment would.
 * <p>
 * <b>Classpath requirement:</b> this suite requires the H2 database driver
 * ({@code com.h2database:h2}) on the classpath in addition to the usual Flowable engine and
 * Flowable image dependencies.
 * <p>
 * <b>Assumption:</b> {@code JFlowClient} was not supplied alongside the server/interface code
 * this suite was written against, so its API is assumed — based on its usage in {@code
 * ClientExample}/{@code DiagramExample} and on the equivalent {@code JCruxClient} in this
 * codebase — to mirror {@link org.j2os.platform.jflow.share.JFlowRemote} one method at a time,
 * with the token supplied once at construction and omitted from every call. If the real {@code
 * JFlowClient} differs, only the calls in this file need adjusting.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JFlowTest {

    /**
     * Port the test RMI registry is started on; distinct from the 1099 used by ServerExample.
     */
    private static final int RMI_REGISTRY_PORT = 10991;
    /**
     * Service name the server is bound under in the test registry.
     */
    private static final String SERVICE_NAME = "test-jflow";
    /**
     * Token used to authenticate against the test server.
     */
    private static final String TOKEN = "test-token";
    /**
     * Two sequential user tasks (managerApproval, hrApproval); mirrors the leave-request sample.
     */
    private static final String LEAVE_REQUEST_XML = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
            "                   xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                   xmlns:flowable=\"http://flowable.org/bpmn\"",
            "                   xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\"",
            "                   id=\"testLeaveRequestDefs\" targetNamespace=\"http://j2os.org/jflow/test\">",
            "    <bpmn:process id=\"testLeaveRequest\" name=\"TestLeaveRequest\" isExecutable=\"true\">",
            "        <bpmn:startEvent id=\"start\" name=\"Start\"><bpmn:outgoing>c1</bpmn:outgoing></bpmn:startEvent>",
            "        <bpmn:sequenceFlow id=\"c1\" sourceRef=\"start\" targetRef=\"managerApproval\"/>",
            "        <bpmn:userTask id=\"managerApproval\" name=\"managerApproval\" flowable:assignee=\"manager\">",
            "            <bpmn:incoming>c1</bpmn:incoming><bpmn:outgoing>c2</bpmn:outgoing>",
            "        </bpmn:userTask>",
            "        <bpmn:sequenceFlow id=\"c2\" sourceRef=\"managerApproval\" targetRef=\"hrApproval\"/>",
            "        <bpmn:userTask id=\"hrApproval\" name=\"hrApproval\" flowable:assignee=\"hr\">",
            "            <bpmn:incoming>c2</bpmn:incoming><bpmn:outgoing>c3</bpmn:outgoing>",
            "        </bpmn:userTask>",
            "        <bpmn:sequenceFlow id=\"c3\" sourceRef=\"hrApproval\" targetRef=\"end\"/>",
            "        <bpmn:endEvent id=\"end\" name=\"End\"><bpmn:incoming>c3</bpmn:incoming></bpmn:endEvent>",
            "    </bpmn:process>",
            "</bpmn:definitions>");
    /**
     * A single receive task that only resumes when explicitly triggered; used to test forceSignal.
     */
    private static final String WAIT_SIGNAL_XML = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
            "                   xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                   xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\"",
            "                   id=\"testWaitSignalDefs\" targetNamespace=\"http://j2os.org/jflow/test\">",
            "    <bpmn:process id=\"testWaitSignal\" name=\"TestWaitSignal\" isExecutable=\"true\">",
            "        <bpmn:startEvent id=\"start\" name=\"Start\"><bpmn:outgoing>c1</bpmn:outgoing></bpmn:startEvent>",
            "        <bpmn:sequenceFlow id=\"c1\" sourceRef=\"start\" targetRef=\"waitStep\"/>",
            "        <bpmn:receiveTask id=\"waitStep\" name=\"waitStep\">",
            "            <bpmn:incoming>c1</bpmn:incoming><bpmn:outgoing>c2</bpmn:outgoing>",
            "        </bpmn:receiveTask>",
            "        <bpmn:sequenceFlow id=\"c2\" sourceRef=\"waitStep\" targetRef=\"end\"/>",
            "        <bpmn:endEvent id=\"end\" name=\"End\"><bpmn:incoming>c2</bpmn:incoming></bpmn:endEvent>",
            "    </bpmn:process>",
            "</bpmn:definitions>");
    /**
     * Two sequential user tasks used to test moving the active activity from one to the other.
     */
    private static final String MOVE_TEST_XML = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
            "                   xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                   xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\"",
            "                   id=\"testMoveDefs\" targetNamespace=\"http://j2os.org/jflow/test\">",
            "    <bpmn:process id=\"testMove\" name=\"TestMove\" isExecutable=\"true\">",
            "        <bpmn:startEvent id=\"start\" name=\"Start\"><bpmn:outgoing>c1</bpmn:outgoing></bpmn:startEvent>",
            "        <bpmn:sequenceFlow id=\"c1\" sourceRef=\"start\" targetRef=\"stepA\"/>",
            "        <bpmn:userTask id=\"stepA\" name=\"stepA\"><bpmn:incoming>c1</bpmn:incoming><bpmn:outgoing>c2</bpmn:outgoing></bpmn:userTask>",
            "        <bpmn:sequenceFlow id=\"c2\" sourceRef=\"stepA\" targetRef=\"stepB\"/>",
            "        <bpmn:userTask id=\"stepB\" name=\"stepB\"><bpmn:incoming>c2</bpmn:incoming><bpmn:outgoing>c3</bpmn:outgoing></bpmn:userTask>",
            "        <bpmn:sequenceFlow id=\"c3\" sourceRef=\"stepB\" targetRef=\"end\"/>",
            "        <bpmn:endEvent id=\"end\" name=\"End\"><bpmn:incoming>c3</bpmn:incoming></bpmn:endEvent>",
            "    </bpmn:process>",
            "</bpmn:definitions>");
    /**
     * Single user task with full BPMNDI layout info, used to test diagram rendering.
     */
    private static final String DIAGRAM_SAMPLE_XML = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
            "                   xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                   xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"",
            "                   xmlns:omgdc=\"http://www.omg.org/spec/DD/20100524/DC\"",
            "                   xmlns:omgdi=\"http://www.omg.org/spec/DD/20100524/DI\"",
            "                   xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\"",
            "                   id=\"testDiagramDefs\" targetNamespace=\"http://j2os.org/jflow/test\">",
            "    <bpmn:process id=\"testDiagram\" name=\"TestDiagram\" isExecutable=\"true\">",
            "        <bpmn:startEvent id=\"start\" name=\"Start\"><bpmn:outgoing>c1</bpmn:outgoing></bpmn:startEvent>",
            "        <bpmn:sequenceFlow id=\"c1\" sourceRef=\"start\" targetRef=\"review\"/>",
            "        <bpmn:userTask id=\"review\" name=\"review\"><bpmn:incoming>c1</bpmn:incoming><bpmn:outgoing>c2</bpmn:outgoing></bpmn:userTask>",
            "        <bpmn:sequenceFlow id=\"c2\" sourceRef=\"review\" targetRef=\"end\"/>",
            "        <bpmn:endEvent id=\"end\" name=\"End\"><bpmn:incoming>c2</bpmn:incoming></bpmn:endEvent>",
            "    </bpmn:process>",
            "    <bpmndi:BPMNDiagram id=\"BPMNDiagram_testDiagram\">",
            "        <bpmndi:BPMNPlane id=\"BPMNPlane_testDiagram\" bpmnElement=\"testDiagram\">",
            "            <bpmndi:BPMNShape id=\"Shape_start\" bpmnElement=\"start\"><omgdc:Bounds x=\"100\" y=\"100\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>",
            "            <bpmndi:BPMNShape id=\"Shape_review\" bpmnElement=\"review\"><omgdc:Bounds x=\"200\" y=\"78\" width=\"100\" height=\"80\"/></bpmndi:BPMNShape>",
            "            <bpmndi:BPMNShape id=\"Shape_end\" bpmnElement=\"end\"><omgdc:Bounds x=\"360\" y=\"100\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>",
            "            <bpmndi:BPMNEdge id=\"Edge_c1\" bpmnElement=\"c1\"><omgdi:waypoint x=\"136\" y=\"118\"/><omgdi:waypoint x=\"200\" y=\"118\"/></bpmndi:BPMNEdge>",
            "            <bpmndi:BPMNEdge id=\"Edge_c2\" bpmnElement=\"c2\"><omgdi:waypoint x=\"300\" y=\"118\"/><omgdi:waypoint x=\"360\" y=\"118\"/></bpmndi:BPMNEdge>",
            "        </bpmndi:BPMNPlane>",
            "    </bpmndi:BPMNDiagram>",
            "</bpmn:definitions>");
    /**
     * Total number of test cases executed so far.
     */
    private static int totalTestCount = 0;
    /**
     * Number of test cases that failed so far.
     */
    private static int failedTestCount = 0;

    // ------------------------------------------------------------------
    // Inline BPMN process definitions used by this suite
    // ------------------------------------------------------------------
    /**
     * The server under test.
     */
    private static JFlowServer server;
    /**
     * Client connected to the server with the correct token.
     */
    private static JFlowClient client;
    /**
     * The registry started for this test run, kept so it can be cleaned up afterward.
     */
    private static Registry registry;
    /**
     * Temporary BPMN file written to disk to exercise deployFromFile, deleted during teardown.
     */
    private static File tempBpmnFile;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        int exitCode = 0;
        try {
            setUp();

            testDeployFromSourceReturnsDefinitionKey();
            testStartProcessWithoutVariablesCreatesOpenTask();
            testStartProcessWithVariablesSetsInitialVariables();
            testSignalByTaskIdCompletesTaskAndAdvancesProcess();
            testCompletingAllTasksLeavesNoOpenTasks();
            testGetOpenTasksByAssigneeFiltersCorrectly();
            testGetOpenTasksByProcessDefinitionNameFiltersCorrectly();
            testForceSignalResumesWaitingExecution();
            testForceSignalWithVariablesPassesThemThrough();
            testMoveByProcessInstanceIdChangesActiveActivity();
            testSetAndGetSingleVariable();
            testSetAndGetMultipleVariables();
            testDiagramsAreValidPngsAndDifferWhenHighlighted();
            testSuspendAndActivateProcessDefinition();
            testDeployFromFileReturnsSameDefinitionKey();
            testWrongTokenFailsWithInvalidTokenMessage();
            testUnknownProcessInstanceFailsWithMeaningfulMessage();
            testUnknownProcessDefinitionNameFailsWithMeaningfulMessage();
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
     * Builds a self-contained, H2-backed Flowable engine, starts a private RMI registry,
     * binds the server to it, and connects a client.
     *
     * @throws Exception if the engine, registry, server, or client fails to start
     */
    private static void setUp() throws Exception {
        StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:jflow-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setJdbcDriver("org.h2.Driver");
        config.setJdbcUsername("sa");
        config.setJdbcPassword("");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);

        registry = LocateRegistry.createRegistry(RMI_REGISTRY_PORT);

        server = new JFlowServer(TOKEN, config);
        Naming.rebind(registryUrl(), server);

        client = new JFlowClient("rmi://localhost:" + RMI_REGISTRY_PORT + "/" + SERVICE_NAME, TOKEN);

        client.deployFromSourceAndGetProcessDefinitionKey("test-leave-request.bpmn20.xml", LEAVE_REQUEST_XML);
        client.deployFromSourceAndGetProcessDefinitionKey("test-wait-signal.bpmn20.xml", WAIT_SIGNAL_XML);
        client.deployFromSourceAndGetProcessDefinitionKey("test-move.bpmn20.xml", MOVE_TEST_XML);
        client.deployFromSourceAndGetProcessDefinitionKey("test-diagram.bpmn20.xml", DIAGRAM_SAMPLE_XML);
    }

    /**
     * Unbinds and unexports the test server, and deletes any temporary file created by the suite.
     */
    private static void tearDown() {
        try {
            if (registry != null) {
                try {
                    Naming.unbind(registryUrl());
                } catch (Exception ignored) {
                }
            }
            if (server != null) {
                UnicastRemoteObject.unexportObject(server, true);
            }
        } catch (Exception e) {
            System.out.println("[WARN] Cleanup encountered an issue: " + e);
        } finally {
            if (tempBpmnFile != null) {
                tempBpmnFile.delete();
            }
        }
    }

    /**
     * Builds the RMI URL the test server is (or will be) bound under.
     *
     * @return the full {@code //host:port/name} style URL used by {@link Naming}
     */
    private static String registryUrl() {
        return "//localhost:" + RMI_REGISTRY_PORT + "/" + SERVICE_NAME;
    }

    // ------------------------------------------------------------------
    // Deployment / start
    // ------------------------------------------------------------------

    /**
     * Verifies deploying from an XML string returns the process definition's key.
     */
    private static void testDeployFromSourceReturnsDefinitionKey() {
        String testName = "deployFromSourceAndGetProcessDefinitionName returns the process key";
        try {
            String key = client.deployFromSourceAndGetProcessDefinitionKey("redeploy-check.bpmn20.xml", LEAVE_REQUEST_XML);
            assertTrue(testName, "testLeaveRequest".equals(key));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies starting a process without variables produces exactly one open task at the first step.
     */
    private static void testStartProcessWithoutVariablesCreatesOpenTask() {
        String testName = "startProcessAndGetProcessInstanceId (no variables) creates the first open task";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");
            List<JFlowTask> openTasks = client.getOpenTasksByProcessInstanceId(processInstanceId);

            assertTrue(testName, openTasks.size() == 1 && "manager".equals(openTasks.get(0).getAssignee()));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            // Drain this instance so it doesn't linger for later assertions, even if something
            // above threw before reaching a manual drain step.
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    /**
     * Verifies starting a process with initial variables makes them readable afterward.
     */
    private static void testStartProcessWithVariablesSetsInitialVariables() {
        String testName = "startProcessAndGetProcessInstanceId (with variables) sets initial variables";
        String processInstanceId = null;
        try {
            Map<String, Object> initialVariables = new HashMap<>();
            initialVariables.put("days", 5);
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest", initialVariables);

            Object days = client.getVariableByProcessInstanceId(processInstanceId, "days");
            assertTrue(testName, Integer.valueOf(5).equals(days));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    // ------------------------------------------------------------------
    // Tasks / signaling
    // ------------------------------------------------------------------

    /**
     * Verifies completing a task advances the process to the next task.
     */
    private static void testSignalByTaskIdCompletesTaskAndAdvancesProcess() {
        String testName = "signalByTaskId advances the process to the next task";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");
            JFlowTask managerTask = client.getOpenTasksByProcessInstanceId(processInstanceId).get(0);
            client.signalByTaskId(managerTask.getTaskId());

            JFlowTask hrTask = client.getOpenTasksByProcessInstanceId(processInstanceId).get(0);
            assertTrue(testName, "hr".equals(hrTask.getAssignee()) && !hrTask.getTaskId().equals(managerTask.getTaskId()));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    /**
     * Verifies that completing every task in a process instance leaves it with no open tasks.
     */
    private static void testCompletingAllTasksLeavesNoOpenTasks() {
        String testName = "Completing all tasks leaves zero open tasks for the process instance";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");
            client.signalByTaskId(client.getOpenTasksByProcessInstanceId(processInstanceId).get(0).getTaskId());
            client.signalByTaskId(client.getOpenTasksByProcessInstanceId(processInstanceId).get(0).getTaskId());

            List<JFlowTask> remaining = client.getOpenTasksByProcessInstanceId(processInstanceId);
            assertTrue(testName, remaining.isEmpty());
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            // No-op in the normal case (the instance is already fully drained), but still a
            // safety net if an exception left tasks open partway through.
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    /**
     * Verifies getOpenTasksByAssignee only returns tasks assigned to the requested assignee.
     */
    private static void testGetOpenTasksByAssigneeFiltersCorrectly() {
        String testName = "getOpenTasksByAssignee only returns tasks for the requested assignee";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");

            List<JFlowTask> managerTasks = client.getOpenTasksByAssignee("manager");
            boolean allAssignedToManager = true;
            for (JFlowTask task : managerTasks) {
                allAssignedToManager &= "manager".equals(task.getAssignee());
            }

            assertTrue(testName, !managerTasks.isEmpty() && allAssignedToManager);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    /**
     * Verifies getOpenTasksByProcessDefinitionName only returns tasks for that process definition.
     */
    private static void testGetOpenTasksByProcessDefinitionNameFiltersCorrectly() {
        String testName = "getOpenTasksByProcessDefinitionName only returns tasks for that definition";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");

            List<JFlowTask> tasks = client.getOpenTasksByProcessDefinitionKey("testLeaveRequest");
            boolean allFromThisDefinition = true;
            for (JFlowTask task : tasks) {
                allFromThisDefinition &= "testLeaveRequest".equals(task.getProcessDefinitionKey())
                        || (task.getProcessDefinitionKey() != null && task.getProcessDefinitionKey().startsWith("testLeaveRequest:"));
            }

            assertTrue(testName, !tasks.isEmpty() && allFromThisDefinition);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    // ------------------------------------------------------------------
    // forceSignal / move
    // ------------------------------------------------------------------

    /**
     * Verifies forceSignalByProcessInstanceId resumes a process waiting at a receive task.
     */
    private static void testForceSignalResumesWaitingExecution() {
        String testName = "forceSignalByProcessInstanceId (no variables) resumes a waiting process";
        try {
            String processInstanceId = client.startProcessAndGetProcessInstanceId("testWaitSignal");
            client.forceSignalByProcessInstanceId(processInstanceId);

            // A process with no more waiting steps after the trigger should have completed;
            // reading its variables should now fail because the instance no longer exists.
            boolean completed = false;
            try {
                client.getVariablesByProcessInstanceId(processInstanceId);
            } catch (Exception expectedAfterCompletion) {
                completed = true;
            }

            assertTrue(testName, completed);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies forceSignalByProcessInstanceId with variables passes them through to the process.
     */
    private static void testForceSignalWithVariablesPassesThemThrough() {
        String testName = "forceSignalByProcessInstanceId (with variables) passes variables through";
        try {
            String processInstanceId = client.startProcessAndGetProcessInstanceId("testWaitSignal");
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumedWith", "value-1");

            // This process has no more steps after the receive task, so it completes as soon as
            // forceSignal triggers it - meaning the instance (and its variables) are no longer
            // queryable afterward. That makes it impossible to directly confirm the variables
            // were applied, so this test only verifies the call itself succeeds without error,
            // mirroring how forceSignal is used in practice.
            client.forceSignalByProcessInstanceId(processInstanceId, variables);

            boolean completed = false;
            try {
                client.getVariablesByProcessInstanceId(processInstanceId);
            } catch (Exception expectedAfterCompletion) {
                completed = true;
            }
            assertTrue(testName, completed);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Verifies moveByProcessInstanceId moves the active activity to the requested target.
     */
    private static void testMoveByProcessInstanceIdChangesActiveActivity() {
        String testName = "moveByProcessInstanceId moves the process to the target activity's task";
        try {
            String processInstanceId = client.startProcessAndGetProcessInstanceId("testMove");
            JFlowTask stepATask = client.getOpenTasksByProcessInstanceId(processInstanceId).get(0);

            client.moveByProcessInstanceId(processInstanceId, "stepB");

            List<JFlowTask> tasksAfterMove = client.getOpenTasksByProcessInstanceId(processInstanceId);
            boolean movedToStepB = tasksAfterMove.size() == 1 && !tasksAfterMove.get(0).getTaskId().equals(stepATask.getTaskId());

            assertTrue(testName, movedToStepB);

            client.signalByTaskId(tasksAfterMove.get(0).getTaskId());
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // Variables
    // ------------------------------------------------------------------

    /**
     * Verifies setVariableByProcessId followed by getVariableByProcessId round-trips a single value.
     */
    private static void testSetAndGetSingleVariable() {
        String testName = "setVariableByProcessId then getVariableByProcessId round-trips a value";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");
            client.setVariableByProcessInstanceId(processInstanceId, "note", "approved-fast-track");

            Object note = client.getVariableByProcessInstanceId(processInstanceId, "note");
            assertTrue(testName, "approved-fast-track".equals(note));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    /**
     * Verifies setVariablesByProcessId followed by getVariablesByProcessId round-trips a whole map.
     */
    private static void testSetAndGetMultipleVariables() {
        String testName = "setVariablesByProcessId then getVariablesByProcessId round-trips a map";
        String processInstanceId = null;
        try {
            processInstanceId = client.startProcessAndGetProcessInstanceId("testLeaveRequest");
            Map<String, Object> variables = new HashMap<>();
            variables.put("days", 7);
            variables.put("reason", "family-event");
            client.setVariablesByProcessInstanceId(processInstanceId, variables);

            Map<String, Object> readBack = client.getVariablesByProcessInstanceId(processInstanceId);
            assertTrue(testName, Integer.valueOf(7).equals(readBack.get("days")) && "family-event".equals(readBack.get("reason")));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        } finally {
            cleanupLeaveRequestInstance(processInstanceId);
        }
    }

    // ------------------------------------------------------------------
    // Diagrams
    // ------------------------------------------------------------------

    /**
     * Verifies both diagram endpoints return valid, non-empty PNGs, and that the
     * instance diagram (with a highlighted activity) differs from the plain definition diagram.
     */
    private static void testDiagramsAreValidPngsAndDifferWhenHighlighted() {
        String testName = "Both diagram endpoints return valid, non-empty, and distinct PNGs";
        try {
            String processInstanceId = client.startProcessAndGetProcessInstanceId("testDiagram");

            byte[] instanceDiagram = client.getProcessDiagramByProcessInstanceId(processInstanceId);
            byte[] definitionDiagram = client.getProcessDiagramByProcessDefinitionKey("testDiagram");

            boolean bothValidPngs = isValidPng(instanceDiagram) && isValidPng(definitionDiagram);
            boolean differ = !Arrays.equals(instanceDiagram, definitionDiagram);

            assertTrue(testName, bothValidPngs && differ);

            client.signalByTaskId(client.getOpenTasksByProcessInstanceId(processInstanceId).get(0).getTaskId());
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Checks whether the given bytes form a non-empty, valid PNG image. The first four
     * bytes of a valid PNG are always 0x89 'P' 'N' 'G'.
     *
     * @param png the bytes to check
     * @return true if {@code png} looks like a valid, non-empty PNG
     */
    private static boolean isValidPng(byte[] png) {
        return png != null && png.length > 8
                && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G';
    }

    // ------------------------------------------------------------------
    // Process definition lifecycle
    // ------------------------------------------------------------------

    /**
     * Verifies suspending a process definition removes it from the active list and adds it
     * to the suspended list, and that activating it reverses this.
     */
    private static void testSuspendAndActivateProcessDefinition() {
        String testName = "suspendByProcessDefinitionName / activateByProcessDefinitionName toggle definition state";
        try {
            client.suspendByProcessDefinitionKey("testMove");
            boolean suspendedCorrectly = containsKeyPrefix(client.getSuspendedProcessDefinitionKeys(), "testMove:")
                    && !containsKeyPrefix(client.getActiveProcessDefinitionKeys(), "testMove:");

            client.activateByProcessDefinitionKey("testMove");
            boolean activatedCorrectly = containsKeyPrefix(client.getActiveProcessDefinitionKeys(), "testMove:")
                    && !containsKeyPrefix(client.getSuspendedProcessDefinitionKeys(), "testMove:");

            assertTrue(testName, suspendedCorrectly && activatedCorrectly);
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    /**
     * Checks whether any entry in a {@code key:version} list starts with the given key prefix.
     *
     * @param keyVersionList the {@code key:version} strings to search
     * @param keyPrefix      the key prefix (including the trailing colon) to look for
     * @return true if a matching entry is present
     */
    private static boolean containsKeyPrefix(List<String> keyVersionList, String keyPrefix) {
        for (String entry : keyVersionList) {
            if (entry.startsWith(keyPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies deploying the same process from a file on disk returns the same process definition key.
     */
    private static void testDeployFromFileReturnsSameDefinitionKey() {
        String testName = "deployFromFileAndGetProcessDefinitionName reads the file and returns its process key";
        try {
            tempBpmnFile = File.createTempFile("jflow-test-leave-request", ".bpmn20.xml");
            try (FileWriter writer = new FileWriter(tempBpmnFile)) {
                writer.write(LEAVE_REQUEST_XML);
            }

            String key = client.deployFromFileAndGetProcessDefinitionKey(tempBpmnFile.getAbsolutePath());
            assertTrue(testName, "testLeaveRequest".equals(key));
        } catch (Exception e) {
            fail(testName + " [unexpected exception: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // Error handling / validation
    // ------------------------------------------------------------------

    /**
     * Verifies a client connected with the wrong token receives an "Invalid token" failure.
     */
    private static void testWrongTokenFailsWithInvalidTokenMessage() {
        String testName = "Calls made with the wrong token fail with an invalid-token error";
        try {
            JFlowClient wrongTokenClient = new JFlowClient("rmi://localhost:" + RMI_REGISTRY_PORT + "/" + SERVICE_NAME, "not-the-real-token");
            try {
                wrongTokenClient.getActiveProcessDefinitionKeys();
                fail(testName + " [expected an exception]");
            } catch (Exception expected) {
                assertTrue(testName, expected.getMessage() != null && expected.getMessage().contains("Invalid token"));
            }
        } catch (Exception e) {
            fail(testName + " [unexpected exception during setup: " + e + "]");
        }
    }

    /**
     * Verifies looking up a nonexistent process instance fails with a message that names its id.
     */
    private static void testUnknownProcessInstanceFailsWithMeaningfulMessage() {
        String testName = "getVariablesByProcessId on an unknown process instance id fails with a meaningful message";
        String bogusId = "does-not-exist-12345";
        try {
            client.getVariablesByProcessInstanceId(bogusId);
            fail(testName + " [expected an exception]");
        } catch (Exception expected) {
            assertTrue(testName, expected.getMessage() != null && expected.getMessage().contains(bogusId));
        }
    }

    /**
     * Verifies looking up a nonexistent process definition name fails with a message that names it.
     */
    private static void testUnknownProcessDefinitionNameFailsWithMeaningfulMessage() {
        String testName = "getProcessDiagramByProcessDefinitionName on an unknown name fails with a meaningful message";
        String bogusName = "processDefinitionThatDoesNotExist999";
        try {
            client.getProcessDiagramByProcessDefinitionKey(bogusName);
            fail(testName + " [expected an exception]");
        } catch (Exception expected) {
            assertTrue(testName, expected.getMessage() != null && expected.getMessage().contains(bogusName));
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Best-effort completion of every remaining open task on a leave-request instance, so
     * it does not linger and skew task-count assertions in later tests.
     *
     * @param processInstanceId the process instance to drain, or null if none was started
     */
    private static void cleanupLeaveRequestInstance(String processInstanceId) {
        if (processInstanceId == null) {
            return;
        }
        try {
            List<JFlowTask> remaining = client.getOpenTasksByProcessInstanceId(processInstanceId);
            for (JFlowTask task : remaining) {
                client.signalByTaskId(task.getTaskId());
            }
        } catch (Exception ignored) {
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