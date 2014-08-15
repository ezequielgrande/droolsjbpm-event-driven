package com.plugtree.training.droolsjbpm.eventdriven;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.persistence.Persistence;

import junit.framework.Assert;

import org.drools.core.ClockType;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.time.SessionPseudoClock;
import org.jbpm.services.task.identity.JBossUserGroupCallbackImpl;
import org.jbpm.services.task.wih.LocalHTWorkItemHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message.Level;
import org.kie.api.builder.ReleaseId;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeEnvironment;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.resource.jdbc.PoolingDataSource;

import com.plugtree.training.droolsjbpm.WarningMessage;
import com.plugtree.training.droolsjbpm.eventdriven.listener.InsertProcessEventListener;

/**
 * JUnit for testing the usage of Complex Event Processing for monitoring
 * Business Processes.<br />
 * For this example, a Business Rule monitors the Start Events of a Process. If
 * there are more than 100 starts events within an hour, inserts a Warning
 * Message into the Working Memory.
 * 
 * @author ezegrande
 * 
 */
public class ClaimProcessTest {
    private static final String VAR_LOCATION = "location";
    private static final String VAR_CUSTOMER_NAME = "customerName";
    private static final String VAR_START_DATE = "startDate";
    private static final String VAR_END_DATE = "endDate";
    private static final String VAR_OPERATOR = "operator";
    private static final String VAR_STATUS = "status";
    private static final String CLAIM_STATUS_OPEN = "OPEN";
    private static final String CLAIM_STATUS_CLOSED = "CLOSED";
    private static final String EN_UK = "en-UK";
    private static final String GROUP_TECH = "tech";
    private static final String GROUP_SUPPORT = "support";
    private static final String USR_TECH_PAUL = "paul";
    private static final String USR_SUPPORT_JOHN = "john";
    private static final String PROCESS_ID = "com.plugtree.training.claimProcess";
    private static final String HUMAN_TASK = "Human Task";
    private Logger logger = LoggerFactory.getLogger(ClaimProcessTest.class.getName());
    private static PoolingDataSource ds;

    private KieSession droolsSession;
    private RuntimeManager runtimeManager;
    private RuntimeEngine runtimeEngine;

    /**
     * Initializes Persistence resources
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
        droolsSession = createDroolsSession();
        runtimeManager = createProcessRuntimeManager();
        // RuntumeEngine
        runtimeEngine = runtimeManager.getRuntimeEngine(EmptyContext.get());
    }

    /**
     * Disposes resources
     */
    @After
    public void afterEachTest() {
        dispose(droolsSession);
        disposeAndClose(runtimeManager, runtimeEngine);
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
        String customerLocation = "US";
        // John will receive a Claim from the Customer
        receiveClaim(taskService, pi.getId(), USR_SUPPORT_JOHN, customerName, customerLocation);
        // Paul will make the Technical Inspection, to solve the claim
        technicalInspection(taskService, pi.getId(), USR_TECH_PAUL, CLAIM_STATUS_CLOSED);

        // Process should be Completed
        Assert.assertEquals(ProcessInstance.STATE_COMPLETED, pi.getState());
        logger.info("Completed process {}, instance id={}", PROCESS_ID, pi.getId());

        // Assert Process variables
        Assert.assertEquals(USR_SUPPORT_JOHN, pi.getVariable(VAR_OPERATOR));
        Assert.assertEquals(customerName, pi.getVariable(VAR_CUSTOMER_NAME));
        Assert.assertEquals(customerLocation, pi.getVariable(VAR_LOCATION));
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
        Logger testLog = LoggerFactory.getLogger(ClaimProcessTest.class.getName() + ".testProcess.droolsSession");

        // KieSession
        KieSession jbpmSession = runtimeEngine.getKieSession();

        // Assert that the sessions are independent from each other
        assertIndependentSessions(droolsSession, jbpmSession);

        // Add logger to Drools Session
        droolsSession.setGlobal("logger", testLog);
        // Add Event Listener for inserting the process instance into the Drools
        // Session
        jbpmSession.addEventListener(new InsertProcessEventListener(droolsSession));

        // Start 100 process instances
        startNInstances(100, jbpmSession);

        // Start Process #101, so a warning should be triggered
        logger.info("Starting process instance #101...");
        jbpmSession.startProcess(PROCESS_ID);
        Collection<FactHandle> msgFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(WarningMessage.class));
        // Assert 1 warning message has been added
        Assert.assertEquals(1, msgFactHandles.size());
        for (FactHandle handle : msgFactHandles) {
            WarningMessage msg = (WarningMessage) ((DefaultFactHandle) handle).getObject();
            logger.info("Warning Message found: {}", msg.getMessage());
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
        Logger testLog = LoggerFactory.getLogger(ClaimProcessTest.class.getName() + ".testProcess.droolsSession");

        // KieSession
        KieSession jbpmSession = runtimeEngine.getKieSession();
        TaskService taskService = runtimeEngine.getTaskService();

        // Add logger to Drools Session
        droolsSession.setGlobal("logger", testLog);
        // Add Event Listener for inserting the process instance into the Drools
        // Session
        jbpmSession.addEventListener(new InsertProcessEventListener(droolsSession));

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
        Collection<FactHandle> msgFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(WarningMessage.class));
        // Assert NO warning messages have been added
        Assert.assertEquals(0, msgFactHandles.size());
    }

    /**
     * Test that if there are more than 100 process instances that are not
     * created within an hour, a warning was NOT triggered by the rules
     * 
     */
    @Test
    public void test100ProcessInMoreThanOneHour_AllInProgress() {
        Logger testLog = LoggerFactory.getLogger(ClaimProcessTest.class.getName() + ".testProcess.droolsSession");

        KieSession jbpmSession = runtimeEngine.getKieSession();

        // Add logger to Drools Session
        droolsSession.setGlobal("logger", testLog);
        // Add Event Listener for inserting the process instance into the Drools
        // Session
        jbpmSession.addEventListener(new InsertProcessEventListener(droolsSession));

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

        // Validate that there are no WarningMessages in the Working Memory
        Collection<FactHandle> msgFactHandles = droolsSession.getFactHandles(new ClassObjectFilter(WarningMessage.class));
        // Assert 0 warning messages have been added
        Assert.assertEquals(0, msgFactHandles.size());
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
        Assert.assertNotNull(droolsSession.getKieBase().getKiePackage("com.plugtree.training.droolsjbpm_event_driven.drl"));
        Assert.assertNull(droolsSession.getKieBase().getKiePackage("com.plugtree.training.droolsjbpm_event_driven.bpmn"));

        // One Process in the jBPM Session
        Assert.assertEquals(1, jbpmSession.getKieBase().getProcesses().size());

        // No Rules in the jBPM Session
        Assert.assertNotNull(jbpmSession.getKieBase().getKiePackage("com.plugtree.training.droolsjbpm_event_driven.bpmn"));
        Assert.assertNull(jbpmSession.getKieBase().getKiePackage("com.plugtree.training.droolsjbpm_event_driven.drl"));

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
    private Task receiveClaim(TaskService taskService, long processId, String supportOperator, String customerName, String customerLocation) {
        // Prepare task's output data: Insert customerName, location, operator
        // name, start date (today), endDate (null)
        Map<String, Object> taskOutParams = new HashMap<String, Object>();
        taskOutParams.put("customerName", customerName);
        taskOutParams.put("location", customerLocation);
        taskOutParams.put(VAR_OPERATOR, supportOperator);
        taskOutParams.put("startDate", new Date());
        taskOutParams.put("endDate", null);
        taskOutParams.put(VAR_STATUS, CLAIM_STATUS_OPEN);

        return workOnTask(taskService, processId, supportOperator, taskOutParams);
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
        taskOutParams.put("endDate", new Date());
        taskOutParams.put(VAR_STATUS, status);

        return workOnTask(taskService, processInstanceId, techOperator, taskOutParams);
    }

    private Task workOnTask(TaskService taskService, long processInstanceId, String user, Map<String, Object> taskOutParams) {
        // User will work the task
        List<TaskSummary> tasksSum = taskService.getTasksByStatusByProcessInstanceId(processInstanceId, Arrays.asList(Status.Ready), EN_UK);
        Assert.assertEquals(user + " does not have one available task for him", 1, tasksSum.size());
        // Get Task from TaskSummary
        Task task = taskService.getTaskById(tasksSum.get(0).getId());
        // The task should be Ready
        Assert.assertEquals(task.getName() + " task is not Ready", Status.Ready, task.getTaskData().getStatus());
        // Reserve/Claim the task
        taskService.claim(task.getId(), user);
        // Refresh task data
        task = taskService.getTaskById(task.getId());
        // The task should be Reserved
        Assert.assertEquals(task.getName() + " task is not Reserved", Status.Reserved, task.getTaskData().getStatus());
        // Start the task
        taskService.start(task.getId(), user);
        // Refresh task data
        task = taskService.getTaskById(task.getId());
        // The task should be In Progress
        Assert.assertEquals("After starting " + task.getName() + " Task, it's status is not In Progress", Status.InProgress,
                task.getTaskData().getStatus());

        // Complete the Task with the out data
        taskService.complete(task.getId(), user, taskOutParams);
        // Refresh task data
        task = taskService.getTaskById(task.getId());
        // Check if task is completed
        Assert.assertEquals("The Human Task " + task.getName() + " is not in Completed state", Status.Completed, task
                .getTaskData().getStatus());

        return task;
    }

    /**
     * Disposes the session sent as parameter
     * 
     * @param sessions
     */
    private static void dispose(KieSession session) {
        if (session != null) {
            session.dispose();
        }
    }

    /**
     * Disposes the RuntimeEngine from the RuntimeManager. Closes the
     * RuntimeManager.
     * 
     * @param manager
     *            the RuntimeManager
     * @param engine
     *            the RuntimeEngine
     */
    private static void disposeAndClose(RuntimeManager manager, RuntimeEngine engine) {
        if (manager != null) {
            manager.disposeRuntimeEngine(engine);
            manager.close();
        }
    }

    /**
     * Creates a new KieSession (Stateful) that will be used for the rules. Its
     * KieBase only contains the claimRules.drl file. KieBase is configured to
     * STREAM Event Processing and PSEUDO clock.
     * 
     * @return the new KieSession
     */
    private KieSession createDroolsSession() {
        logger.info("Creating new Drools Session");
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(ResourceFactory.newClassPathResource("com/plugtree/training/droolsjbpm_event_driven/drl/claimRules.drl"));
        KieBuilder kbuilder = ks.newKieBuilder(kfs);
        kbuilder.buildAll();
        if (kbuilder.getResults().hasMessages(Level.ERROR)) {
            throw new IllegalArgumentException(kbuilder.getResults().toString());
        }
        ReleaseId relId = kbuilder.getKieModule().getReleaseId();
        KieContainer kcontainer = ks.newKieContainer(relId);
        KieSessionConfiguration ksconf = ks.newKieSessionConfiguration();
        KieBaseConfiguration kbconf = ks.newKieBaseConfiguration();
        kbconf.setOption(EventProcessingOption.STREAM);
        ksconf.setOption(ClockTypeOption.get(ClockType.PSEUDO_CLOCK.getId()));
        KieBase kbase = kcontainer.newKieBase(kbconf);

        return kbase.newKieSession(ksconf, null);
    }

    /**
     * Creates a new RuntimeManager for the Process Session. Its KieBase only
     * contains the claimProcess.bpmn file.
     * 
     * @return the new KieSession
     */
    private RuntimeManager createProcessRuntimeManager() {
        logger.info("Creating new Process Session");
        Properties userGroups = new Properties();
        userGroups.put(USR_SUPPORT_JOHN, GROUP_SUPPORT);
        userGroups.put(USR_TECH_PAUL, GROUP_TECH);
        // Environment creation
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.Factory
                .get()
                .newDefaultInMemoryBuilder()
                .addAsset(
                        ResourceFactory
                                .newClassPathResource("com/plugtree/training/droolsjbpm_event_driven/bpmn/claimProcess.bpmn"),
                        ResourceType.BPMN2)
                .entityManagerFactory(Persistence.createEntityManagerFactory("org.jbpm.services.task"))
                .userGroupCallback(new JBossUserGroupCallbackImpl(userGroups)).get();
        return RuntimeManagerFactory.Factory.get().newPerRequestRuntimeManager(environment);
    }
}
