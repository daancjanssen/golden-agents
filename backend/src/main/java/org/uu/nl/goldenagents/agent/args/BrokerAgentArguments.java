package org.uu.nl.goldenagents.agent.args;

import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.context.registration.MinimalFunctionalityContext;
import org.uu.nl.goldenagents.agent.planscheme.broker.BrokerGoalPlanScheme;
import org.uu.nl.goldenagents.agent.planscheme.broker.BrokerMessagePlanScheme;
import org.uu.nl.goldenagents.util.AgentUtils;

public class BrokerAgentArguments extends DFRegistration {
	
	public BrokerAgentArguments(BrokerContext context) {
		super(AgentUtils.BROKER_SERVICE_TYPE, AgentUtils.SPARQL_SERVICE_NAME, new BrokerMessagePlanScheme());
		super.addContext(context, BrokerContext.class, MinimalFunctionalityContext.class);
		super.addGoalPlanScheme(new BrokerGoalPlanScheme());
	}

}