package com.plugtree.training.droolsjbpm.eventdriven;

import java.util.Collection;
import java.util.Map;

import junit.framework.Assert;

import org.drools.core.common.DefaultFactHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.event.rule.DebugRuleRuntimeEventListener;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.plugtree.training.droolsjbpm.event.ServerDownEvent;
import com.plugtree.training.droolsjbpm.event.ServerUpEvent;
import com.plugtree.training.droolsjbpm.eventdriven.util.TestUtil;
import com.plugtree.training.droolsjbpm.model.Location;
import com.plugtree.training.droolsjbpm.model.LocationOutageWarning;
import com.plugtree.training.droolsjbpm.model.Server;
import com.plugtree.training.droolsjbpm.model.Server.Status;
import com.plugtree.training.droolsjbpm.model.ServerFailingWarning;
import com.plugtree.training.droolsjbpm.model.SupportTool;

/**
 * JUnit for testing the usage of Complex Event Processing for monitoring
 * Servers Status.<br />
 * 
 * @author ezegrande
 * 
 */
public class ServerMonitoringTest {
    private Logger logger = LoggerFactory.getLogger(ServerMonitoringTest.class);
    private KieSession droolsSession;

    /**
     * Initializes resources
     */
    @Before
    public void beforeEachTest() {
        droolsSession = TestUtil.createDroolsSession();
        droolsSession.setGlobal("logger", logger);
    }

    /**
     * Disposes resources
     */
    @After
    public void afterEachTest() {
        TestUtil.dispose(droolsSession);
    }

    /**
     * Asserts that there is a ServerUpEvent in the Working Memory when a Server
     * is started up.
     * 
     */
    @Test
    public void testServerUpEvents() {
        Server serverAmerica = new Server("1.2.3.4", Location.AMERICA);
        serverAmerica.startUp();
        Assert.assertEquals(Status.UP, serverAmerica.getStatus());
        droolsSession.insert(serverAmerica);
        droolsSession.fireAllRules();
        Collection<FactHandle> upEventsFactHandles = droolsSession.getEntryPoint(
                SupportTool.SERVER_MONITORING.getEntryPointName()).getFactHandles(new ClassObjectFilter(ServerUpEvent.class));
        // Assert 1 server up event has been added
        Assert.assertEquals(1, upEventsFactHandles.size());
        for (FactHandle handle : upEventsFactHandles) {
            ServerUpEvent event = (ServerUpEvent) ((DefaultFactHandle) handle).getObject();
            logger.info("Server up event found: {}", event.getServer().getLocation());
        }
        Collection<FactHandle> downEventsFactHandles = droolsSession.getEntryPoint(
                SupportTool.SERVER_MONITORING.getEntryPointName()).getFactHandles(new ClassObjectFilter(ServerDownEvent.class));
        // Assert no server down events have been added
        Assert.assertEquals(0, downEventsFactHandles.size());
    }

    /**
     * Asserts that there is a ServerDownEvent in the Working Memory when a
     * Server is shut down.
     */
    @Test
    public void testServerDownEvents() {
        Server serverEurope = new Server("5.5.5.5", Location.EUROPE);
        serverEurope.shutDown();
        Assert.assertEquals(Status.DOWN, serverEurope.getStatus());
        droolsSession.insert(serverEurope);
        droolsSession.fireAllRules();
        Collection<FactHandle> downEventsFactHandles = droolsSession.getEntryPoint(
                SupportTool.SERVER_MONITORING.getEntryPointName()).getFactHandles(new ClassObjectFilter(ServerDownEvent.class));
        // Assert 1 server down event has been added
        Assert.assertEquals(1, downEventsFactHandles.size());
        for (FactHandle handle : downEventsFactHandles) {
            ServerDownEvent event = (ServerDownEvent) ((DefaultFactHandle) handle).getObject();
            logger.info("Server down event found: {}", event.getServer().getLocation());
        }
        Collection<FactHandle> upEventsFactHandles = droolsSession.getEntryPoint(
                SupportTool.SERVER_MONITORING.getEntryPointName()).getFactHandles(new ClassObjectFilter(ServerUpEvent.class));
        // Assert no server down events have been added
        Assert.assertEquals(0, upEventsFactHandles.size());
    }

    /**
     * Asserts that there is a LocationOutageWarning in the Working Memory when
     * all the servers of a Location are down, and the ServerDownEvent had
     * happened in a window of 8 minutes.
     */
    @Test
    public void testLocationOutage_No_Process() {
        droolsSession.addEventListener(new DebugRuleRuntimeEventListener());
        // Europe: 3 servers
        Server serverEurope1 = new Server("5.5.5.1", Location.EUROPE);
        Server serverEurope2 = new Server("5.5.5.2", Location.EUROPE);
        Server serverEurope3 = new Server("5.5.5.3", Location.EUROPE);
        // America: 2 servers
        Server serverAmerica1 = new Server("1.1.1.1", Location.AMERICA);
        Server serverAmerica2 = new Server("1.1.1.2", Location.AMERICA);
        // Oceania: 1 server
        Server serverOceania1 = new Server("3.1.1.1", Location.OCEANIA);

        // Start up all servers
        TestUtil.startUpServers(serverEurope1, serverEurope2, serverEurope2, serverEurope3, serverAmerica1, serverAmerica2,
                serverOceania1);

        // Insert all servers into Working Memory and keep a reference of their
        // FactHandle
        Map<Object, FactHandle> factHandles = TestUtil.insertAll(droolsSession, serverEurope1, serverEurope2, serverEurope2,
                serverEurope3, serverAmerica1, serverAmerica2, serverOceania1, Location.EUROPE, Location.AMERICA,
                Location.OCEANIA);

        // Fire all rules!
        droolsSession.fireAllRules();

        // Assert there are no Warnings for Location Outages
        Collection<FactHandle> locationOutageFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(
                LocationOutageWarning.class));
        Assert.assertEquals(0, locationOutageFactHandles.size());

        // Shut down one server in Europe (2 servers are still up)
        serverEurope1.shutDown();
        droolsSession.update(factHandles.get(serverEurope1), serverEurope1);
        droolsSession.fireAllRules();

        // Assert there are no Warnings for Location Outages
        locationOutageFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(LocationOutageWarning.class));
        Assert.assertEquals(0, locationOutageFactHandles.size());

        // Shut down another server in Europe (1 server is still up)
        serverEurope2.shutDown();
        droolsSession.update(factHandles.get(serverEurope2), serverEurope2);
        droolsSession.fireAllRules();

        // Assert there are no Warnings for Location Outages
        locationOutageFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(LocationOutageWarning.class));
        Assert.assertEquals(0, locationOutageFactHandles.size());

        // Shut down the only server that it is still running in Europe
        serverEurope3.shutDown();
        droolsSession.update(factHandles.get(serverEurope3), serverEurope3);
        droolsSession.fireAllRules();

        // Assert there are is a Warning for Location Outage in Europe
        locationOutageFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(LocationOutageWarning.class));
        Assert.assertEquals(1, locationOutageFactHandles.size());
        for (FactHandle handle : locationOutageFactHandles) {
            LocationOutageWarning event = (LocationOutageWarning) ((DefaultFactHandle) handle).getObject();
            logger.info("Location outage found: {}", event.getLocation());
            Assert.assertEquals(Location.EUROPE, event.getLocation());
        }

    }

    @Test
    public void testServerFailing() {
        droolsSession.addEventListener(new DebugRuleRuntimeEventListener());
        // Europe: 3 servers
        Server serverEurope = new Server("5.5.5.1", Location.EUROPE);
        Server serverAmerica = new Server("1.1.1.1", Location.AMERICA);
        Server serverOceania = new Server("3.1.1.1", Location.OCEANIA);

        // Start up all servers
        TestUtil.startUpServers(serverEurope, serverAmerica, serverOceania);

        // Insert all servers into Working Memory and keep a reference of their
        // FactHandle
        Map<Object, FactHandle> factHandles = TestUtil.insertAll(droolsSession, serverEurope, serverAmerica, serverOceania);

        // Fire all rules!
        droolsSession.fireAllRules();

        // Assert there are no Warnings for Server Failing
        Collection<FactHandle> serverFailingFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(
                ServerFailingWarning.class));
        Assert.assertEquals(0, serverFailingFactHandles.size());

        // Shut down 4 servers (keep 1 running)
        for (int i = 0; i < 4; i++) {
            serverEurope.shutDown();
            droolsSession.update(factHandles.get(serverEurope), serverEurope);
            droolsSession.fireAllRules();
        }

        // Assert there are no Warnings for Server Failing
        serverFailingFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(ServerFailingWarning.class));
        Assert.assertEquals(0, serverFailingFactHandles.size());

        // Shut down the last server
        serverEurope.shutDown();
        droolsSession.update(factHandles.get(serverEurope), serverEurope);
        droolsSession.fireAllRules();

        // Assert there are no Warnings for Server Failing Outages
        serverFailingFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(ServerFailingWarning.class));
        Assert.assertEquals(1, serverFailingFactHandles.size());
        for (FactHandle handle : serverFailingFactHandles) {
            ServerFailingWarning event = (ServerFailingWarning) ((DefaultFactHandle) handle).getObject();
            logger.info("Server failing event found: {}", event.getServer().getLocation());
            Assert.assertEquals(serverEurope, event.getServer());
        }

    }
}