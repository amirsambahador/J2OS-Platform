package org.j2os.examples.desktop.jflow.server.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

/**
 * The second Java method in this process; its input is exactly the output of the
 * previous method ({@link CalculateLeaveDeductionDelegate}).
 * <p>
 * Input: the process variable named {@code "deductionAmount"} (set by the previous service task).
 * Output: the process variable named {@code "summaryMessage"} (a text summary message).
 * <p>
 * Example: if {@code deductionAmount} is 1500000, {@code summaryMessage} becomes:
 * "Salary deduction for leave: 1500000 Rials"
 * <p>
 * Delegate classes such as this one must reside on the server, since they are loaded
 * and invoked by the process engine itself, not by clients.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class BuildLeaveSummaryDelegate implements JavaDelegate {

    /**
     * Reads the {@code deductionAmount} process variable and stores a text summary of it
     * back on the process as {@code summaryMessage}.
     *
     * @param execution the delegate execution context this service task is running in
     */
    @Override
    public void execute(DelegateExecution execution) {
        Object deductionAmount = execution.getVariable("deductionAmount");

        String summaryMessage = "Salary deduction for leave: " + deductionAmount + " Rials";

        execution.setVariable("summaryMessage", summaryMessage);
    }
}