package org.uu.nl.goldenagents.sparql;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.LiteralImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.shared.Lock;
import org.uu.nl.goldenagents.decompose.expertise.ProvenanceTracer;
import org.uu.nl.goldenagents.netmodels.fipa.QueryResult;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.platform.Platform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class CachedModel {

	/** A map of status of all Data Source agents that should participate in answering this query **/
	private final Map<AgentID, SourceReplyStatus> participatingAgents = new HashMap<>();
	
	/** A map of partial models of all Data Source agents that should participate in answering this query **/
	private final Map<AgentID, Model> partialModels = new HashMap<>();

	/** Same as participatingAgents, but specifically for the current round of suggestions **/
	private final Map<AgentID, SourceReplyStatus> suggestingAgents = new HashMap<>();

	/** handles corner case:
	 * If the broker is still selecting data source agents to send the query to while all previously contacted
	 * data source agents already reply with their full answer, isFinished would be true even though not all data
	 * source agents have been contacted yet. Instead, the broker explicitly notifies this class if it is done contacting
	 * data source agents.
	 */
	private boolean doneContactingAgents = false;

	/** The cached model in which the intermediate query results are aggregated **/
	private final InfModel cachedModel;

	/** The query info object **/
	private final QueryInfo queryInfo;
	
	/** Total size of the collected partial models **/
	private long totalSize = 0;

	private UserQueryTrigger trigger;

	private ArrayList<ResourceImpl> focusEntities;
	private EntityList<String> serializableFocusEntities;

	/**
	 * Whether the user agent expects suggestions for the query to be generated
	 * based on this result
	 */
	private boolean suggestionsExpected = false;
	
	/**
	 * Create a cached model with an empty initial model
	 * @param queryInfo 	The query info object representing the user query
	 * @param reasoner	Reasoner that infers information based on collected data and linksets
	 */
	public CachedModel(QueryInfo queryInfo, UserQueryTrigger trigger, Reasoner reasoner) {
		this.queryInfo = queryInfo;
		this.cachedModel = ModelFactory.createInfModel(reasoner, ModelFactory.createDefaultModel());
		this.trigger = trigger;
	}

	/**
	 * If a data source agent can answer (part of) the given user query, it can be tracked by the cached model object
	 * through this method
	 * @param agentID 		AgentID of agent which is expected to provide (partial) results for this query
	 */
	public void addParticipatingAgent(AgentID agentID) {
		this.participatingAgents.put(agentID, SourceReplyStatus.WAITING);
		this.partialModels.put(agentID, ModelFactory.createDefaultModel());
	}

	/**
	 * If all agents which are expected to be able to contribute to the query result have been contacted, the broker
	 * should call this method.
	 *
	 * @param doneContactingAgents 	True iff all data source agents expected to be able to help in answering this query
	 *                              have been sent their partial query.
	 */
	public void setDoneContactingAgents(boolean doneContactingAgents) {
		this.doneContactingAgents = doneContactingAgents;
	}

	/**
	 * Set the status of one of the agents expected to be able to contribute to the query results as done. This means
	 * the agent has provided some results and is not expected to be able to provide further results.
	 *
	 * @param agentID 	AgentID which has provided all expected results
	 * @return 			True iff the status of this agent has been changed, false in the other case, which is that this
	 * 					agent was not expected to participate at any rate (not added with {@see CachedModel.addParticipatingAgent}?
	 */
	public boolean setAgentFinished(AgentID agentID) {
		if(this.participatingAgents.containsKey(agentID)) {
			this.participatingAgents.put(agentID, SourceReplyStatus.SUCCESS);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Set the status of one of the agents expected to be able to contribute to the query results as failed. This means
	 * the agent notified the broker of an error while querying the database. It may or may not have provided some
	 * results before the error occurred.
	 *
	 * @param agent 	AgentID which notified the user of an error in answering the query
	 * @return 			True iff the status of this agent has been changed, false in the other case, which is that this
	 * 					agent was not expected to participate at any rate (not added with {@see CachedModel.addParticipatingAgent}?
	 */
	public boolean setErrorForAgent(AgentID agent) {
		if(this.participatingAgents.containsKey(agent)) {
			this.participatingAgents.put(agent, SourceReplyStatus.FAILED);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @return true if all the expected models are added and no more replies from data source agents are expected
	 */
	public synchronized boolean isComplete() {
		return this.doneContactingAgents &&
				this.participatingAgents.values().stream().map(SourceReplyStatus::isSuccess).reduce(true, Boolean::logicalAnd);
	}
	
	/**
	 * Compares the number of expected models with the total number of successful and unsuccessful replies.
	 * Unsuccessful reply means the DB agent sends a message with error instead of a model.
	 * @return true if all the end points send their reply messages
	 */
	public synchronized boolean isFinished() {
		return this.doneContactingAgents &&
				this.participatingAgents.values().stream().map(SourceReplyStatus::isFinished).reduce(true, Boolean::logicalAnd);
	}

	/**
	 * Adds the linkset as a partial model to the cached model
	 *
	 * @param linksetModel 	Partial model representing the links between models that are provided by different data source 
	 * 						agents participating in answering this query
	 * @return 				Number of triples that were added to the cached model because of this partial model. Number
	 * 						may be different from the size of the partial model, because the reasoner may remove duplicates
	 * 						or add inferred triples.
	 */
	public long addLinksetAsModel(Model linksetModel) {
		this.cachedModel.enterCriticalSection(Lock.WRITE);
		long addedItems = 0;
		try {
			long previousSize = this.cachedModel.size();
			this.cachedModel.add(linksetModel);
			long newSize = this.cachedModel.size();
			addedItems = newSize - previousSize;
		} finally {
			this.cachedModel.leaveCriticalSection();
		}
		return addedItems;
	}
	
	/**
	 * Adds the partial model, as provided by one of the participating data source agents, to the cached model.
	 * @param agentID		agentID of the agent providing the partial data
	 * @param partialModel	Partial model representing a set of results, as provided by a data source agent participating
	 *                      in answering this query
	 * @return
	 */
	public long addPartialModel(AgentID agentID, Model partialModel) {
		Model agentModel = partialModels.get(agentID);
		agentModel.enterCriticalSection(Lock.WRITE);
		long addedItems = 0;
		try {
			long previousSize = agentModel.size();
			agentModel.add(partialModel);
			long newSize = agentModel.size();
			addedItems = newSize - previousSize;
		} finally {
			agentModel.leaveCriticalSection();
		}
		this.totalSize += addedItems;
		return addedItems;
	}
	
	private void finalizeCachedModel() {
		this.partialModels.forEach((aId, partial) -> {
			this.cachedModel.enterCriticalSection(Lock.WRITE);
			try {
				this.cachedModel.add(partial);
			} finally {
				this.cachedModel.leaveCriticalSection();
			}
		});	
	}

	/**
	 * @return 	Number of data source agents that are expected to be able to participate in answering the user query
	 */
	public synchronized int getExpectedSize() {
		return this.participatingAgents.size();
	}

	/**
	 * @return 	Number of data source agents which have provided all results they can to contribute to the user query
	 */
	public synchronized int getCurrentSize() {
		return (int) this.participatingAgents.values().stream().filter(SourceReplyStatus::isFinished).count();
	}

	/**
	 * @return Number of data source agents which could not provide all results that were expected to contribute to
	 * answering the user query
	 */
	public synchronized int getUnsuccessfullReplySize() {
		return (int) this.participatingAgents.values().stream().filter(v -> v.equals(SourceReplyStatus.FAILED)).count();
	}

	/**
	 * Returns the total size of the partial models
	 * @return the total size of the partial models
	 */
	public long getTotalSize() {
		return totalSize;
	}
	
	private void dump() {
		this.finalizeCachedModel();
		this.cachedModel.enterCriticalSection(Lock.READ);
		try {
			File file = new File("dump.nt");
			try(FileOutputStream out = new FileOutputStream(file)) {
				file.createNewFile();
				this.cachedModel.write(out, "N-TRIPLE");
			} catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			this.cachedModel.leaveCriticalSection();
		}
	}

	/**
	 * Perform the original user query on the model with all collected triples
	 * @return QueryResult
	 * @throws IOException
	 */
	public QueryResult querySelect() throws IOException {
		this.finalizeCachedModel();
		return this.query(queryInfo.getAliasedJenaQuery());
	}

	/**
	 * Perform a query on the cached model
	 * @param query		Query to perform
	 * @return			QueryResult
	 * @throws IOException
	 */
	public QueryResult query(Query query) throws IOException {
		this.cachedModel.enterCriticalSection(Lock.READ);
		try {
			QueryResult qResult = new QueryResult();
			try (QueryExecution qexec = QueryExecutionFactory.create(query, this.cachedModel)) {
				ResultSet results = qexec.execSelect();
				ProvenanceTracer pt = new ProvenanceTracer(queryInfo);
				pt.trace();
				try(ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
					ResultSetFormatter.outputAsJSON(byteArrayOutputStream, results);
					qResult = new QueryResult(byteArrayOutputStream, results.getRowNumber(), pt.getMappedSources());
				}
			}
			return qResult;
		} finally {
			this.cachedModel.leaveCriticalSection();
		}
	}

	public ArrayList<ResourceImpl> getEntitiesAtFocus() {
		return this.focusEntities;
	}

	public EntityList<String> getSerializableFocusEntities() {
		return this.serializableFocusEntities;
	}

	/**
	 * Add hoc and slow method to find entities that actually occur in participating sources.
	 * Probably a more general approach is required that makes clever use of SPARQL SERVICE keyword, to query over
	 * all the partial models. This approach could potentially also be used for detailed provenance information, but
	 * it requires some extra thinking on my part for which the capacity is currently missing
	 *
	 * @param query	Yes
	 * @return		Also yes
	 */
	public HashMap<AgentID, EntityList<String>> getEntitiesAtFocus(Query query) {
		this.focusEntities = new ArrayList<>();
		this.serializableFocusEntities = new EntityList<>();
		String var = query.getProjectVars().get(0).toString(); // TODO Don't take first var in projection; take FOCUS var

		// Find participating agents from provenance tracer
		ProvenanceTracer t = getProvenanceTracer();
		Map<String, Set<AgentID>> mappedSources = t.getMappedSources();
		Set<AgentID> relevantAgents = mappedSources.get(var);

		if(relevantAgents == null)  {
			Platform.getLogger().log(getClass(), Level.SEVERE,"No relevant agents found. This has to be an error");
			relevantAgents = new HashSet<>();
		}

		// Create aggregator for results
		HashMap<AgentID, EntityList<String>> relevantEntities = new HashMap<>();
		relevantAgents.iterator().forEachRemaining(x -> relevantEntities.put(x, new EntityList<>()));

		// Start query process
		query.setDistinct(true);
		try(QueryExecution exec = QueryExecutionFactory.create(query, this.cachedModel)) {
			ResultSet r = exec.execSelect();
			while(r.hasNext()) {
				RDFNode n = r.next().get(var);
				if(n.canAs(ResourceImpl.class)) {
					this.focusEntities.add(n.as(ResourceImpl.class));
					this.serializableFocusEntities.addEntity(n.asResource().getURI());
				} else if (n.canAs(Literal.class)) {
					Platform.getLogger().log(getClass(), Level.WARNING, "Can't convert node " + n.toString() + " to Individual.class but adding as Literal");
					this.serializableFocusEntities.addEntity(n.asLiteral().getString());
				} else {
					Platform.getLogger().log(getClass(), Level.WARNING, "Can't convert node " + n.toString() + " to Individual.class");
				}
				for(AgentID aid : relevantAgents) {
					if(this.partialModels.get(aid).containsResource(n)) {
						try {
							if (n.isResource() && n.canAs(ResourceImpl.class)) {
								relevantEntities.get(aid).addEntity(n.asResource().getURI());
							} else if (n.isLiteral() && n.canAs(Literal.class)) {
								relevantEntities.get(aid).addEntity(n.asLiteral().getString());
							}
						} catch (Exception e) {
							Platform.getLogger().log(getClass(), e);
						}
					}
				}
			}
		}

		return relevantEntities;
	}

	public ProvenanceTracer getProvenanceTracer() {
		ProvenanceTracer pt = new ProvenanceTracer(this.queryInfo);
		pt.trace();
		return pt;
	}

	/**
	 * The original query that broker has received from user agent
	 * @return The original query that broker has received from user agent
	 */
	public String getOriginalQuery() {
		return queryInfo.getOriginalQuery();
	}

	public void setSuggestionsExpected(boolean suggestionsExpected) {
		this.suggestionsExpected = suggestionsExpected;
	}

	public boolean isSuggestionsExpected() {
		return this.suggestionsExpected;
	}

	public void expectSuggestionsFrom(AgentID aid) {
		this.suggestingAgents.put(aid, SourceReplyStatus.WAITING);
	}

	public int getExpectedSuggestionAgents() {
		return this.suggestingAgents.size();
	}

	public boolean querySuggestionsDone() {
		for(AgentID aid : this.suggestingAgents.keySet()) {
			if(!this.suggestingAgents.get(aid).isFinished()) return false;
		}
		return true;
	}

	public boolean setAgentSuggestionsReceived(AgentID agentID, boolean success) {
		if(this.suggestingAgents.containsKey(agentID)) {
			this.suggestingAgents.put(agentID, success ? SourceReplyStatus.SUCCESS : SourceReplyStatus.FAILED);
			return true;
		}
		return false;
	}

	public UserQueryTrigger getUserQueryTrigger() {
		return trigger;
	}

	public void setTrigger(UserQueryTrigger trigger) {
		this.trigger = trigger;
	}

	public InfModel getCachedModel() {
		return cachedModel;
	}

	/**
	 * Represents the status of data source agents expected to participate in answering the user query
	 */
	enum SourceReplyStatus {

		WAITING(false, false), FAILED(true, false), SUCCESS(true, true);

		SourceReplyStatus(boolean finished, boolean success) {
			this.finished = finished;
			this.success = success;
		}

		private boolean finished;
		private boolean success;

		/**
		 * @return 	True iff this data source agent is not expected to provide any more results
		 */
		private boolean isFinished() {
			return this.finished;
		}

		/**
		 * @return True iff this data source agent successfully replied with all expected results
		 */
		private boolean isSuccess() {
			return this.success;
		}
	}

	public static class EntityList<T> implements FIPASendableObject {
		private ArrayList<T> entities;

		public EntityList(ArrayList<T> entities) {
			this.entities = entities;
		}

		public EntityList() {
			this.entities = new ArrayList<>();
		}

		public void addEntity(T entity) {
			this.entities.add(entity);
		}

		public ArrayList<T> getEntities() {
			return this.entities;
		}
	}
}
