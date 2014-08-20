package com.plugtree.training.droolsjbpm.model;

public class LocationOutageWarning {
    private Location location;

    public LocationOutageWarning(Location location) {
        this.location = location;
    }

    public String toString() {
        return "WARNING: Location outage. Please check servers located in " + location;
    }

    public Location getLocation() {
        return location;
    }
}
