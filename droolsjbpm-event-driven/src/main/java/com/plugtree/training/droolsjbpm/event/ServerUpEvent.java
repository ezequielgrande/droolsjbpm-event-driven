package com.plugtree.training.droolsjbpm.event;

import com.plugtree.training.droolsjbpm.model.Server;

public class ServerUpEvent {
    private Server server;

    public ServerUpEvent(Server server) {
        this.server = server;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

}
