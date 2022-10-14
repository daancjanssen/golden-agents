package org.uu.nl.goldenagents.agent.plan.broker.suggestions;

import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.netmodels.AqlDbTypeSuggestionWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.sparql.CachedModel;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.UnreadableException;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;

import java.util.logging.Level;

public class ReceiveDBSuggestionsPlan extends MessagePlan {

    public ReceiveDBSuggestionsPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
        super(message, header, content);
    }

    /**
     * This method is executed when a message has been received
     *
     * @param planInterface   An interface to the agent in order to access context, etc
     * @param receivedMessage The message that triggered this plan
     * @param header          The header of the message
     * @param content         The content of the message
     * @throws PlanExecutionError Exception thrown when executing this plan goes totally awry
     */
    @Override
    public void executeOnce(PlanToAgentInterface planInterface, ACLMessage receivedMessage, GAMessageHeader header, FIPASendableObject content) throws PlanExecutionError {
        BrokerContext context = planInterface.getContext(BrokerContext.class);
        AqlDbTypeSuggestionWrapper suggestions;
        try {
            suggestions = (AqlDbTypeSuggestionWrapper) ((GAMessageContentWrapper) message.getContentObject()).getContent();
        } catch (UnreadableException e) {
            logger.log(getClass(), "Unable to parse AQL DB Type Suggestion Wrapper object. Plan failed");
            logger.log(getClass(), e);
            throw new PlanExecutionError();
        }

        // TODO is this even correct for storing the data? Is AQL ID same as QueryID? Should maybe use query trigger for this
        context.addDbSuggestionsForQuery(this.message.getConversationId(), message.getSender(), suggestions);
        CachedModel model = context.getCachedModel(receivedMessage.getConversationId());
        model.setAgentSuggestionsReceived(message.getSender(), true);

        if(model.querySuggestionsDone()) {
            logger.log(getClass(), String.format(
                    "Finished receiving %d suggestions, starting plan to aggregate and inform user",
                    model.getExpectedSuggestionAgents()));

            // TODO need AQLTree message somehow
            AQLTree t = context.getCachedModel(receivedMessage.getConversationId()).getUserQueryTrigger().getAql().getQueryTree();
            this.message.addReplyTo(context.getConversationUser(receivedMessage.getConversationId()));
            planInterface.adoptPlan(new DbBasedSuggestSearchOptionsPlan(receivedMessage, header, t));
        } else {
            logger.log(getClass(), Level.SEVERE, String.format("Received suggestions from %d data source agents, expecting %d in total",
                    context.getDbSuggestionsForQuery(this.message.getConversationId()).size(),
                    model.getExpectedSuggestionAgents()));
        }
    }
}
