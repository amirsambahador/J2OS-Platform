package org.j2os.examples.desktop.jflow.client;

import org.j2os.platform.jflow.client.JFlowClient;
import org.j2os.platform.jflow.share.JFlowTask;
import org.j2os.examples.desktop.jflow.server.ServerExample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates a full client-side run of the {@code leaveRequest} process: starting an
 * instance with initial variables, completing its two approval tasks in order, and then
 * running the fully automated {@code leaveAutomation} process.
 * <p>
 * Assumes {@link ServerExample} is already running and has deployed both process
 * definitions.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ClientExample {

    /**
     * The token used to authenticate against the JFlow server.
     */
    private static final String TOKEN = "CHANGE_ME_TOKEN";

    /**
     * Runs the client example.
     *
     * @param args not used
     * @throws Exception if any remote call fails
     */
    public static void main(String[] args) throws Exception {
        JFlowClient client = new JFlowClient("rmi://localhost:1099/jbpms", TOKEN);

        // Step 1: start the leave-request process with 3 leave days.
        Map<String, Object> startVariables = new HashMap<>();
        startVariables.put("days", 3);
        String processInstanceId = client.startProcessAndGetProcessInstanceId("leaveRequest", startVariables);
        System.out.println("Process started, id: " + processInstanceId);

        // Step 2: direct manager approval. The process is currently waiting at its
        // first user task, so this is the only open task for this instance.
        JFlowTask managerTask = getFirstOpenTask(client, processInstanceId);
        System.out.println("Current open task: " + managerTask.getTaskId() + " (" + managerTask.getAssignee() + ")");
        client.signalByTaskId(managerTask.getTaskId());
        System.out.println("Direct manager approval done.");

        // Step 3: HR approval. Completing the manager's task above moved the process
        // to the next user task.
        JFlowTask hrTask = getFirstOpenTask(client, processInstanceId);
        System.out.println("Current open task: " + hrTask.getTaskId() + " (" + hrTask.getAssignee() + ")");
        client.signalByTaskId(hrTask.getTaskId());
        System.out.println("HR approval done.");

        // Step 4: verify the process has actually finished (no open tasks left).
        List<JFlowTask> remainingTasks = client.getOpenTasksByProcessInstanceId(processInstanceId);
        System.out.println("Remaining open task count: " + remainingTasks.size() + " (should be zero)");

        // Step 5: run the fully automated leaveAutomation process; it has only two service
        // tasks (no task waiting on user approval), so a single startProcess call runs it
        // to completion.
        Map<String, Object> automationVariables = new HashMap<>();
        automationVariables.put("days", 3);
        String automationInstanceId = client.startProcessAndGetProcessInstanceId("leaveAutomation", automationVariables);
        System.out.println("leaveAutomation process ran and finished immediately; id: " + automationInstanceId);
        System.out.println("(During that run: CalculateLeaveDeductionDelegate computed deductionAmount=1500000 for days=3,");
        System.out.println(" and BuildLeaveSummaryDelegate used that same value to build the summaryMessage.)");
    }

    /**
     * Fetches the first open task for the given process instance.
     *
     * @param client            the client to query with
     * @param processInstanceId the process instance to look up open tasks for
     * @return the first open task found
     * @throws Exception if the remote call fails, or if no open task is found
     */
    private static JFlowTask getFirstOpenTask(JFlowClient client, String processInstanceId) throws Exception {
        List<JFlowTask> tasks = client.getOpenTasksByProcessInstanceId(processInstanceId);
        if (tasks.isEmpty()) {
            throw new IllegalStateException("No open task was found for this process: " + processInstanceId);
        }
        return tasks.get(0);
    }
}