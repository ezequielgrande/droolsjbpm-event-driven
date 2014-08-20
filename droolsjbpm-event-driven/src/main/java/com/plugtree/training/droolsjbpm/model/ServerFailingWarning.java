package com.plugtree.training.droolsjbpm.model;

public class ServerFailingWarning {
    private Server server;

    public ServerFailingWarning(Server server) {
        this.server = server;
    }

    public String toString() {
        return "WARNING: Server seems to be failing. Please, check server " + server;
    }
    
    public Server getServer() {
        return server;
    }
}
