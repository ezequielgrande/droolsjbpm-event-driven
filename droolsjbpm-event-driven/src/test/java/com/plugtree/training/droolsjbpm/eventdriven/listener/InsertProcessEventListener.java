package com.plugtree.training.droolsjbpm.eventdriven.listener;

import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.plugtree.training.droolsjbpm.model.SupportTool;

/**
 * Before a Process is started, the ProcessStartedEvent is inserted into the
 * Drools KieSession and all rules are fired.
 * 
 * @author ezegrande
 *
 */
public class InsertProcessEventListener implements ProcessEventListener {
    private static Logger logger = LoggerFactory.getLogger(InsertProcessEventListener.class);
    private KieSession eventSession;
    private String entryPoint;

    public InsertProcessEventListener(KieSession droolsSession, SupportTool tool) {
        this.eventSession = droolsSession;
        this.entryPoint = tool.getEntryPointName();
    }

    public void beforeProcessStarted(ProcessStartedEvent event) {
        logger.info("Process {} is getting started. Id: {} ", event.getProcessInstance().getProcessId(), event
                .getProcessInstance().getId());
        EntryPoint ep = eventSession.getEntryPoint(entryPoint);
        // Insert ProcessStartedEvent into Events Session
        ep.insert(event);
        // Fire rules that might be activated/deactivated with previous
        // insertion
        eventSession.fireAllRules();
    }

    public void afterProcessStarted(ProcessStartedEvent event) {
    }

    public void beforeProcessCompleted(ProcessCompletedEvent event) {
    }

    public void afterProcessCompleted(ProcessCompletedEvent event) {
        logger.info("Process {} is completed. Id: {} ", event.getProcessInstance().getProcessId(), event.getProcessInstance()
                .getId());
        EntryPoint ep = eventSession.getEntryPoint(entryPoint);
        // Insert ProcessCompletedEvent into Events Session
        ep.insert(event);
        // Fire rules that might be activated/deactivated with previous
        // insertion
        eventSession.fireAllRules();
    }

    public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
    }

    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
    }

    public void beforeNodeLeft(ProcessNodeLeftEvent event) {
    }

    public void afterNodeLeft(ProcessNodeLeftEvent event) {
    }

    public void beforeVariableChanged(ProcessVariableChangedEvent event) {
    }

    public void afterVariableChanged(ProcessVariableChangedEvent event) {
    }

}
