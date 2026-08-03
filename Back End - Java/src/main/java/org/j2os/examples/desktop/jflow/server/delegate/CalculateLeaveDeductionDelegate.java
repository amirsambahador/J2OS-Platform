package org.j2os.examples.desktop.jflow.server.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

/**
 * A plain Java method invoked directly from BPMN (as a service task).
 * <p>
 * Input: the process variable named {@code "days"} (number of leave days).
 * Output: the process variable named {@code "deductionAmount"} (the salary deduction amount).
 * <p>
 * Example: if {@code days} is 3, {@code deductionAmount} becomes 3 x 500000 = 1500000.
 * <p>
 * To invoke this from BPMN, set the following on a service task:
 * {@code flowable:class="org.j2os.platform.test.example.jflow.server.delegate.CalculateLeaveDeductionDelegate"}
 * <p>
 * Delegate classes such as this one must reside on the server, since they are loaded
 * and invoked by the process engine itself, not by clients.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class CalculateLeaveDeductionDelegate implements JavaDelegate {

    /**
     * Deduction amount per leave day (example value only; could be made configurable in a real project).
     */
    private static final int DAILY_RATE = 500_000;

    /**
     * Reads the {@code days} process variable, computes the deduction amount, and stores
     * it back on the process as {@code deductionAmount}.
     *
     * @param execution the delegate execution context this service task is running in
     */
    @Override
    public void execute(DelegateExecution execution) {
        Object daysVariable = execution.getVariable("days");
        int days = daysVariable == null ? 0 : ((Number) daysVariable).intValue();

        int deductionAmount = days * DAILY_RATE;

        // This method's output is set as a new variable on the same process instance;
        // the next step (BuildLeaveSummaryDelegate) reads this same variable as its input.
        execution.setVariable("deductionAmount", deductionAmount);
    }
}