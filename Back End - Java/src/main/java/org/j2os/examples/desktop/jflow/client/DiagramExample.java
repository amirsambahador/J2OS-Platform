package org.j2os.examples.desktop.jflow.client;

import org.j2os.platform.jflow.client.JFlowClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Demonstrates JFlow's diagram-rendering endpoints: deploying a dedicated diagram-sample
 * process, rendering a highlighted diagram of a running instance, and rendering a plain
 * diagram of the process definition.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class DiagramExample {

    /**
     * The RMI URL of the JFlow server to connect to.
     */
    private static final String RMI_URL = "rmi://localhost:1099/jbpms";

    /**
     * The same token configured on the server side.
     */
    private static final String TOKEN = "CHANGE_ME_TOKEN";

    /**
     * Path, on the client machine's disk, of the diagram-sample BPMN file (kept separate
     * from the other bpmn-samples).
     */
    private static final String DIAGRAM_BPMN_PATH = "src/main/resources/bpmn-repository/diagram-sample.bpmn20.xml";

    /**
     * Runs the diagram example.
     *
     * @param args not used
     * @throws Exception if connecting, deploying, or starting the process fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Connecting to the server ===");
        JFlowClient client = new JFlowClient(RMI_URL, TOKEN);
        System.out.println("Connected.");

        // Step 1: deploy the diagram-sample process (without touching the other sample
        // processes deployed by ServerExample).
        String bpmnXml = new String(Files.readAllBytes(Paths.get(DIAGRAM_BPMN_PATH)));
        String processDefinitionName = client.deployFromSourceAndGetProcessDefinitionKey("diagram-sample.bpmn20.xml", bpmnXml);
        System.out.println("Deployed: diagram-sample.bpmn20.xml -> processDefinitionName=" + processDefinitionName);

        // Step 2: start an instance so it waits at the review step.
        String processInstanceId = client.startProcessAndGetProcessInstanceId(processDefinitionName);
        System.out.println("Process started, id: " + processInstanceId);

        // Step 3: diagram of the running instance (with the review activity highlighted).
        byte[] instanceDiagram = client.getProcessDiagramByProcessInstanceId(processInstanceId);
        Path instanceDiagramFile = Paths.get("diagram-instance-highlighted.png");
        Files.write(instanceDiagramFile, instanceDiagram);
        System.out.println("Running-instance image saved to: " + instanceDiagramFile.toAbsolutePath());

        // Step 4: "plain outline" diagram of the process definition (no highlighting at all).
        byte[] definitionDiagram = client.getProcessDiagramByProcessDefinitionKey(processDefinitionName);
        Path definitionDiagramFile = Paths.get("diagram-definition-plain.png");
        Files.write(definitionDiagramFile, definitionDiagram);
        System.out.println("Process definition outline image saved to: " + definitionDiagramFile.toAbsolutePath());
    }
}