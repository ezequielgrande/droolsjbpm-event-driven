package com.plugtree.training.droolsjbpm.model;

public class Server {
    private String ipAddress;
    private Status status = Status.DOWN;
    private Location location;

    public Server(String ipAddress, Location location) {
        this.ipAddress = ipAddress;
        this.location = location;
    }

    public void startUp() {
        this.status = Status.UP;
    }

    public void shutDown() {
        this.status = Status.DOWN;
    }

    /*
     * Getters & setters
     */

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    /*
     * Enumerations
     */

    public enum Status {
        UP, DOWN;
    }

    public String toString() {
        return "IP Address: " + ipAddress + " - Location: " + location + " - Status: " + status;
    }
}
