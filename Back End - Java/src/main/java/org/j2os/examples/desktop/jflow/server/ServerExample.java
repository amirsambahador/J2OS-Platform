package org.j2os.examples.desktop.jflow.server;

import org.flowable.common.engine.impl.persistence.StrongUuidGenerator;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.j2os.platform.jflow.server.JFlowServer;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.util.List;

/**
 * Starts a JFlow server backed by a Postgres-based Flowable engine, publishes it via RMI,
 * and deploys three example process definitions to it.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ServerExample {

    /**
     * The token clients must supply to authenticate against the server started here.
     */
    private static final String TOKEN = "CHANGE_ME_TOKEN";

    /**
     * Configures and starts the process engine, publishes the server via RMI, and deploys
     * the example process definitions.
     *
     * @param args not used
     * @throws Exception if the engine, RMI registry, server, or any deployment fails to start
     */
    public static void main(String[] args) throws Exception {
        StandaloneProcessEngineConfiguration standaloneConfig = new StandaloneProcessEngineConfiguration();
        standaloneConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/flowable");
        standaloneConfig.setJdbcDriver("org.postgresql.Driver");
        standaloneConfig.setJdbcUsername("postgres");
        standaloneConfig.setJdbcPassword("myjava123");
        standaloneConfig.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        standaloneConfig.setAsyncExecutorActivate(false);
        // We no longer have a businessKey layer; Flowable's internal id is handed directly to
        // clients, so it must be unguessable. That is why this line is always enabled.
        standaloneConfig.setIdGenerator(new StrongUuidGenerator());

        JFlowServer jbpmsServer = new JFlowServer(TOKEN, standaloneConfig);

        LocateRegistry.createRegistry(1099);
        Naming.rebind("//localhost/jbpms", jbpmsServer);

        System.out.println("JBpms server is up on port 1099 (service: jbpms)");

        // Deploy the three sample processes on this server; only the file path needs to be
        // supplied, the file is read from disk inside deployFromFileAndGetProcessDefinitionName
        // itself. Paths are relative to the project root (where pom.xml lives).
        deploySample(jbpmsServer, "src/main/resources/bpmn-repository/leave-request.bpmn20.xml");
        deploySample(jbpmsServer, "src/main/resources/bpmn-repository/purchase-approval.bpmn20.xml");
        deploySample(jbpmsServer, "src/main/resources/bpmn-repository/leave-automation.bpmn20.xml");

        List<String> activeDefinitions = jbpmsServer.getActiveProcessDefinitionKeys(TOKEN);
        System.out.println("Active process definitions on the server: " + activeDefinitions);
    }

    /**
     * Deploys a single BPMN sample file to the given server and prints the resulting
     * process definition name.
     *
     * @param server   the server to deploy to
     * @param filePath the path, on the server, of the BPMN file to deploy
     * @throws Exception if deployment fails
     */
    private static void deploySample(JFlowServer server, String filePath) throws Exception {
        String processDefinitionName = server.deployFromFileAndGetProcessDefinitionKey(TOKEN, filePath);
        System.out.println("Deployed " + filePath + " -> processDefinitionName=" + processDefinitionName);
    }
}