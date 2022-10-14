package org.uu.nl.goldenagents.agent.plan.broker.splitquery;

import org.apache.jena.query.QueryException;
import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.context.BrokerPrefixNamespaceContext;
import org.uu.nl.goldenagents.agent.context.DirectSsePublisher;
import org.uu.nl.goldenagents.agent.context.query.QueryProgressType;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.exceptions.BadQueryException;
import org.uu.nl.goldenagents.netmodels.angular.QueryExceptionInfo;
import org.uu.nl.goldenagents.netmodels.angular.QueryProgress;
import org.uu.nl.goldenagents.netmodels.fipa.AgentQuery;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.goldenagents.sparql.CachedModel;
import org.uu.nl.goldenagents.sparql.QueryInfo;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.defaults.messenger.MessageReceiverNotFoundException;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.Performative;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.platform.Platform;
import org.uu.nl.net2apl.core.platform.PlatformNotFoundException;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public abstract class SplitQueryPlan extends MessagePlan {

	/**
	 * The trigger sent by the user to initiate the query process
	 */
	protected UserQueryTrigger queryRequest;

	/**
	 * The SPARQL-complient query string
	 */
	protected String queryString;

	/**
	 * Query info object containing Golden Agents parsed result of a query string
	 */
	protected QueryInfo queryInfo;

	/**
	 * A list of names of sources that should be consulted if possible to answer the {@code queryString} query
	 */
	protected String[] selectedSources;

	/**
	 * The QueryID, which is unique for this query, and used to indicate the topic of messages, errors and progress
	 * updates
	 */
	private String queryID;

	/**
	 * The identitier of this conversion, used to connect this query progress to a specific user request
	 */
	private String conversationID;

	/**
	 * The plan interface is static for every execution cycle. Making it a class member allows not having to pass it
	 * as an argument to other class methods
	 */
	private PlanToAgentInterface planInterface;

	/**
	 * A reference to the BrokerContext object
	 */
	protected BrokerContext context;

	/**
	 * A direct SSE Publisher to inform subscribers of progress in the query process
	 */
	private DirectSsePublisher publisher;

	// Message fields, so we don't have to unpack constantly
	private AgentID userAgent;

	/**
	 * A boolean that tells the broker to collect suggestions based on the results
	 * it retrieves for this query.
	 */
	protected boolean suggestionsRequested = false;

	public SplitQueryPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
		super(message, header, content);
	}

	/**
	 * Main execution endpoint for this plan
	 * @param planInterface An interface to the agent in order to access context, etc
	 * @param receivedMessage The message that triggered this plan
	 * @param header The header of the message
	 * @param content The content of the message
	 * @throws PlanExecutionError If the message does not contain any query at all
	 */
	@Override
	public void executeOnce(
			PlanToAgentInterface planInterface,
			ACLMessage receivedMessage,
			GAMessageHeader header,
			FIPASendableObject content
	)
			throws PlanExecutionError
	{
		// Update plan interface and broker context on every execution cycle
		this.planInterface = planInterface;
		this.context = planInterface.getContext(BrokerContext.class);
		BrokerPrefixNamespaceContext prefixContext = planInterface.getContext(BrokerPrefixNamespaceContext.class);

		// Perform initial checks
		inspectMessage();
		parseUserTrigger();
		publishQueryProcessStart();

		context.addQueryID(this.conversationID, this.queryID);

		// Perform advanced syntax checks and parse query
		try {
			this.queryInfo = new QueryInfo(this.queryString, prefixContext.getOntologyPrefixes());
		} catch(QueryException | BadQueryException e) {
			informQueryError(e);
			return;
		} catch (Exception e) {
			// Added this to catch any type of error and thus, to stop crashing broker agent
			e.printStackTrace();
			return;
		}

		processQuery();
	}

	/**
	 * Tell the broker agent that suggestions are expected to be generated based
	 * on the results retrieved for this query
	 * @param suggestionsRequested true if suggestions are expected
	 */
	public void setSuggestionsRequested(boolean suggestionsRequested) {
		this.suggestionsRequested = suggestionsRequested;
	}

	/**
	 * Inspect message and extract relevant information
	 * @throws PlanExecutionError 	If no valid UserQueryTrigger is attached to the received message
	 */
	private void inspectMessage() throws PlanExecutionError {
		Platform.getLogger().log(getClass(), Level.FINE, "Inspecting query request message");
		this.queryRequest = UserQueryTrigger.fromACLMessage(this.message);
		if(queryRequest == null) throw new PlanExecutionError();

		this.userAgent = this.message.getEnvelope().getFrom();
	}

	/**
	 * Parses the UserQueryTrigger and extracts relevant information
	 */
	private void parseUserTrigger() {
		Platform.getLogger().log(getClass(), Level.FINE, "Parsing User Trigger for query request");
		this.queryString = this.queryRequest.getQuery();
		this.selectedSources = this.queryRequest.getSelectedSources();
		this.queryID = this.queryRequest.getQueryID();
		this.conversationID = this.message.getConversationId();
	}

	/**
	 * Notifies relevant subscribers that the query request has been received and accepted
	 */
	private void publishQueryProcessStart() {
		logger.log(SplitQueryPlan.class, "I got a request to query \n" + this.queryString);

		this.publisher = this.planInterface.getContext(DirectSsePublisher.class);
		this.publisher.publishQueryProgress(new QueryProgress<Long>(this.queryID, QueryProgressType.QUERY_SENT, true));
	}

	/**
	 * Once parsing of the query has succeeded, try to match data source agents to parts of the query
	 */
	private void processQuery() {
		logger.log(getClass(), Level.FINE, "Starting query process");
		QueryProgress<Integer> queryProgress =
				new QueryProgress<>(this.queryID, QueryProgressType.SUBQUERY_SENT, 0, false);

		CachedModel model = this.context.createCachedModel(this.conversationID, this.queryRequest, this.queryInfo);
		model.setSuggestionsExpected(this.suggestionsRequested);
        logger.log(getClass(), Level.FINE, "Created cached model");

		List<AgentQuery> agentQueries = null;
		try {
			agentQueries = createAgentQueries();
		} catch (MissingExpertException e) {
			sendErrorMessage(this.planInterface, e.getErrorMessage());
			return;
		} catch (BadQueryException e) {
			informQueryError(e);
			return;
		}

		if(agentQueries.isEmpty()) {
			sendErrorMessage(
					this.planInterface,
					"No database agents are expert on this query, so this query could not be answered");
		} else {
			for(AgentQuery agentQuery : agentQueries) {
				logger.log(getClass(), Level.FINER, "Sending subquery to " + agentQuery.getQueryOwner().getShortLocalName());
				handleSingleAgentQuery(model, queryProgress, agentQuery);
			}
		}
		logger.log(getClass(), Level.INFO, "Done contacting agents");
		model.setDoneContactingAgents(true);
		queryProgress.setFinished(true);
		this.publisher.publishQueryProgress(queryProgress);
	}

	/**
	 * Create a subquery and send it as a query request to a data source agent
	 * @param model			Model in which intermediate results are collected for this broker agent and this query
	 * @param agentQuery	The AgentQuery that specifies how a subquery should be constructed and to which data source
	 *                      agent it should be sent
	 */
	private void handleSingleAgentQuery(CachedModel model, QueryProgress<Integer> progress, AgentQuery agentQuery) {
		logger.log(SplitQueryPlan.class, "Considering agent " + agentQuery.getQueryOwner().getName());

		ACLMessage forward = this.message.createForward(this.planInterface.getAgentID(), agentQuery.getQueryOwner());
		forward.setPerformative(Performative.QUERY_REF);
		this.context.addConversation(this.conversationID, this.userAgent);

		try {
			forward.setContentObject(new GAMessageContentWrapper(GAMessageHeader.BROKER_QUERY, agentQuery));
		} catch (IOException e) {
			logger.log(SplitQueryPlan.class, e);
			return;
		}

		logger.log(SplitQueryPlan.class, "Forwarding CONSTRUCT query to DB Agent "+ agentQuery.getQueryOwner().getName().toString());
		logger.log(SplitQueryPlan.class, agentQuery);

		try {
			planInterface.getAgent().sendMessage(forward);
		} catch (MessageReceiverNotFoundException | PlatformNotFoundException e) {
			logger.log(SplitQueryPlan.class, e);
			return;
		}

		model.addParticipatingAgent(agentQuery.getQueryOwner());

		updateQueryProgress(progress, agentQuery);
	}

	/**
	 * Notify subscribers of a new data source agent that has been contacted for answering the active user query
	 * @param progress 		QueryProgress object for this query
	 * @param agentQuery	AgentQuery object used to contact data source agent for this query
	 */
	private void updateQueryProgress(QueryProgress<Integer> progress, AgentQuery agentQuery) {
		progress.setValue(progress.getValue() + 1);
		QueryProgress.QueryProgressSubResult subResult = new QueryProgress.QueryProgressSubResult(
				agentQuery.getQueryOwner().getName().getFragment(), true);
		progress.addSubresult(subResult);
		this.publisher.publishQueryProgress(progress);
	}
	
	/**
	 * Sends a syntax error message back to the user
	 */
	@Override
	protected void sendErrorMessage(PlanToAgentInterface planInterface, String errorMessage) {
		try {
			ACLMessage reply = this.message.createReply(this.planInterface.getAgentID());
			reply.setPerformative(Performative.NOT_UNDERSTOOD);
			reply.setContentObject(new GAMessageContentWrapper(GAMessageHeader.QUERY_SYNTAX_ERROR, 
					new QueryExceptionInfo(errorMessage, this.queryID)));
			planInterface.sendMessage(this.userAgent, reply);
		} catch(IOException e) {
			logger.log(SplitQueryPlan.class, e);
		}
	}

	/**
	 * When an error occurs in parsing the query, this method is used to inform the user agent of the nature
	 * of the error
	 * @param e 	Exception object thrown because of syntax errors encountered during parsing
	 */
	private void informQueryError(Exception e) {
		DirectSsePublisher.queryFailed(this.queryID, e);
		ACLMessage errorMessage = this.message.createReply(this.planInterface.getAgentID(), Performative.NOT_UNDERSTOOD);
		try {
			errorMessage.setContentObject(
					new GAMessageContentWrapper(
							GAMessageHeader.QUERY_SYNTAX_ERROR,
							new QueryExceptionInfo(e, this.queryID)
					));

			this.planInterface.sendMessage(this.userAgent, errorMessage);
		} catch (IOException ioException) {
			logger.log(SplitQueryPlan.class, Level.INFO, ioException.getMessage());
		}
	}

	/**
	 * Create AgentQuery objects for all agents that should be consulted to answer the current user query. All agents
	 * in this list will be sent a message containing the subquery specified on the object.
	 *
	 * Note: Make sure both the fields AgentID and QueryInfo are set. These are used in the query distribution process
	 *
	 * @return 	List of AgentQuery objects.
	 * @throws MissingExpertException if no list of agents can be constructed such that the query is
	 * decomposed completely
	 */
	@NotNull abstract List<AgentQuery> createAgentQueries() throws MissingExpertException, BadQueryException;
}