package org.uu.nl.goldenagents.agent.plan.broker;

import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.context.DirectSsePublisher;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.agent.trigger.goal.broker.DiscoverBrokerExpertiseGoal;
import org.uu.nl.goldenagents.agent.trigger.goal.broker.LoadConceptsGoal;
import org.uu.nl.goldenagents.decompose.expertise.DbAgentExpertise;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.platform.Platform;

import java.util.logging.Level;

public class AddDbExpertisePlan extends MessagePlan {

	public AddDbExpertisePlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
		super(message, header, content);
	}

	@Override
	public void executeOnce(PlanToAgentInterface planInterface, ACLMessage receivedMessage, 
			GAMessageHeader header, FIPASendableObject content) throws PlanExecutionError {
		
		DbAgentExpertise expertise = (DbAgentExpertise) content;

		BrokerContext context = planInterface.getContext(BrokerContext.class);
		context.addDbAgentExpertise(receivedMessage.getSender(), expertise);

		// Broker expertise changed. Concepts that can be suggested have to be updated
		planInterface.getAgent().adoptGoal(new LoadConceptsGoal());

		//The plan to add the new source agent to the expertise of broker agent
		planInterface.getAgent().adoptGoal(new DiscoverBrokerExpertiseGoal());
		
		if(context.fullFunctionalityReady()) {
			DirectSsePublisher publisher = planInterface.getAgent().getContext(DirectSsePublisher.class);
			if(publisher != null) {
				if(!context.hasNotifiedFullState()) {
					publisher.publishStateReady();
					context.setHasNotifiedFullState(true);
				}
			} else {
				Platform.getLogger().log(AddDbExpertisePlan.class, Level.SEVERE,
						"No DirectSsePublisher found on broker. Can't send READY status update");
			}
		}
	}
}
