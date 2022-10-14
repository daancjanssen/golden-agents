package org.uu.nl.goldenagents.agent.plan.user;

import org.apache.jena.sparql.core.Var;
import org.uu.nl.goldenagents.agent.context.registration.DFRegistrationContext;
import org.uu.nl.goldenagents.agent.trigger.user.AQLQueryChangedExternalTrigger;
import org.uu.nl.goldenagents.aql.MostGeneralQuery;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.defaults.messenger.MessageReceiverNotFoundException;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.Performative;
import org.uu.nl.net2apl.core.fipa.mts.Envelope;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.plan.builtin.RunOncePlan;
import org.uu.nl.net2apl.core.platform.Platform;
import org.uu.nl.net2apl.core.platform.PlatformNotFoundException;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;

/**
 * This plan is executed when the user changes a query using the AQL query builder in the frontend.
 * It requests suggestions from the BROKER agent for the given query.
 */
public class RequestSuggestionsPlan extends RunOncePlan {

    AQLQueryChangedExternalTrigger trigger;

    private Set<AgentID> availableBrokers;

    public RequestSuggestionsPlan(AQLQueryChangedExternalTrigger trigger){
        this.trigger = trigger;
    }

    @Override
    public void executeOnce(PlanToAgentInterface planInterface) throws PlanExecutionError {
        // Find available brokers
        DFRegistrationContext context = planInterface.getContext(DFRegistrationContext.class);
        this.availableBrokers = context.getSubscriptions();

        ACLMessage msg = new ACLMessage(Performative.REQUEST);
        Envelope envelope = new Envelope();
        envelope.setFrom(planInterface.getAgentID());
        availableBrokers.forEach(envelope::addTo);
        msg.setEnvelope(envelope);
        msg.setReceivers(availableBrokers);

        GAMessageContentWrapper messageContent;
        // TODO just for testing interface
        if(this.trigger.getQueryTree() instanceof MostGeneralQuery) {
            messageContent = createSimpleSuggestionRequest();
        } else {
            messageContent = requestQueryBasedSuggestions();
        }

        try {
            msg.setContentObject(messageContent);
        } catch (IOException e) {
            Platform.getLogger().log(getClass(), Level.SEVERE, "Error serializing object of type " + messageContent.getContent().getClass());
            Platform.getLogger().log(getClass(), e);
        }

        try {
            planInterface.getAgent().sendMessage(msg);
        } catch (MessageReceiverNotFoundException | PlatformNotFoundException e) {
            Platform.getLogger().log(getClass(), e);
        }
    }

    private GAMessageContentWrapper createSimpleSuggestionRequest() {
        return new GAMessageContentWrapper(GAMessageHeader.REQUEST_SUGGESTIONS, this.trigger.getQueryTree());
    }

    private GAMessageContentWrapper requestQueryBasedSuggestions() {
        UserQueryTrigger t = new UserQueryTrigger(this.trigger.getQuery(), GAMessageHeader.REQUEST_DATA_BASED_SUGGESTIONS);
        return new GAMessageContentWrapper(GAMessageHeader.REQUEST_DATA_BASED_SUGGESTIONS, t);
    }
}
