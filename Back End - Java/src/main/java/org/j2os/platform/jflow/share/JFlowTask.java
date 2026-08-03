package org.j2os.platform.jflow.share;

import java.io.Serializable;
import java.util.Date;

/**
 * Lightweight, serializable descriptor of a Flowable user task, as returned by the
 * task-query methods of {@code org.j2os.platform.jflow.share.JFlowRemote}.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JFlowTask implements Serializable {

    /**
     * The id of the underlying Flowable task.
     */
    private String taskId;

    /**
     * The user or group this task is currently assigned to.
     */
    private String assignee;

    /**
     * When this task was created.
     */
    private Date createTime;

    /**
     * The key of the process definition this task belongs to.
     */
    private String processDefinitionKey;

    /**
     * The id of the process instance this task belongs to.
     */
    private String processInstanceId;

    /**
     * This task's priority.
     */
    private int priority;

    /**
     * Returns the id of the underlying Flowable task.
     *
     * @return the task id
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Sets the id of the underlying Flowable task.
     *
     * @param taskId the task id
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * Returns the user or group this task is currently assigned to.
     *
     * @return the assignee
     */
    public String getAssignee() {
        return assignee;
    }

    /**
     * Sets the user or group this task is currently assigned to.
     *
     * @param assignee the assignee
     */
    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    /**
     * Returns when this task was created, as a defensive copy.
     *
     * @return the creation time, or {@code null} if not set
     */
    public Date getCreateTime() {
        return createTime == null ? null : new Date(createTime.getTime());
    }

    /**
     * Sets when this task was created, storing a defensive copy.
     *
     * @param createTime the creation time
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime == null ? null : new Date(createTime.getTime());
    }

    /**
     * Returns the key of the process definition this task belongs to.
     *
     * @return the process definition key
     */
    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    /**
     * Sets the key of the process definition this task belongs to.
     *
     * @param processDefinitionKey the process definition key
     */
    public void setProcessDefinitionKey(String processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
    }

    /**
     * Returns the id of the process instance this task belongs to.
     *
     * @return the process instance id
     */
    public String getProcessInstanceId() {
        return processInstanceId;
    }

    /**
     * Sets the id of the process instance this task belongs to.
     *
     * @param processInstanceId the process instance id
     */
    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    /**
     * Returns this task's priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets this task's priority.
     *
     * @param priority the priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Returns a debug-friendly string representation of this task.
     *
     * @return a string containing all of this task's fields
     */
    @Override
    public String toString() {
        return "JFlowTask{" +
                "taskId='" + taskId + '\'' +
                ", assignee='" + assignee + '\'' +
                ", createTime=" + createTime +
                ", processDefinitionKey='" + processDefinitionKey + '\'' +
                ", processInstanceId='" + processInstanceId + '\'' +
                ", priority=" + priority +
                '}';
    }
}