package com.plugtree.training.droolsjbpm.model;

public enum SupportTool {
    CUSTOMER_SUPPORT("customer-support"), SERVER_MONITORING("server-monitoring");
    
    private String entryPointName;
    
    private SupportTool(String entryPointName) {
        this.entryPointName = entryPointName;
    }
    
    public String getEntryPointName() {
        return entryPointName;
    }
    
}
