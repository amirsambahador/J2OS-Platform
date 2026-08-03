package org.j2os.platform.jflow.server;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.*;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.j2os.platform.jflow.share.JFlowRemote;
import org.j2os.platform.jflow.share.JFlowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.NoSuchObjectException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RMI-exported server side of JFlow: a token-authenticated facade over a Flowable
 * {@link ProcessEngine}, exposing process deployment, process-instance lifecycle
 * (start, signal, move), task queries, variable access, and diagram rendering to
 * remote clients.
 * <p>
 * Every operation is authenticated with a single server-wide token, checked by
 * {@link #checkToken(String)} before the underlying Flowable call is made. Every
 * authentication failure, and every process/definition deployment or suspend/activate
 * call, is logged (see {@link #LOGGER}) so an audit trail exists.
 * <p>
 * When this server is no longer needed (e.g. application shutdown, or the end of a
 * test), call {@link #close()} to unexport the RMI object and close the underlying
 * process engine; otherwise its RMI export and any resources held by the engine
 * (such as its database connection pool) remain open until the JVM exits.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JFlowServer extends UnicastRemoteObject implements JFlowRemote {

    private static final Logger LOGGER = LoggerFactory.getLogger(JFlowServer.class);

    /**
     * The token that must be supplied by callers to authenticate against this server.
     */
    private final String validToken;

    /**
     * The Flowable process engine backing this server.
     */
    private final ProcessEngine processEngine;

    /**
     * Flowable service used to start, signal, move, and inspect running process instances.
     */
    private final RuntimeService runtimeService;

    /**
     * Flowable service used to deploy, suspend, activate, and query process definitions.
     */
    private final RepositoryService repositoryService;

    /**
     * Flowable service used to query and complete user tasks.
     */
    private final TaskService taskService;

    /**
     * Creates and exports a new JFlow server, building a new {@link ProcessEngine} from
     * the given configuration.
     *
     * @param token                      the token callers must supply to authenticate against this server
     * @param processEngineConfiguration the configuration used to build the underlying process engine
     * @throws Exception if exporting the object fails, or if building the process engine fails
     */
    public JFlowServer(String token, ProcessEngineConfiguration processEngineConfiguration) throws Exception {
        super();
        this.validToken = token;
        this.processEngine = processEngineConfiguration.buildProcessEngine();
        this.runtimeService = processEngine.getRuntimeService();
        this.repositoryService = processEngine.getRepositoryService();
        this.taskService = processEngine.getTaskService();
        LOGGER.info("JFlowServer: started (new process engine)");
    }

    /**
     * Creates and exports a new JFlow server around an already-built {@link ProcessEngine}.
     *
     * @param token         the token callers must supply to authenticate against this server
     * @param processEngine the already-built process engine to use
     * @throws Exception if exporting the object fails
     */
    public JFlowServer(String token, ProcessEngine processEngine) throws Exception {
        super();
        this.validToken = token;
        this.processEngine = processEngine;
        this.runtimeService = processEngine.getRuntimeService();
        this.repositoryService = processEngine.getRepositoryService();
        this.taskService = processEngine.getTaskService();
        LOGGER.info("JFlowServer: started (existing process engine)");
    }

    /**
     * Returns the underlying Flowable process engine.
     *
     * @return the process engine backing this server
     */
    public ProcessEngine getProcessEngine() {
        return processEngine;
    }

    /**
     * Shuts this server down: unexports the RMI object (so no further remote calls can
     * reach it) and closes the underlying {@link ProcessEngine} (releasing its database
     * connection pool and any other resources it holds).
     * <p>
     * Safe to call more than once; a failure to unexport (e.g. because it was already
     * unexported) is ignored rather than thrown, so callers can always proceed to close
     * the process engine.
     */
    public void close() {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
            // Already unexported - nothing further to do here.
        }
        processEngine.close();
        LOGGER.info("JFlowServer: shut down (unexported and process engine closed)");
    }

    /**
     * Verifies that the given token matches this server's token, using a constant-time
     * comparison so the check does not leak timing information about how much of the
     * token matched. Every failed attempt is logged at {@code WARN} level so repeated
     * failures (e.g. a brute-force attempt) leave an audit trail.
     *
     * @param token the token to check
     * @throws Exception if the token is null or does not match
     */
    private void checkToken(String token) throws Exception {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                validToken.getBytes(StandardCharsets.UTF_8))) {
            LOGGER.warn("JFlowServer: authentication failed (invalid or missing token)");
            throw new Exception("Invalid token, access denied.");
        }
    }

    /**
     * Validates {@code token}, then runs {@code operation} and returns its result, wrapping
     * any failure from the operation itself in a new {@link Exception} that carries the
     * original exception's class name and message, with the original exception preserved
     * as the cause so its stack trace is not lost.
     *
     * @param token     the caller's authentication token
     * @param operation the operation to run once the token has been validated
     * @param <T>       the type of value returned by the operation
     * @return the value returned by {@code operation}
     * @throws Exception if the token is invalid, or if the operation itself fails
     */
    private <T> T call(String token, ServerOperation<T> operation) throws Exception {
        checkToken(token);
        try {
            return operation.run();
        } catch (Exception e) {
            throw new Exception(e.getClass() + ":" + e.getMessage(), e);
        }
    }

    /**
     * Validates {@code token}, then runs {@code operation}, wrapping any failure from the
     * operation itself in a new {@link Exception} that carries the original exception's
     * class name and message, with the original exception preserved as the cause so its
     * stack trace is not lost.
     *
     * @param token     the caller's authentication token
     * @param operation the operation to run once the token has been validated
     * @throws Exception if the token is invalid, or if the operation itself fails
     */
    private void run(String token, VoidServerOperation operation) throws Exception {
        checkToken(token);
        try {
            operation.run();
        } catch (Exception e) {
            throw new Exception(e.getClass() + ":" + e.getMessage(), e);
        }
    }

    /**
     * Verifies that a process instance with the given id currently exists.
     *
     * @param processInstanceId the process instance id to check
     * @throws Exception if no such process instance exists (it may already have completed)
     */
    private void requireProcessInstanceExists(String processInstanceId) throws Exception {
        boolean exists = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count() > 0;
        if (!exists) {
            throw new Exception("No process found with this id (it may have already completed): " + processInstanceId);
        }
    }

    /**
     * Starts a new instance of the given process definition.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to start
     * @return the id of the newly started process instance
     * @throws Exception if the token is invalid, or if starting the process fails
     */
    @Override
    public String startProcessAndGetProcessInstanceId(String token, String processDefinitionKey) throws Exception {
        return call(token, () ->
                runtimeService.startProcessInstanceByKey(processDefinitionKey).getId());
    }

    /**
     * Starts a new instance of the given process definition with initial variables.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to start
     * @param variables            the initial process variables
     * @return the id of the newly started process instance
     * @throws Exception if the token is invalid, or if starting the process fails
     */
    @Override
    public String startProcessAndGetProcessInstanceId(String token, String processDefinitionKey, Map<String, Object> variables) throws Exception {
        return call(token, () ->
                runtimeService.startProcessInstanceByKey(processDefinitionKey, variables).getId());
    }

    /**
     * Triggers the single waiting execution of the given process instance, resuming it
     * without any variables.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to trigger
     * @throws Exception if the token is invalid, the process instance does not exist, or no
     *                   waiting execution is found for it
     */
    @Override
    public void forceSignalByProcessInstanceId(String token, String processInstanceId) throws Exception {
        run(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            runtimeService.trigger(findWaitingExecution(processInstanceId).getId());
        });
    }

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
    @Override
    public void forceSignalByProcessInstanceId(String token, String processInstanceId, Map<String, Object> variables) throws Exception {
        run(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            runtimeService.trigger(findWaitingExecution(processInstanceId).getId(), variables);
        });
    }

    /**
     * Finds the single child execution currently waiting within the given process instance.
     *
     * @param processInstanceId the process instance to search
     * @return the waiting child execution
     * @throws Exception if no waiting execution is found for the process instance
     */
    private Execution findWaitingExecution(String processInstanceId) throws Exception {
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .onlyChildExecutions()
                .singleResult();
        if (execution == null) {
            throw new Exception("No waiting execution was found for this process instance: " + processInstanceId);
        }
        return execution;
    }

    /**
     * Completes the given task without any output variables.
     *
     * @param token  the caller's authentication token
     * @param taskId the id of the task to complete
     * @throws Exception if the token is invalid, or if completing the task fails
     */
    @Override
    public void signalByTaskId(String token, String taskId) throws Exception {
        run(token, () -> taskService.complete(taskId));
    }

    /**
     * Completes the given task with the given output variables.
     *
     * @param token     the caller's authentication token
     * @param taskId    the id of the task to complete
     * @param variables the variables to set as part of completing the task
     * @throws Exception if the token is invalid, or if completing the task fails
     */
    @Override
    public void signalByTaskId(String token, String taskId, Map<String, Object> variables) throws Exception {
        run(token, () -> taskService.complete(taskId, variables));
    }

    /**
     * Moves the given process instance's current activity to a different target activity.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to move
     * @param targetActivityId  the id of the activity to move the process instance to
     * @throws Exception if the token is invalid, the process instance does not exist, or it
     *                   currently has no active activity
     */
    @Override
    public void moveByProcessInstanceId(String token, String processInstanceId, String targetActivityId) throws Exception {
        run(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
            if (activeActivityIds == null || activeActivityIds.isEmpty()) {
                throw new Exception("No active activity was found for this process instance: " + processInstanceId);
            }
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(processInstanceId)
                    .moveActivityIdTo(activeActivityIds.get(0), targetActivityId)
                    .changeState();
        });
    }

    /**
     * Lists every currently open task across all processes.
     *
     * @param token the caller's authentication token
     * @return the open tasks, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    @Override
    public List<JFlowTask> getAllOpenTasks(String token) throws Exception {
        return call(token, () -> toJFlowTasks(taskService.createTaskQuery().orderByTaskCreateTime().asc().list()));
    }

    /**
     * Lists every currently open task assigned to the given user or group.
     *
     * @param token       the caller's authentication token
     * @param userOrGroup the assignee to filter by
     * @return the open tasks for that assignee, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    @Override
    public List<JFlowTask> getOpenTasksByAssignee(String token, String userOrGroup) throws Exception {
        return call(token, () -> toJFlowTasks(taskService.createTaskQuery()
                .taskAssignee(userOrGroup)
                .orderByTaskCreateTime().asc()
                .list()));
    }

    /**
     * Lists every currently open task belonging to the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to filter by
     * @return the open tasks for that process instance, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    @Override
    public List<JFlowTask> getOpenTasksByProcessInstanceId(String token, String processInstanceId) throws Exception {
        return call(token, () -> toJFlowTasks(taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list()));
    }

    /**
     * Lists every currently open task belonging to any instance of the given process definition.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the process definition key to filter by
     * @return the open tasks for that process definition, ordered by creation time ascending
     * @throws Exception if the token is invalid
     */
    @Override
    public List<JFlowTask> getOpenTasksByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception {
        return call(token, () -> toJFlowTasks(taskService.createTaskQuery()
                .processDefinitionKey(processDefinitionKey)
                .orderByTaskCreateTime().asc()
                .list()));
    }

    /**
     * Renders a PNG diagram of the given process instance, highlighting its currently active activities.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the id of the process instance to render
     * @return the rendered diagram, as PNG bytes
     * @throws Exception if the token is invalid, the process instance does not exist, or its
     *                   process definition has no diagram layout information
     */
    @Override
    public byte[] getProcessDiagramByProcessInstanceId(String token, String processInstanceId) throws Exception {
        return call(token, () -> {
            ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (instance == null) {
                throw new Exception("No process found with this id (it may have already completed): " + processInstanceId);
            }

            BpmnModel bpmnModel = repositoryService.getBpmnModel(instance.getProcessDefinitionId());
            requireDiagramInfo(bpmnModel, processInstanceId);

            List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
            return renderDiagram(bpmnModel, activeActivityIds);
        });
    }

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
    @Override
    public byte[] getProcessDiagramByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception {
        return call(token, () -> {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processDefinitionKey)
                    .latestVersion()
                    .singleResult();
            if (processDefinition == null) {
                throw new Exception("No process definition was found with this name: " + processDefinitionKey);
            }

            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
            requireDiagramInfo(bpmnModel, processDefinitionKey);

            return renderDiagram(bpmnModel, Collections.emptyList());
        });
    }

    /**
     * Verifies that a BPMN model carries diagram layout information, without which no
     * diagram can be rendered.
     *
     * @param bpmnModel          the model to check
     * @param subjectDescription a description of the subject (process instance id or
     *                           definition key) to include in the error message
     * @throws Exception if the model has no {@code bpmndi:BPMNDiagram} layout information
     */
    private void requireDiagramInfo(BpmnModel bpmnModel, String subjectDescription) throws Exception {
        if (bpmnModel.getLocationMap() == null || bpmnModel.getLocationMap().isEmpty()) {
            throw new Exception("This process has no diagram layout information (bpmndi:BPMNDiagram); the diagram cannot be rendered: " + subjectDescription);
        }
    }

    /**
     * Renders a BPMN model to a PNG image, optionally highlighting the given activities.
     *
     * @param bpmnModel              the model to render
     * @param highlightedActivityIds the ids of activities to highlight in the rendered image
     * @return the rendered diagram, as PNG bytes
     * @throws Exception if reading the generated diagram stream fails
     */
    private byte[] renderDiagram(BpmnModel bpmnModel, List<String> highlightedActivityIds) throws Exception {
        ProcessEngineConfiguration engineConfig = processEngine.getProcessEngineConfiguration();
        ProcessDiagramGenerator diagramGenerator = engineConfig.getProcessDiagramGenerator();

        try (InputStream diagramStream = diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                highlightedActivityIds,
                Collections.emptyList(),
                engineConfig.getActivityFontName(),
                engineConfig.getLabelFontName(),
                engineConfig.getAnnotationFontName(),
                engineConfig.getClassLoader(),
                1.0,
                engineConfig.isDrawSequenceFlowNameWithNoLabelDI())) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = diagramStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    /**
     * Converts Flowable {@link Task} instances into the serializable {@link JFlowTask} DTO
     * exposed to clients.
     *
     * @param taskList the Flowable tasks to convert
     * @return the equivalent list of {@link JFlowTask} instances, in the same order
     */
    private List<JFlowTask> toJFlowTasks(List<Task> taskList) {
        List<JFlowTask> result = new ArrayList<>();
        for (Task task : taskList) {
            JFlowTask jFlowTask = new JFlowTask();
            jFlowTask.setTaskId(task.getId());
            jFlowTask.setAssignee(task.getAssignee());
            jFlowTask.setCreateTime(task.getCreateTime());
            //jFlowTask.setProcessDefinitionName(task.getProcessDefinitionId());
            ProcessDefinition definition = repositoryService.getProcessDefinition(task.getProcessDefinitionId());
            jFlowTask.setProcessDefinitionKey(definition.getKey());
            jFlowTask.setProcessInstanceId(task.getProcessInstanceId());
            jFlowTask.setPriority(task.getPriority());
            result.add(jFlowTask);
        }
        return result;
    }

    /**
     * Returns every variable currently set on the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to read variables from
     * @return a map of variable name to value
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    @Override
    public Map<String, Object> getVariablesByProcessInstanceId(String token, String processInstanceId) throws Exception {
        return call(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            return runtimeService.getVariables(processInstanceId);
        });
    }

    /**
     * Returns a single named variable from the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to read the variable from
     * @param variableName      the name of the variable to read
     * @return the current value of the variable, or {@code null} if it is not set
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    @Override
    public Object getVariableByProcessInstanceId(String token, String processInstanceId, String variableName) throws Exception {
        return call(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            return runtimeService.getVariable(processInstanceId, variableName);
        });
    }

    /**
     * Sets multiple variables on the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to update
     * @param variables         the variables to set
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    @Override
    public void setVariablesByProcessInstanceId(String token, String processInstanceId, Map<String, Object> variables) throws Exception {
        run(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            runtimeService.setVariables(processInstanceId, variables);
        });
    }

    /**
     * Sets a single named variable on the given process instance.
     *
     * @param token             the caller's authentication token
     * @param processInstanceId the process instance to update
     * @param variableName      the name of the variable to set
     * @param value             the value to assign to the variable
     * @throws Exception if the token is invalid, or if the process instance does not exist
     */
    @Override
    public void setVariableByProcessInstanceId(String token, String processInstanceId, String variableName, Object value) throws Exception {
        run(token, () -> {
            requireProcessInstanceExists(processInstanceId);
            runtimeService.setVariable(processInstanceId, variableName, value);
        });
    }

    /**
     * Suspends every version of the given process definition, preventing new instances
     * from starting.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to suspend
     * @throws Exception if the token is invalid
     */
    @Override
    public void suspendByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception {
        run(token, () -> repositoryService.suspendProcessDefinitionByKey(processDefinitionKey));
        LOGGER.info("JFlowServer: suspended process definition '{}'", processDefinitionKey);
    }

    /**
     * Re-activates every version of the given process definition.
     *
     * @param token                the caller's authentication token
     * @param processDefinitionKey the key of the process definition to activate
     * @throws Exception if the token is invalid
     */
    @Override
    public void activateByProcessDefinitionKey(String token, String processDefinitionKey) throws Exception {
        run(token, () -> repositoryService.activateProcessDefinitionByKey(processDefinitionKey));
        LOGGER.info("JFlowServer: activated process definition '{}'", processDefinitionKey);
    }

    /**
     * Deploys a process definition from an in-memory BPMN XML string.
     *
     * @param token        the caller's authentication token
     * @param resourceName the resource name to deploy the XML under
     * @param bpmnXml      the BPMN 2.0 XML content to deploy
     * @return the key of the deployed process definition
     * @throws Exception if the token is invalid, or if deployment fails
     */
    @Override
    public String deployFromSourceAndGetProcessDefinitionKey(String token, String resourceName, String bpmnXml) throws Exception {
        String key = call(token, () -> deployXmlAndGetProcessDefinitionKey(resourceName, bpmnXml));
        LOGGER.info("JFlowServer: deployed process definition '{}' from source '{}'", key, resourceName);
        return key;
    }

    /**
     * Deploys a process definition read from a BPMN file on the server's local disk.
     *
     * @param token    the caller's authentication token
     * @param filePath the path, on the server, of the BPMN file to deploy
     * @return the key of the deployed process definition
     * @throws Exception if the token is invalid, the file cannot be read, or deployment fails
     */
    @Override
    public String deployFromFileAndGetProcessDefinitionKey(String token, String filePath) throws Exception {
        String key = call(token, () -> {
            String bpmnXml = new String(Files.readAllBytes(Paths.get(filePath)));
            String resourceName = Paths.get(filePath).getFileName().toString();
            return deployXmlAndGetProcessDefinitionKey(resourceName, bpmnXml);
        });
        LOGGER.info("JFlowServer: deployed process definition '{}' from file '{}'", key, filePath);
        return key;
    }

    /**
     * Deploys the given BPMN XML under the given resource name and returns the key of the
     * resulting process definition. Appends a {@code .bpmn20.xml} suffix to the resource
     * name if it does not already look like a BPMN file name.
     *
     * @param resourceName the resource name to deploy the XML under
     * @param bpmnXml      the BPMN 2.0 XML content to deploy
     * @return the key of the deployed process definition
     */
    private String deployXmlAndGetProcessDefinitionKey(String resourceName, String bpmnXml) {
        String normalizedName = resourceName.endsWith(".bpmn20.xml") || resourceName.endsWith(".bpmn")
                ? resourceName
                : resourceName + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .addString(normalizedName, bpmnXml)
                .deploy();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        return processDefinition.getKey();
    }

    /**
     * Lists every currently active (non-suspended) process definition.
     *
     * @param token the caller's authentication token
     * @return the active process definitions, each formatted as {@code key:version},
     * ordered by version ascending
     * @throws Exception if the token is invalid
     */
    @Override
    public List<String> getActiveProcessDefinitionKeys(String token) throws Exception {
        return call(token, () -> toKeyVersionList(repositoryService.createProcessDefinitionQuery()
                .active()
                .orderByProcessDefinitionVersion().asc()
                .list()));
    }

    /**
     * Lists every currently suspended process definition.
     *
     * @param token the caller's authentication token
     * @return the suspended process definitions, each formatted as {@code key:version},
     * ordered by version ascending
     * @throws Exception if the token is invalid
     */
    @Override
    public List<String> getSuspendedProcessDefinitionKeys(String token) throws Exception {
        return call(token, () -> toKeyVersionList(repositoryService.createProcessDefinitionQuery()
                .suspended()
                .orderByProcessDefinitionVersion().asc()
                .list()));
    }

    /**
     * Formats a list of process definitions as {@code key:version} strings.
     *
     * @param processDefinitions the process definitions to format
     * @return one {@code key:version} string per process definition, in the same order
     */
    private List<String> toKeyVersionList(List<ProcessDefinition> processDefinitions) {
        List<String> result = new ArrayList<>();
        for (ProcessDefinition processDefinition : processDefinitions) {
            result.add(processDefinition.getKey() + ":" + processDefinition.getVersion());
        }
        return result;
    }

    /**
     * A token-checked server operation that returns a result.
     */
    @FunctionalInterface
    private interface ServerOperation<T> {
        /**
         * Runs the operation.
         *
         * @return the operation's result
         * @throws Exception if the operation fails
         */
        T run() throws Exception;
    }

    /**
     * A token-checked server operation with no result.
     */
    @FunctionalInterface
    private interface VoidServerOperation {
        /**
         * Runs the operation.
         *
         * @throws Exception if the operation fails
         */
        void run() throws Exception;
    }
}