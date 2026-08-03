package org.j2os.platform.jflow.client;

import org.j2os.platform.jflow.share.JFlowRemote;
import org.j2os.platform.jflow.share.JFlowTask;

import java.rmi.Naming;
import java.util.List;
import java.util.Map;

/**
 * Client-side handle to a single JFlow server.
 * <p>
 * After construction, every method transparently forwards the stored authentication
 * token to the remote {@link JFlowRemote} looked up at construction time.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JFlowClient {

    /**
     * The remote server stub looked up at construction time.
     */
    private final JFlowRemote remote;

    /**
     * The authentication token supplied at construction and reused on every call.
     */
    private final String token;

    /**
     * Connects this client to a JFlow server published via RMI, and stores the token to
     * be used for all subsequent calls.
     *
     * @param rmiUrl the RMI URL of the server to connect to (e.g. {@code rmi://localhost:1099/jbpms})
     * @param token  the authentication token to use for this server on every subsequent call
     * @throws Exception if the RMI lookup fails
     */
    public JFlowClient(String rmiUrl, String token) throws Exception {
        this.remote = (JFlowRemote) Naming.lookup(rmiUrl);
        this.token = token;
    }

    /**
     * Returns the underlying remote server stub, for callers that need direct access to it.
     *
     * @return the remote server stub
     */
    public JFlowRemote getRemote() {
        return remote;
    }

    /**
     * Starts a new instance of the given process definition.
     * <p>
     * Takes the id defined inside the BPMN file, starts the process, and returns the
     * new process instance's id.
     *
     * @param processDefinitionKey the key of the process definition to start
     * @return the id of the newly started process instance
     * @throws Exception if the token is invalid, or if starting the process fails
     */
    public String startProcessAndGetProcessInstanceId(String processDefinitionKey) throws Exception {
        return remote.startProcessAndGetProcessInstanceId(token, processDefinitionKey);
    }

    /**
     * Starts a new instance of the given process definition with initial variables.
     *
     * @param processDefinitionKey the key of the process definition to start
     * @param variables            the initial process variables
     * @return the id of the newly started process instance
     * @throws Exception if the token is invalid, or if starting the process fails
     */
    public String startProcessAndGetProcessInstanceId(String processDefinitionKey, Map<String, Object> variables) throws Exception {
        return remote.startProcessAndGetProcessInstanceId(token, processDefinitionKey, variables);
    }

    /**
     * Forces the given process instance forward one step, regardless of its current state.
     * <p>
     * Takes the process instance id and triggers its single waiting execution, without
     * passing any variables along.
     *
     * @param processInstanceId the id of the process instance to force forward
     * @throws Exception if the token is invalid, the process instance does not exist, or no
     *                   waiting execution is found for it
     */
    public void forceSignalByProcessInstanceId(String processInstanceId) throws Exception {
        remote.forceSignalByProcessInstanceId(token, processInstanceId);
    }

    /**
     * Forces the given process instance forward one step, regardless of its current state,
     * passing along the given variables.
     *
     * @param processInstanceId the id of the process instance to force forward
     * @param variables         the variables to pass along with the trigger
     * @throws Exception if the token is invalid, the process instance does not exist, or no
     *                   waiting execution is found for it
     */
    public void forceSignalByProcessInstanceId(String processInstanceId, Map<String, Object> variables) throws Exception {
        remote.forceSignalByProcessInstanceId(token, processInstanceId, variables);
    }

    /**
     * Advances the process one step by completing the given task.
     * <p>
     * Takes the task id and advances its process by completing it, without any output variables.
     *
     * @param taskId the id of the task to complete
     * @throws Exception if the token is invalid, or if completing the task fails
     */
    public void signalByTaskId(String taskId) throws Exception {
        remote.signalByTaskId(token, taskId);
    }

    /**
     * Advances the process one step by completing the given task with the given output variables.
     *
     * @param taskId    the id of the task to complete
     * @param variables the variables to set as part of completing the task
     * @throws Exception if the token is invalid, or if completing the task fails
     */
    public void signalByTaskId(String taskId, Map<String, Object> variables) throws Exception {
        remote.signalByTaskId(token, taskId, variables);
    }

    /**
     * Moves the given process instance's current activity to a different target activity.
     * <p>
     * Takes the process instance id and transitions it to the given state.
     *
     * @param processInstanceId the id of the process instance to move
     * @param targetActivityId  the id of the activity to move the process instance to
     * @throws Exception if the token is invalid, the process instance does not exist, or it
     *                   currently has no active activity
     */
    public void moveByProcessInstanceId(String processInstanceId, String targetActivityId) throws Exception {
        remote.moveByProcessInstanceId(token, processInstanceId, targetActivityId);
    }

    /**
     * Lists every currently open task in the process engine.
     *
     * @return the open tasks, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    public List<JFlowTask> getAllOpenTasks() throws Exception {
        return remote.getAllOpenTasks(token);
    }

    /**
     * Lists every currently open task assigned to the given person or group.
     *
     * @param userOrGroup the assignee to filter by
     * @return the open tasks for that assignee, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    public List<JFlowTask> getOpenTasksByAssignee(String userOrGroup) throws Exception {
        return remote.getOpenTasksByAssignee(token, userOrGroup);
    }

    /**
     * Lists every currently open task belonging to the given process instance.
     * <p>
     * Handy for driving a process from a UI: put a page reference on the {@code
     * flowable:assignee} attribute in the process, e.g. {@code page:form1.jsp}, then use
     * this method to find out which step the process is currently on. If the assignee
     * starts with {@code page:} (e.g. {@code page:form1.jsp}), route the user to that page
     * ({@code form1.jsp}); if it doesn't start with {@code page:} (e.g. it's a manager's
     * name), the task is really assigned to a person.
     *
     * @param processInstanceId the process instance to filter by
     * @return the open tasks for that process instance, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    public List<JFlowTask> getOpenTasksByProcessInstanceId(String processInstanceId) throws Exception {
        return remote.getOpenTasksByProcessInstanceId(token, processInstanceId);
    }

    /**
     * Lists every currently open task belonging to any instance of the given process definition.
     *
     * @param processDefinitionKey the process definition key to filter by
     * @return the open tasks for that process definition, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    public List<JFlowTask> getOpenTasksByProcessDefinitionKey(String processDefinitionKey) throws Exception {
        return remote.getOpenTasksByProcessDefinitionKey(token, processDefinitionKey);
    }

    /**
     * Renders a PNG diagram of the given process instance, highlighting its currently active activities.
     *
     * @param processInstanceId the id of the process instance to render
     * @return the rendered diagram, as PNG bytes
     * @throws Exception if the token is invalid, the process instance does not exist, or its
     *                   process definition has no diagram layout information
     */
    public byte[] getProcessDiagramByProcessInstanceId(String processInstanceId) throws Exception {
        return remote.getProcessDiagramByProcessInstanceId(token, processInstanceId);
    }

    /**
     * Renders a plain PNG diagram of the given process definition's latest version, with
     * no activities highlighted.
     *
     * @param processDefinitionKey the key of the process definition to render
     * @return the rendered diagram, as PNG bytes
     * @throws Exception if the token is invalid, no such process definition exists, or it
     *                   has no diagram layout information
     */
    public byte[] getProcessDiagramByProcessDefinitionKey(String processDefinitionKey) throws Exception {
        return remote.getProcessDiagramByProcessDefinitionKey(token, processDefinitionKey);
    }

    /**
     * Returns every variable currently set on the given process instance.
     *
     * @param processInstanceId the process instance to read variables from
     * @return a map of variable name to value
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    public Map<String, Object> getVariablesByProcessInstanceId(String processInstanceId) throws Exception {
        return remote.getVariablesByProcessInstanceId(token, processInstanceId);
    }

    /**
     * Returns a single named variable from the given process instance.
     *
     * @param processInstanceId the process instance to read the variable from
     * @param variableName      the name of the variable to read
     * @return the current value of the variable, or {@code null} if it is not set
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    public Object getVariableByProcessInstanceId(String processInstanceId, String variableName) throws Exception {
        return remote.getVariableByProcessInstanceId(token, processInstanceId, variableName);
    }

    /**
     * Sets multiple variables on the given process instance.
     *
     * @param processInstanceId the process instance to update
     * @param variables         the variables to set
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    public void setVariablesByProcessInstanceId(String processInstanceId, Map<String, Object> variables) throws Exception {
        remote.setVariablesByProcessInstanceId(token, processInstanceId, variables);
    }

    /**
     * Sets a single named variable on the given process instance.
     *
     * @param processInstanceId the process instance to update
     * @param variableName      the name of the variable to set
     * @param value             the value to assign to the variable
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    public void setVariableByProcessInstanceId(String processInstanceId, String variableName, Object value) throws Exception {
        remote.setVariableByProcessInstanceId(token, processInstanceId, variableName, value);
    }

    /**
     * Takes a process definition out of service, preventing new instances from starting from it.
     *
     * @param processDefinitionKey the key of the process definition to suspend
     * @throws Exception if the token is invalid
     */
    public void suspendByProcessDefinitionKey(String processDefinitionKey) throws Exception {
        remote.suspendByProcessDefinitionKey(token, processDefinitionKey);
    }

    /**
     * Puts a process definition back into service, allowing new instances to start from it again.
     *
     * @param processDefinitionKey the key of the process definition to activate
     * @throws Exception if the token is invalid
     */
    public void activateByProcessDefinitionKey(String processDefinitionKey) throws Exception {
        remote.activateByProcessDefinitionKey(token, processDefinitionKey);
    }

    /**
     * Deploys a process definition from an in-memory BPMN XML string.
     *
     * @param resourceName the resource name to deploy the XML under
     * @param bpmnXml      the BPMN 2.0 XML content to deploy
     * @return the key of the deployed process definition
     * @throws Exception if the token is invalid, or if deployment fails
     */
    public String deployFromSourceAndGetProcessDefinitionKey(String resourceName, String bpmnXml) throws Exception {
        return remote.deployFromSourceAndGetProcessDefinitionKey(token, resourceName, bpmnXml);
    }

    /**
     * Deploys a process definition read from a BPMN file on the server's local disk.
     *
     * @param filePath the path, on the server, of the BPMN file to deploy
     * @return the key of the deployed process definition
     * @throws Exception if the token is invalid, the file cannot be read, or deployment fails
     */
    public String deployFromFileAndGetProcessDefinitionKey(String filePath) throws Exception {
        return remote.deployFromFileAndGetProcessDefinitionKey(token, filePath);
    }

    /**
     * Lists every currently active (non-suspended) process definition.
     *
     * @return the active process definitions, each formatted as {@code key:version},
     * ordered by version ascending
     * @throws Exception if the token is invalid
     */
    public List<String> getActiveProcessDefinitionKeys() throws Exception {
        return remote.getActiveProcessDefinitionKeys(token);
    }

    /**
     * Lists every currently suspended (inactive) process definition.
     *
     * @return the suspended process definitions, each formatted as {@code key:version},
     * ordered by version ascending
     * @throws Exception if the token is invalid
     */
    public List<String> getSuspendedProcessDefinitionKeys() throws Exception {
        return remote.getSuspendedProcessDefinitionKeys(token);
    }
}
