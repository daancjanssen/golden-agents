package org.uu.nl.goldenagents.agent.plan.broker.mergeresult;

import org.apache.jena.query.Query;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.lang.ParserARQ;
import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.context.DirectSsePublisher;
import org.uu.nl.goldenagents.agent.context.query.QueryProgressType;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.netmodels.angular.QueryProgress;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.netmodels.fipa.QueryResult;
import org.uu.nl.goldenagents.sparql.CachedModel;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.defaults.messenger.MessageReceiverNotFoundException;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.Performative;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.platform.Platform;
import org.uu.nl.net2apl.core.platform.PlatformNotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;

public abstract class MergeResultPlan extends MessagePlan {

	protected AgentID userAgent;
	protected AgentID datasourceAgent;
	protected BrokerContext context;
	protected String conversationID;
	protected String queryID;
	protected DirectSsePublisher publisher;
	protected CachedModel model;
	private PlanToAgentInterface planInterface;

	public MergeResultPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
		super(message, header, content);
	}

	@Override
	public void executeOnce(PlanToAgentInterface planInterface, ACLMessage receivedMessage, GAMessageHeader header, FIPASendableObject content) throws PlanExecutionError {

		// Reinitialize values on every execution step to make sure these are up-to-date
		this.planInterface = planInterface;
		this.userAgent = planInterface.getContext(BrokerContext.class)
				.getConversationUser(receivedMessage.getConversationId());
		this.datasourceAgent = receivedMessage.getSender();
		this.context = planInterface.getContext(BrokerContext.class);
		this.publisher = planInterface.getContext(DirectSsePublisher.class);

		if (userAgent != null) {
			this.conversationID = receivedMessage.getConversationId();
			this.queryID = context.getQueryID(this.conversationID);
			this.model = context.getCachedModel(this.conversationID);

			final int expectedModelNr = this.model.getExpectedSize();
			
			handleMessage(planInterface);
			
			// If we have as many cached models as are expected for this conversation
			if(this.model.isFinished()) {
				finalizeModel();
				handleSuggestions();
			}
		} else {
			logger.log(MergeResultPlan.class, Level.WARNING,
					"in Broker.INFORM_REF: Recieved response-message from DB with no conversation-id");
			// TODO better error handling
		}
	}

	private void finalizeModel() {
		this.publisher.publishQueryProgress(
				new QueryProgress<>(queryID, QueryProgressType.DATA_COLLECTED, this.model.getTotalSize(), true));

		//TODO this part can be extended according
		if(this.model.isComplete()) {
			logger.log(MergeResultPlan.class, "Received all expected models");
		} else {
			logger.log(MergeResultPlan.class,
					String.format(
							"Received %d models and %d error replies",
							this.model.getCurrentSize(),
							this.model.getUnsuccessfullReplySize()
					));
		}

		try {
			logger.log(MergeResultPlan.class, "Querying the final merged model");
			this.publisher.publishQueryProgress(
					new QueryProgress<Long>(this.queryID, QueryProgressType.QUERY_EXECUTED, true));

			QueryResult result = this.model.querySelect();

			logger.log(MergeResultPlan.class, "Passing message to user " + this.userAgent.toString());

			// Inform user agent that all results have been collected
			ACLMessage forward = this.message.createForward(this.planInterface.getAgentID(), this.userAgent);
			forward.setPerformative(Performative.INFORM_REF);
			forward.setContentObject(new GAMessageContentWrapper(GAMessageHeader.BROKER_RESULTSET, result));
			this.planInterface.getAgent().sendMessage(forward);

			this.publisher.publishQueryProgress(
					new QueryProgress<Long>(queryID, QueryProgressType.RESULTS_RETURNED, true));

		} catch (IOException | MessageReceiverNotFoundException | PlatformNotFoundException ex) {
			logger.log(MergeResultPlan.class, ex);
		} finally {
			// We don't want all cached models to hang around in memory after the work is done
			// TODO instead of removing cached model, see if existing model can be reused in incremental search
			this.context.removeCachedModel(this.queryID);
		}
	}

	/**
	 * In some cases, query results should be followed up with finding search suggestions for the UI. This method
	 * handles that bit.
	 */
	private void handleSuggestions() {
		if(this.model.isSuggestionsExpected() && this.model.getUserQueryTrigger().getSparqlTranslation() != null) {
			logger.log(getClass(), "Starting to aggregate properties suggestions for search");

			Var focus = this.model.getUserQueryTrigger().getSparqlTranslation().getFocusVar();
			Query q = new Query();

			// Parse
			ParserARQ.createParser(Syntax.syntaxSPARQL_11).parse(q, this.model.getOriginalQuery());
			q.setDistinct(true);
			q.addProjectVars(Collections.singleton(focus));

			// TODO this doesn't allow including new data sources
			HashMap<AgentID, CachedModel.EntityList<String>> relevantEntities = this.model.getEntitiesAtFocus(q);
			Platform.getLogger().log(getClass(), Level.SEVERE, "Contacting agents: " + relevantEntities.keySet().toString());
			for(AgentID aid : relevantEntities.keySet()) {
				if(relevantEntities.get(aid).getEntities().size() > 0) {
					this.model.expectSuggestionsFrom(aid);
					requestSuggestionsToAgent(aid, relevantEntities.get(aid));
				}
			}

			// TODO, are entities resolved after linkset reasoning included here?
//			CachedModel.EntityList<String> entities = model.getSerializableFocusEntities();
//			for(AgentID aid : this.context.getDbAgentCapabilities().keySet()) {
//				this.model.expectSuggestionsFrom(aid);
//				requestSuggestionsToAgent(aid, entities);
//			}
		}
	}

	private void requestSuggestionsToAgent(AgentID dbAgent, CachedModel.EntityList entities) {
		ACLMessage m = this.message.createForward(this.planInterface.getAgentID(), dbAgent);
		m.setPerformative(Performative.REQUEST);
		try {
			m.setContentObject(new GAMessageContentWrapper(GAMessageHeader.REQUEST_SUGGESTIONS, entities));
		} catch (IOException e) {
			logger.log(getClass(), e);
		}

		try {
			planInterface.getAgent().sendMessage(m);
			logger.log(getClass(), Level.INFO, String.format(
					"Requested suggestions for %d entities from db agent %s",
					entities.getEntities().size(), dbAgent.getShortLocalName()));
		} catch (PlatformNotFoundException | MessageReceiverNotFoundException e) {
			logger.log(getClass(), e);
		}
	}

	/**
	 * Construct a query progress object
	 * @param datasize		The size of the total data model aggregated so far
	 * @param addedItems	The number of items added by this sub-model, or -1 if the DB agent is done
	 * @return				A Query Progress object encoding a data collected event
	 */
	protected QueryProgress createQueryProgress(long datasize, long addedItems ) {
		QueryProgress<Long> progress = new QueryProgress<>(
				this.queryID, QueryProgressType.DATA_COLLECTED, datasize, false);

		QueryProgress.QueryProgressSubResult subResult = new QueryProgress.QueryProgressSubResult(
				this.datasourceAgent.getName().getFragment(),
				addedItems >= 0 ? addedItems : 0,
				addedItems < 0
		);

		progress.addSubresult(subResult);
		return progress;
	}

    /**
     * Log current status of the cached model
     * @param actionSpecificMessage     A message explaining result of performed action. May be null
     */
	protected void logUpdate(String actionSpecificMessage) {
	    if(actionSpecificMessage != null)
	        logger.log(getClass(), actionSpecificMessage);

        logger.log(getClass(), "Nr of cached models: " + this.model.getCurrentSize());
        logger.log(getClass(), String.format("Expecting %s more models",
                this.model.getExpectedSize() - this.model.getCurrentSize()));
    }

    /**
     * Message type specific operations. Between general pre- and post processing of message, this is where the actual
     * plan for the specific type of reply from a data source agent to a query request is handled
     * @param planInterface     PlanToAgentInterface for e.g. sending messages
     */
	protected abstract void handleMessage(PlanToAgentInterface planInterface);
	
}