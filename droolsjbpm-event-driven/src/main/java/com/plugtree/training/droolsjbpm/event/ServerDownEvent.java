package com.plugtree.training.droolsjbpm.event;

import com.plugtree.training.droolsjbpm.model.Server;

public class ServerDownEvent {
    private Server server;

    public ServerDownEvent(Server server) {
        this.server = server;
    }
    
    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    
}
