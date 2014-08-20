package com.plugtree.training.droolsjbpm.eventdriven;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.drools.core.common.DefaultFactHandle;
import org.drools.core.time.SessionPseudoClock;
import org.jbpm.services.task.wih.LocalHTWorkItemHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.event.rule.DebugRuleRuntimeEventListener;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.resource.jdbc.PoolingDataSource;

import com.plugtree.training.droolsjbpm.eventdriven.listener.InsertProcessEventListener;
import com.plugtree.training.droolsjbpm.eventdriven.util.TestUtil;
import com.plugtree.training.droolsjbpm.model.Location;
import com.plugtree.training.droolsjbpm.model.LocationOutageWarning;
import com.plugtree.training.droolsjbpm.model.ProcessOverloadWarning;
import com.plugtree.training.droolsjbpm.model.Server;
import com.plugtree.training.droolsjbpm.model.SupportTool;

/**
 * JUnit for testing the usage of Complex Event Processing for monitoring
 * Business Processes.<br />
 * For this example, a Business Rule monitors the Start Events of a Process. If
 * there are more than 100 starts events within an hour, inserts a Warning into
 * the Working Memory.
 * 
 * @author ezegrande
 * 
 */
public class ClaimProcessTest {
    private Logger logger = LoggerFactory.getLogger(ClaimProcessTest.class.getName());
    private static final String KIE_PKG_BPMN = "com.plugtree.training.droolsjbpm_event_driven.bpmn";
    private static final String KIE_PKG_DRL = "com.plugtree.training.droolsjbpm_event_driven.drl";
    private static final String PROCESS_ID = "com.plugtree.training.claimProcess";
    private static final String HUMAN_TASK = "Human Task";

    // Process variables
    private static final String VAR_LOCATION = "location";
    private static final String VAR_CUSTOMER_NAME = "customerName";
    private static final String VAR_START_DATE = "startDate";
    private static final String VAR_END_DATE = "endDate";
    private static final String VAR_OPERATOR = "operator";
    private static final String VAR_STATUS = "status";
    private static final String VAR_INSPECTOR = "inspector";

    // Claim status
    private static final String CLAIM_STATUS_OPEN = "OPEN";
    private static final String CLAIM_STATUS_CLOSED = "CLOSED";

    // Users & groups
    private static final String GROUP_TECH = "tech";
    private static final String GROUP_SUPPORT = "support";
    private static final String USR_TECH_PAUL = "paul";
    private static final String USR_SUPPORT_JOHN = "john";
    private static final String USR_SERVER_MONITORING = "serverMonitoring";
    private static Properties userGroups;

    // Test resources
    private static PoolingDataSource ds;
    private KieSession droolsSession;
    private RuntimeManager runtimeManager;
    private RuntimeEngine runtimeEngine;

    /**
     * Initializes Persistence resources & user groups
     */
    @BeforeClass
    public static void setup() {
        ds = new PoolingDataSource();
        ds.setUniqueName("jdbc/testDS");
        ds.setClassName("org.h2.jdbcx.JdbcDataSource");
        ds.setMaxPoolSize(3);
        ds.setAllowLocalTransactions(true);
        ds.getDriverProperties().setProperty("URL", "jdbc:h2:tasks;MVCC=true;DB_CLOSE_ON_EXIT=FALSE");
        ds.getDriverProperties().setProperty("user", "sa");
        ds.getDriverProperties().setProperty("password", "sasa");
        ds.init();

        userGroups = new Properties();
        userGroups.put(USR_SUPPORT_JOHN, GROUP_SUPPORT);
        userGroups.put(USR_TECH_PAUL, GROUP_TECH);
    }

    @AfterClass
    public static void cleanup() {
        if (ds != null) {
            ds.close();
        }
    }

    /**
     * Initializes resources
     */
    @Before
    public void beforeEachTest() {
        droolsSession = TestUtil.createDroolsSession();
        // Add logger to Drools Session
        Logger testLog = LoggerFactory.getLogger(ClaimProcessTest.class.getName() + ".testProcess.droolsSession");
        droolsSession.setGlobal("logger", testLog);

        runtimeManager = TestUtil.createProcessRuntimeManager(userGroups);
        // RuntumeEngine
        runtimeEngine = runtimeManager.getRuntimeEngine(EmptyContext.get());
    }

    /**
     * Disposes resources
     */
    @After
    public void afterEachTest() {
        TestUtil.dispose(droolsSession);
        TestUtil.disposeAndClose(runtimeManager, runtimeEngine);
    }

    /**
     * Tests that the Drools Session and the jBPM Session are independent from
     * each other
     */
    @Test
    public void testSessions() {
        // Assert that the sessions are independent from each other
        assertIndependentSessions(droolsSession, runtimeEngine.getKieSession());
    }

    /**
     * Test the full flow of the process, including the human tasks, without the
     * usage of the Drools session
     */
    @Test
    public void testCompleteProcess() {
        // KieSession
        KieSession jbpmSession = runtimeEngine.getKieSession();
        TaskService taskService = runtimeEngine.getTaskService();
        LocalHTWorkItemHandler htHandler = new LocalHTWorkItemHandler();
        htHandler.setRuntimeManager(runtimeManager);
        jbpmSession.getWorkItemManager().registerWorkItemHandler(HUMAN_TASK, htHandler);

        startAndCompleteProcess(jbpmSession, taskService);
    }

    /**
     * Starts a Process Instance and completes the full flow of it
     * 
     * @param jbpmSession
     * @param taskService
     */
    private void startAndCompleteProcess(KieSession jbpmSession, TaskService taskService) {
        WorkflowProcessInstance pi = (WorkflowProcessInstance) jbpmSession.startProcess(PROCESS_ID);
        logger.info("Started process {}, instance id={}", PROCESS_ID, pi.getId());
        // Process should be Active, awaiting for Human Task 'Receive Claim'
        Assert.assertEquals(ProcessInstance.STATE_ACTIVE, pi.getState());

        // Customer info
        String customerName = "Tom Smith";
        Location customerLocation = Location.AMERICA;
        // John will receive a Claim from the Customer
        receiveClaim(taskService, pi.getId(), USR_SUPPORT_JOHN, new Date(), customerName, customerLocation, CLAIM_STATUS_OPEN);
        // Paul will make the Technical Inspection, to solve the claim
        technicalInspection(taskService, pi.getId(), USR_TECH_PAUL, CLAIM_STATUS_CLOSED);

        // Process should be Completed
        Assert.assertEquals(ProcessInstance.STATE_COMPLETED, pi.getState());
        logger.info("Completed process {}, instance id={}", PROCESS_ID, pi.getId());

        // Assert Process variables
        Assert.assertEquals(USR_SUPPORT_JOHN, pi.getVariable(VAR_OPERATOR));
        Assert.assertEquals(USR_TECH_PAUL, pi.getVariable(VAR_INSPECTOR));
        Assert.assertEquals(customerName, pi.getVariable(VAR_CUSTOMER_NAME));
        Assert.assertEquals(customerLocation.toString(), pi.getVariable(VAR_LOCATION));
        Assert.assertEquals(CLAIM_STATUS_CLOSED, pi.getVariable(VAR_STATUS));
        Assert.assertNotNull(pi.getVariable(VAR_START_DATE));
        Assert.assertNotNull(pi.getVariable(VAR_END_DATE));
        Assert.assertTrue(((Date) pi.getVariable(VAR_START_DATE)).before((Date) pi.getVariable(VAR_END_DATE)));
    }

    /**
     * Test that if there are more than 100 process instances during an hour, a
     * warning was triggered by the rules
     * 
     */
    @Test
    public void test100ProcessInLessThanOneHour_AllInProgress() {
        // KieSession
        KieSession jbpmSession = runtimeEngine.getKieSession();

        // Assert that the sessions are independent from each other
        assertIndependentSessions(droolsSession, jbpmSession);

        // Add Event Listener for inserting the process instance into the Drools
        // Session
        jbpmSession.addEventListener(new InsertProcessEventListener(droolsSession, SupportTool.CUSTOMER_SUPPORT));

        // Start 100 process instances
        startNInstances(100, jbpmSession);

        // Start Process #101, so a warning should be triggered
        logger.info("Starting process instance #101...");
        jbpmSession.startProcess(PROCESS_ID);
        Collection<FactHandle> warningFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(
                ProcessOverloadWarning.class));
        // Assert 1 warning has been added
        Assert.assertEquals(1, warningFactHandles.size());
        for (FactHandle handle : warningFactHandles) {
            ProcessOverloadWarning warning = (ProcessOverloadWarning) ((DefaultFactHandle) handle).getObject();
            logger.info("Warning found: {}", warning);
        }
    }

    /**
     * Test that if there are more than 100 process instances during an hour, a
     * warning was triggered by the rules ONLY if the 100 process instances are
     * In Progress. In this example we will Complete some of the processes.
     * 
     */
    @Test
    public void test100ProcessInLessThanOneHour_NotAllInProgress() {
        // KieSession
        KieSession jbpmSession = runtimeEngine.getKieSession();
        TaskService taskService = runtimeEngine.getTaskService();

        // Add Event Listener for inserting the process instance into the Drools
        // Session
        // jbpmSession.addEventListener(new
        // InsertProcessEventListener(droolsSession,
        // SupportTool.CUSTOMER_SUPPORT));

        // Start 99 process instances
        logger.info("Starting 99 process instances...");
        startNInstances(99, jbpmSession);

        // Start & Complete Process Instance #100
        logger.info("Starting and Completing 1 process instance...");
        startAndCompleteProcess(jbpmSession, taskService);

        // Start Process #101. Currently there are 99 active instances. 99 + 1 =
        // 100. Warning should NOT been triggered
        logger.info("Starting process instance #101...");
        jbpmSession.startProcess(PROCESS_ID);
        Collection<FactHandle> warningFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(
                ProcessOverloadWarning.class));
        // Assert NO warning have been added
        Assert.assertEquals(0, warningFactHandles.size());
    }

    /**
     * Test that if there are more than 100 process instances that are not
     * created within an hour, a warning was NOT triggered by the rules
     * 
     */
    @Test
    public void test100ProcessInMoreThanOneHour_AllInProgress() {
        KieSession jbpmSession = runtimeEngine.getKieSession();

        // Add Event Listener for inserting the process instance into the Drools
        // Session
        jbpmSession.addEventListener(new InsertProcessEventListener(droolsSession, SupportTool.CUSTOMER_SUPPORT));

        // Pseudo Clock
        // ____00____30 min____1h 01m____
        // ....^^........................
        //
        // Start 50 process instances
        startNInstances(50, jbpmSession);

        // Advance clock in 30 minutes
        // ____00____30 min____1h 01m____
        // ............^^................
        //
        SessionPseudoClock clock = droolsSession.getSessionClock();
        logger.info("Advancing time in 30'...");
        clock.advanceTime(30, TimeUnit.MINUTES);

        // Start 50 process instances. Total count = 100 instances
        startNInstances(50, jbpmSession);

        // Advance clock in 31 minutes
        // ____00____30 min____1h 01m____
        // ......................^^-.....
        //
        logger.info("Advancing time in 31'...");
        clock.advanceTime(31, TimeUnit.MINUTES);

        // Now that the clock had moved on, the first 50 process are not more
        // taken into account by the rule

        // Start 1 process instance. Total count = 101 instances. But in the
        // last hour: 51 processes. So, Warning should not be triggered.
        logger.info("Starting process instance #101...");
        startNInstances(1, jbpmSession);

        // Validate that there are no Warnings in the Working Memory
        Collection<FactHandle> warningFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(
                ProcessOverloadWarning.class));
        // Assert 0 warning have been added
        Assert.assertEquals(0, warningFactHandles.size());
    }

    /**
     * Tests that when the Business Rules detect a Location Outage, a Process
     * instance is automatically generated and there is a Human Task available
     * for an operator. This test also completes the process, by working on the
     * Human tasks.
     */
    @Test
    public void testLocationOutage_TriggerProcess() {
        // KieSession
        KieSession jbpmSession = runtimeEngine.getKieSession();
        TaskService taskService = runtimeEngine.getTaskService();
        LocalHTWorkItemHandler htHandler = new LocalHTWorkItemHandler();
        htHandler.setRuntimeManager(runtimeManager);
        jbpmSession.getWorkItemManager().registerWorkItemHandler(HUMAN_TASK, htHandler);

        droolsSession.addEventListener(new DebugRuleRuntimeEventListener());
        droolsSession.setGlobal("jbpmSession", jbpmSession);
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

        // Assert there are no Process Instances
        Assert.assertEquals(0, jbpmSession.getProcessInstances().size());

        // Shut down one server in Europe (2 servers are still up)
        serverEurope1.shutDown();
        droolsSession.update(factHandles.get(serverEurope1), serverEurope1);
        droolsSession.fireAllRules();

        // Assert there are no Process Instances
        Assert.assertEquals(0, jbpmSession.getProcessInstances().size());

        // Shut down another server in Europe (1 server is still up)
        serverEurope2.shutDown();
        droolsSession.update(factHandles.get(serverEurope2), serverEurope2);
        droolsSession.fireAllRules();

        // Assert there are no Process Instances
        Assert.assertEquals(0, jbpmSession.getProcessInstances().size());

        // Shut down the only server that it is still running in Europe
        serverEurope3.shutDown();
        droolsSession.update(factHandles.get(serverEurope3), serverEurope3);
        droolsSession.fireAllRules();

        // Assert there is one Process Instance started
        Assert.assertEquals(1, jbpmSession.getProcessInstances().size());
        // Assert there are no Warnings for Location Outages
        Collection<FactHandle> locationOutageFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(
                LocationOutageWarning.class));
        Assert.assertEquals(0, locationOutageFactHandles.size());

        // Assert that the process is awaiting in the Receive Claim task
        WorkflowProcessInstance pi = (WorkflowProcessInstance) jbpmSession.getProcessInstances().iterator().next();
        List<Long> tasks = taskService.getTasksByProcessInstanceId(pi.getId());
        // Assert there is only one Task (Human task - Receive Claim)
        Assert.assertEquals(1, jbpmSession.getProcessInstances().size());
        Task task = taskService.getTaskById(tasks.get(0));
        Assert.assertEquals("Receive Claim", task.getName());
        Assert.assertEquals(Status.Ready, task.getTaskData().getStatus());

        Map<String, Object> inParams = taskService.getTaskContent(task.getId());
        Assert.assertNotNull(inParams.get(VAR_START_DATE));
        Assert.assertNull(inParams.get(VAR_END_DATE));
        Assert.assertEquals(USR_SERVER_MONITORING, inParams.get(VAR_CUSTOMER_NAME));
        Assert.assertEquals(Location.EUROPE.toString(), inParams.get(VAR_LOCATION));
        Assert.assertEquals("OPEN", inParams.get(VAR_STATUS));

        // John will work on the Process automatically created by the Business
        // Rules
        receiveClaim(taskService, pi.getId(), USR_SUPPORT_JOHN, (Date) inParams.get(VAR_START_DATE),
                (String) inParams.get(VAR_CUSTOMER_NAME), Location.valueOf((String) inParams.get(VAR_LOCATION)),
                (String) inParams.get(VAR_STATUS));

        // Paul will restart the servers
        serverEurope1.startUp();
        serverEurope2.startUp();
        serverEurope3.startUp();
        // Update servers in drools session
        droolsSession.update(factHandles.get(serverEurope1), serverEurope1);
        droolsSession.update(factHandles.get(serverEurope2), serverEurope2);
        droolsSession.update(factHandles.get(serverEurope3), serverEurope3);
        // Fire all rules
        droolsSession.fireAllRules();
        // Work on the Process task
        technicalInspection(taskService, pi.getId(), USR_TECH_PAUL, CLAIM_STATUS_CLOSED);

        // Process should be Completed
        Assert.assertEquals(ProcessInstance.STATE_COMPLETED, pi.getState());
        logger.info("Completed process {}, instance id={}", PROCESS_ID, pi.getId());

        // Assert Process variables
        Assert.assertEquals(USR_SUPPORT_JOHN, pi.getVariable(VAR_OPERATOR));
        Assert.assertEquals(USR_TECH_PAUL, pi.getVariable(VAR_INSPECTOR));
        Assert.assertEquals(USR_SERVER_MONITORING, pi.getVariable(VAR_CUSTOMER_NAME));
        Assert.assertEquals(Location.EUROPE.toString(), pi.getVariable(VAR_LOCATION));
        Assert.assertEquals(CLAIM_STATUS_CLOSED, pi.getVariable(VAR_STATUS));
        Assert.assertNotNull(pi.getVariable(VAR_START_DATE));
        Assert.assertNotNull(pi.getVariable(VAR_END_DATE));
        Assert.assertTrue(((Date) pi.getVariable(VAR_START_DATE)).before((Date) pi.getVariable(VAR_END_DATE)));

        // Assert there are no more Process Instances
        Assert.assertEquals(0, jbpmSession.getProcessInstances().size());
    }

    /**
     * Starts n instances of the Process 'com.plugtree.training.claimProcess'
     * 
     * @param n
     *            number of instances
     * @param jbpmSession
     */
    private void startNInstances(int n, KieSession jbpmSession) {
        logger.info("Starting {} process instances...", n);
        for (int i = 0; i < n; i++) {
            jbpmSession.startProcess(PROCESS_ID);
        }
    }

    /**
     * Asserts that the sessions are independent and one is for handling rules
     * and the other one for handling processes:
     * <ul>
     * <li>Drools Session has no processes
     * <li>Drools Session has one rule
     * <li>jBPM Session has one process
     * <li>jBPM Session has no rules
     * <li>Drools Session insertions are not inserted into jBPM Session
     * <li>jBPM Session insertions are not inserted into Drools Session
     * </ul>
     * 
     * @param droolsSession
     * @param jbpmSession
     */
    private void assertIndependentSessions(KieSession droolsSession, KieSession jbpmSession) {
        // No Processes in the Drools Session
        Assert.assertEquals(0, droolsSession.getKieBase().getProcesses().size());
        Assert.assertNotNull(droolsSession.getKieBase().getKiePackage(KIE_PKG_DRL));
        Assert.assertNull(droolsSession.getKieBase().getKiePackage(KIE_PKG_BPMN));

        // One Process in the jBPM Session
        Assert.assertEquals(1, jbpmSession.getKieBase().getProcesses().size());

        // No Rules in the jBPM Session
        Assert.assertNotNull(jbpmSession.getKieBase().getKiePackage(KIE_PKG_BPMN));
        Assert.assertNull(jbpmSession.getKieBase().getKiePackage(KIE_PKG_DRL));

        // Assert both sessions are empty
        Assert.assertEquals(0, droolsSession.getFactCount());
        Assert.assertEquals(0, jbpmSession.getFactCount());

        // Insert in Drools Session
        FactHandle droolsHandle = droolsSession.insert("Drools");
        Assert.assertEquals(1, droolsSession.getFactCount());
        Assert.assertEquals(0, jbpmSession.getFactCount());

        // Insert in jBPM Session
        FactHandle jbpmHandle = jbpmSession.insert("jBPM");
        Assert.assertEquals(1, droolsSession.getFactCount());
        Assert.assertEquals(1, jbpmSession.getFactCount());

        // Retract facts
        droolsSession.delete(droolsHandle);
        jbpmSession.delete(jbpmHandle);

        // Assert both sessions are empty
        Assert.assertEquals(0, droolsSession.getFactCount());
        Assert.assertEquals(0, jbpmSession.getFactCount());
        logger.info("Sessions are independant...assert ok!");
    }

    /**
     * This method will Claim, Start and Complete the Task 'Receive Claim'. It
     * will set the output parameters of the task:
     * <ul>
     * <li>Customer Name
     * <li>Location
     * <li>Operator
     * <li>Status (OPEN)
     * <li>Start Date (now)
     * <li>End Date (null)
     * </ul>
     * 
     * Returns the completed Task.
     * 
     * @param taskService
     *            the TaskService
     * @param supportOperator
     *            Name of the operator, who will work on the task
     * @param customerName
     *            Name of the Customer
     * @param customerLocation
     *            Location of the Customer
     * @return the completed Task
     */
    private Task receiveClaim(TaskService taskService, long processId, String supportOperator, Date startDate,
            String customerName, Location customerLocation, String status) {
        // Prepare task's output data: Insert customerName, location, operator
        // name, start date (today), endDate (null)
        Map<String, Object> taskOutParams = new HashMap<String, Object>();
        taskOutParams.put(VAR_CUSTOMER_NAME, customerName);
        taskOutParams.put(VAR_LOCATION, customerLocation.toString());
        taskOutParams.put(VAR_OPERATOR, supportOperator);
        taskOutParams.put(VAR_START_DATE, startDate);
        taskOutParams.put(VAR_END_DATE, null);
        taskOutParams.put(VAR_STATUS, status);

        return TestUtil.workOnTask(taskService, processId, supportOperator, taskOutParams);
    }

    /**
     * This method will Claim, Start and Complete the Task 'Technical
     * Inspection'. It will set the output parameters of the task:
     * <ul>
     * <li>Status
     * <li>End Date (now)
     * </ul>
     * 
     * Returns the completed Task.
     * 
     * @param taskService
     *            the TaskService
     * @param status
     *            Final status of the claim
     * @return the completed Task
     */
    private Task technicalInspection(TaskService taskService, long processInstanceId, String techOperator, String status) {
        // Prepare task's output data: status, endDate (now)
        Map<String, Object> taskOutParams = new HashMap<String, Object>();
        taskOutParams.put(VAR_OPERATOR, techOperator);
        taskOutParams.put(VAR_END_DATE, new Date());
        taskOutParams.put(VAR_STATUS, status);
        taskOutParams.put(VAR_INSPECTOR, techOperator);
        return TestUtil.workOnTask(taskService, processInstanceId, techOperator, taskOutParams);
    }

}
