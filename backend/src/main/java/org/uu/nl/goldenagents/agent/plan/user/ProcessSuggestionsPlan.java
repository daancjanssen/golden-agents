package org.uu.nl.goldenagents.agent.plan.user;

import org.uu.nl.goldenagents.agent.context.DirectSsePublisher;
import org.uu.nl.goldenagents.agent.context.query.AQLQueryContext;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.netmodels.angular.AQLSuggestions;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentString;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.UnreadableException;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;

public class ProcessSuggestionsPlan extends MessagePlan {

    public ProcessSuggestionsPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
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
        try {
            GAMessageContentWrapper wrapper = (GAMessageContentWrapper) receivedMessage.getContentObject();
            AQLSuggestions suggestions = (AQLSuggestions) wrapper.getContent();
            AQLQueryContext c = planInterface.getContext(AQLQueryContext.class);
            c.setSuggestionsForNodeID(suggestions);

            DirectSsePublisher publisher = planInterface.getContext(DirectSsePublisher.class);
            publisher.publishSuggestionsReady(planInterface.getAgentID(), suggestions);
        } catch (UnreadableException e) {
            e.printStackTrace();
            return;
        }
    }
}
