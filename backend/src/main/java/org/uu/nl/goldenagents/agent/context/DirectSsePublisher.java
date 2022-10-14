package org.uu.nl.goldenagents.agent.context;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import org.uu.nl.goldenagents.netmodels.angular.AQLSuggestions;
import org.uu.nl.goldenagents.netmodels.angular.CrudMessage;
import org.uu.nl.goldenagents.netmodels.angular.QueryProgress;
import org.uu.nl.goldenagents.util.PublishedEvent;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.agent.Context;
import org.uu.nl.net2apl.core.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

public class DirectSsePublisher implements Context {

    private static final Semaphore semaphore = new Semaphore(1);
    private final SseEventBus eventBus;
    private AgentID agentID;

    private static final ConcurrentLinkedQueue<SSeListener> listeners = new ConcurrentLinkedQueue<>();

    public DirectSsePublisher(SseEventBus eventBus, AgentID agentID) {
        this.eventBus = eventBus;
        this.agentID = agentID;
    }

    public synchronized boolean publishQueryProgress(QueryProgress<?> progress) {
        PublishedEvent<QueryProgress<?>> event = new PublishedEvent<>("query_progress_" + progress.getQueryID(), progress);
        log("Query Progress", event);
        for(SSeListener listener : listeners) {
            listener.onQueryProgress(progress);
        }
        return this.publishEvent(event);
    }

    public synchronized boolean publishCrudMessage(String involvedAgentUUID, CrudMessage message) {
        PublishedEvent<CrudMessage> event = new PublishedEvent<>("messages_" + involvedAgentUUID, message);
        log("Message", event);
        for(SSeListener listener : listeners) {
            listener.onCrudMessage(involvedAgentUUID, message);
        }
        return this.publishEvent(event);
    }

    public synchronized boolean publishStateReady() {
        PublishedEvent<String> event = new PublishedEvent<>("agent_state_ready", this.agentID.getUuID());
        log("State Ready", event);
        for(SSeListener listener : listeners) {
            listener.onStateReady(this.agentID);
        }
        return this.publishEvent(event);
    }

    public synchronized boolean publishSuggestionsReady(AgentID agentID, AQLSuggestions suggestions) {
        PublishedEvent<AQLSuggestions> event = new PublishedEvent<>("suggestions_" + agentID.getUuID(), suggestions);
        log("Suggestions ready", event);
        for(SSeListener listener : listeners) {
            listener.onSuggestionsReady(agentID, suggestions);
        }
        return this.publishEvent(event);
    }

    private synchronized boolean publishEvent(PublishedEvent<?> event) {
        boolean success = false;

        try {
            semaphore.acquire();
            eventBus.handleEvent(SseEvent.of(event.getEventId(), event.getPayload()));
            success = true;
        } catch(InterruptedException e) {
            Platform.getLogger().log(DirectSsePublisher.class, e);
        } finally {
            semaphore.release();
        }

        return success;
    }

    private void log(String type, PublishedEvent<?> event) {
        Platform.getLogger().log(DirectSsePublisher.class, Level.INFO, String.format(
                "Event sent\t|\tAgent %s\t|\tEvent type %s\t|\tEventID %s\t|\tPayload: %s",
                this.agentID,
                type,
                event.getEventId(),
                event.toString()
        ));
    }

    public static void queryFailed(String queryID, Throwable cause) {
        for(SSeListener listener : listeners) {
            listener.onQueryFailed(queryID, cause);
        }
    }

    public static void addListener(SSeListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(SSeListener listener) {
        listeners.remove(listener);
    }

    public interface SSeListener {
        void onQueryProgress(QueryProgress<?> progress);
        void onCrudMessage(String involvedAgentUUID, CrudMessage message);
        void onStateReady(AgentID agentID);
        void onSuggestionsReady(AgentID agentID, AQLSuggestions suggestions);
        void onQueryFailed(String queryID, Throwable cause);
    }
}
