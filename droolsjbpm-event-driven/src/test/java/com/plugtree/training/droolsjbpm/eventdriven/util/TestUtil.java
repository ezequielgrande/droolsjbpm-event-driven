package com.plugtree.training.droolsjbpm.eventdriven.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.Persistence;

import junit.framework.Assert;

import org.drools.core.ClockType;
import org.jbpm.services.task.identity.JBossUserGroupCallbackImpl;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message.Level;
import org.kie.api.builder.ReleaseId;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeEnvironment;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.io.ResourceFactory;

import com.plugtree.training.droolsjbpm.model.Server;

/**
 * Helper methods for testing purposes
 * 
 * @author ezegrande
 *
 */
public class TestUtil {
    private static final String EN_UK = "en-UK";

    private TestUtil() {
        // Non-instantiable class
    }

    /**
     * Inserts all the object into the session and returns a Map with a
     * relationship between each object and their corresponding FactHandle
     * 
     * @param session
     * @param objects
     * @return a Map with a relationship between each object and their
     *         corresponding FactHandle
     */
    public static Map<Object, FactHandle> insertAll(KieSession session, Object... objects) {
        Map<Object, FactHandle> factHandles = new HashMap<Object, FactHandle>(objects.length);
        for (Object o : objects) {
            FactHandle factHandle = session.insert(o);
            factHandles.put(o, factHandle);
        }
        return factHandles;
    }

    /**
     * Starts up all the servers sent as parameter
     * 
     * @param servers
     */
    public static void startUpServers(Server... servers) {
        for (Server s : servers) {
            s.startUp();
        }
    }

    /**
     * Creates a new KieSession (Stateful) that will be used for the rules. Its
     * KieBase only contains the claimRules.drl file. KieBase is configured to
     * STREAM Event Processing and PSEUDO clock.
     * 
     * @return the new KieSession
     */
    public static KieSession createDroolsSession() {
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
     * Disposes the session sent as parameter
     * 
     * @param sessions
     */
    public static void dispose(KieSession session) {
        if (session != null) {
            session.dispose();
        }
    }

    /**
     * Creates a new RuntimeManager for the Process Session. Its KieBase only
     * contains the claimProcess.bpmn file.
     * 
     * @return the new KieSession
     */
    public static RuntimeManager createProcessRuntimeManager(Properties userGroups) {
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

    /**
     * Disposes the RuntimeEngine from the RuntimeManager. Closes the
     * RuntimeManager.
     * 
     * @param manager
     *            the RuntimeManager
     * @param engine
     *            the RuntimeEngine
     */
    public static void disposeAndClose(RuntimeManager manager, RuntimeEngine engine) {
        if (manager != null) {
            manager.disposeRuntimeEngine(engine);
            manager.close();
        }
    }

    /**
     * Works on the Ready tasks of the Process Instance (claim, start, complete)
     * 
     * @param taskService
     *            the TaskService
     * @param processInstanceId
     *            the Process Instance ID
     * @param user
     *            the User who will work on the task
     * @param taskOutParams
     *            Task results
     * 
     * @return the Completed Task
     */
    public static Task workOnTask(TaskService taskService, long processInstanceId, String user,
            Map<String, Object> taskOutParams) {
        // User will work the task
        List<TaskSummary> tasksSum = taskService.getTasksByStatusByProcessInstanceId(processInstanceId,
                Arrays.asList(Status.Ready), EN_UK);
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

}
