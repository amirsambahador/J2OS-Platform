package org.j2os.platform.jflow.share;

import java.rmi.Remote;
import java.util.List;
import java.util.Map;

/**
 * RMI contract implemented by the JFlow server and consumed through
 * {@code org.j2os.platform.jflow.client.JFlowClient}.
 * <p>
 * Every operation is authenticated with a single server-wide {@code token}, which
 * must be supplied as the first argument of each call.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public interface JFlowRemote extends Remote {

    /**
     * Starts a new instance of the given process definition.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to start
     * @return the id of the newly started process instance
     * @throws Exception if the token is invalid, or if starting the process fails
     */
    String startProcessAndGetProcessInstanceId(String token, String processDefinitionKey) throws Exception;

    /**
     * Starts a new instance of the given process definition with initial variables.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to start
     * @param variables            the initial process variables
     * @return the id of the newly started process instance
     * @throws Exception if the token is invalid, or if starting the process fails
     */
    String startProcessAndGetProcessInstanceId(String token, String processDefinitionKey, Map<String, Object> variables) throws Exception;

    /**
     * Triggers the single waiting execution of the given process instance, resuming it
     * without any variables.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to trigger
     * @throws Exception if the token is invalid, the process instance does not exist, or no
     *                   waiting execution is found for it
     */
    void forceSignalByProcessInstanceId(String token, String processInstanceId) throws Exception;

    /**
     * Triggers the single waiting execution of the given process instance, resuming it
     * with the given variables.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to trigger
     * @param variables         the variables to pass along with the trigger
     * @throws Exception if the token is invalid, the process instance does not exist, or no
     *                   waiting execution is found for it
     */
    void forceSignalByProcessInstanceId(String token, String processInstanceId, Map<String, Object> variables) throws Exception;

    /**
     * Completes the given task without any output variables.
     *
     * @param token  the caller's authentication token
     * @param taskId the id of the task to complete
     * @throws Exception if the token is invalid, or if completing the task fails
     */
    void signalByTaskId(String token, String taskId) throws Exception;

    /**
     * Completes the given task with the given output variables.
     *
     * @param token     the caller's authentication token
     * @param taskId    the id of the task to complete
     * @param variables the variables to set as part of completing the task
     * @throws Exception if the token is invalid, or if completing the task fails
     */
    void signalByTaskId(String token, String taskId, Map<String, Object> variables) throws Exception;

    /**
     * Moves the given process instance's current activity to a different target activity.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to move
     * @param targetActivityId  the id of the activity to move the process instance to
     * @throws Exception if the token is invalid, the process instance does not exist, or it
     *                   currently has no active activity
     */
    void moveByProcessInstanceId(String token, String processInstanceId, String targetActivityId) throws Exception;

    /**
     * Lists every currently open task across all processes.
     *
     * @param token the caller's authentication token
     * @return the open tasks, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    List<JFlowTask> getAllOpenTasks(String token) throws Exception;

    /**
     * Lists every currently open task assigned to the given user or group.
     *
     * @param token       the caller's authentication token
     * @param userOrGroup the assignee to filter by
     * @return the open tasks for that assignee, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    List<JFlowTask> getOpenTasksByAssignee(String token, String userOrGroup) throws Exception;

    /**
     * Lists every currently open task belonging to the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to filter by
     * @return the open tasks for that process instance, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    List<JFlowTask> getOpenTasksByProcessInstanceId(String token, String processInstanceId) throws Exception;

    /**
     * Lists every currently open task belonging to any instance of the given process definition.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the process definition key to filter by
     * @return the open tasks for that process definition, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    List<JFlowTask> getOpenTasksByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception;

    /**
     * Renders a PNG diagram of the given process instance, highlighting its currently active activities.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to render
     * @return the rendered diagram, as PNG bytes
     * @throws Exception if the token is invalid, the process instance does not exist, or its
     *                   process definition has no diagram layout information
     */
    byte[] getProcessDiagramByProcessInstanceId(String token, String processInstanceId) throws Exception;

    /**
     * Renders a plain PNG diagram of the given process definition's latest version, with
     * no activities highlighted.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to render
     * @return the rendered diagram, as PNG bytes
     * @throws Exception if the token is invalid, no such process definition exists, or it
     *                   has no diagram layout information
     */
    byte[] getProcessDiagramByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception;

    /**
     * Returns every variable currently set on the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to read variables from
     * @return a map of variable name to value
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    Map<String, Object> getVariablesByProcessInstanceId(String token, String processInstanceId) throws Exception;

    /**
     * Returns a single named variable from the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to read the variable from
     * @param variableName      the name of the variable to read
     * @return the current value of the variable, or {@code null} if it is not set
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    Object getVariableByProcessInstanceId(String token, String processInstanceId, String variableName) throws Exception;

    /**
     * Sets multiple variables on the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to update
     * @param variables         the variables to set
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    void setVariablesByProcessInstanceId(String token, String processInstanceId, Map<String, Object> variables) throws Exception;

    /**
     * Sets a single named variable on the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to update
     * @param variableName      the name of the variable to set
     * @param value             the value to assign to the variable
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    void setVariableByProcessInstanceId(String token, String processInstanceId, String variableName, Object value) throws Exception;

    /**
     * Suspends every version of the given process definition, preventing new instances
     * from starting.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to suspend
     * @throws Exception if the token is invalid
     */
    void suspendByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception;

    /**
     * Re-activates every version of the given process definition.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to activate
     * @throws Exception if the token is invalid
     */
    void activateByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception;

    /**
     * Deploys a process definition from an in-memory BPMN XML string.
     *
     * @param token        the caller's authentication token
     * @param resourceName the resource name to deploy the XML under
     * @param bpmnXml      the BPMN 2.0 XML content to deploy
     * @return the key of the deployed process definition
     * @throws Exception if the token is invalid, or if deployment fails
     */
    String deployFromSourceAndGetProcessDefinitionKey(String token, String resourceName, String bpmnXml) throws Exception;

    /**
     * Deploys a process definition read from a BPMN file on the server's local disk.
     *
     * @param token    the caller's authentication token
     * @param filePath the path, on the server, of the BPMN file to deploy
     * @return the key of the deployed process definition
     * @throws Exception if the token is invalid, the file cannot be read, or deployment fails
     */
    String deployFromFileAndGetProcessDefinitionKey(String token, String filePath) throws Exception;

    /**
     * Lists every currently active (non-suspended) process definition.
     *
     * @param token the caller's authentication token
     * @return the active process definitions, each formatted as {@code key:version},
     * ordered by version ascending
     * @throws Exception if the token is invalid
     */
    List<String> getActiveProcessDefinitionKeys(String token) throws Exception;

    /**
     * Lists every currently suspended process definition.
     *
     * @param token the caller's authentication token
     * @return the suspended process definitions, each formatted as {@code key:version},
     * ordered by version ascending
     * @throws Exception if the token is invalid
     */
    List<String> getSuspendedProcessDefinitionKeys(String token) throws Exception;
}
