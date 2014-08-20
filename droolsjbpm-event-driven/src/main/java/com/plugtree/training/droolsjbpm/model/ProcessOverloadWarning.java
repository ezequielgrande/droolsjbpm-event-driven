package com.plugtree.training.droolsjbpm.model;

public class ProcessOverloadWarning {
    private String processId;
    private String message;

    public ProcessOverloadWarning(String processId, String message) {
        this.processId = processId;
        this.message = message;
    }

    public String toString() {
        return "WARNING: Process '" + processId + "' seems to be overloaded. " + message;
    }
    
    public String getProcessId() {
        return processId;
    }
}
