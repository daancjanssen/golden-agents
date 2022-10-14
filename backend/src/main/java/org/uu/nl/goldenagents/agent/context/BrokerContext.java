package org.uu.nl.goldenagents.agent.context;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.reasoner.rulesys.Util;
import org.uu.nl.goldenagents.agent.context.registration.MinimalFunctionalityContext;
import org.uu.nl.goldenagents.decompose.expertise.DbAgentExpertise;
import org.uu.nl.goldenagents.decompose.expertise.model.ExpertiseGraph;
import org.uu.nl.goldenagents.netmodels.AqlDbTypeSuggestionWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.goldenagents.sparql.CachedModel;
import org.uu.nl.goldenagents.sparql.QueryInfo;
import org.uu.nl.goldenagents.util.AgentUtils;
import org.uu.nl.goldenagents.util.agentconfiguration.RdfSourceConfig;
import org.uu.nl.net2apl.core.agent.AgentID;

import java.util.*;


public class BrokerContext extends MinimalFunctionalityContext {
	private OntModel ontology;
	private Model linkset;

	private final Map<String, Set<String>> linkMap = new LinkedHashMap<>();
	/** Reasoner that only infers owl:sameAs relations **/
	private final Reasoner owlSameAsReasoner;
////<<<<<<< HEAD
//	private final Map<String, AgentID> conversationMap = new HashMap<>();
//	private final Map<String, CachedModel> queryCachedModels = new HashMap<>();
//	private final Map<AgentID, DbAgentExpertise> dbAgentExpertises = new HashMap<>();
//	private final Map<String, String> queryIDMap = new HashMap<>();
////=======
	private final Map<String, AgentID> conversationMap = new HashMap<>();
	private final Map<String, CachedModel> queryCachedModels = new HashMap<>();
	private final Map<AgentID, DbAgentExpertise> dbAgentExpertises = new HashMap<>();
	private final Map<String, Map<AgentID, AqlDbTypeSuggestionWrapper>> aqlSuggestionMap = new HashMap<>();
	private final Map<String, String> queryIDMap = new HashMap<>();
//>>>>>>> feature/UI
	private Set<OntClass> ontologyClasses;
	private Set<OntProperty> ontologyProperties;
	private ExpertiseGraph<String> expertiseGraph;
	private final List<RdfSourceConfig> ontologyConfigs;
	private final List<RdfSourceConfig> linksetConfigs;

	public BrokerContext(List<RdfSourceConfig> ontologyConfigs, List<RdfSourceConfig> linksetConfigs) {
		this.ontologyConfigs = ontologyConfigs;
		this.owlSameAsReasoner = new GenericRuleReasoner(
				Rule.parseRules(Util.loadRuleParserFromResourceFile(AgentUtils.OWL_SAME_AS_RULES))
		);
		this.linksetConfigs = linksetConfigs;
		this.linkset = ModelFactory.createDefaultModel();
	}

	public Model getLinkset() {
		return this.linkset;
	}
	
	public void addLinkset(Model m) {
		//We should consider keeping the linksets separate
		try {
			this.linkset.add(m);
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: JENA throws interesting errors related to model, handle exception
		}
		addLinksToMap(m);
	}
	
	private void addLinksToMap(Model m) {
		StmtIterator it = m.listStatements();
		while(it.hasNext()) {
			Statement statement = it.next();
			String object = statement.getObject().toString();
			String subject = statement.getSubject().toString();
			//If the object is already linked to other entities
			if(linkMap.containsKey(object)) {
				linkMap.get(object).add(subject);
			}
			//If the subject is already linked to other entities
			else if(linkMap.containsKey(subject)) {
				linkMap.get(subject).add(object);
			} 
			else {
				Set<String> entities = new HashSet<>();
				entities.add(subject);
				entities.add(object);
				/*
				 * We do not know which one (subject or object) will appear
				 * in other linksets and therefore, we create an entry for 
				 * both of them with the same set. Since they address the same set,
				 * it is space-efficient, but the size of the map size is equal to the
				 * total number of entities (to be able to do fast retrieve for any entity)
				 */
				linkMap.put(subject, entities);
				linkMap.put(object, entities);
			}
		}
	}
	
	public void addConversation(String conversation, AgentID user) {
		conversationMap.put(conversation, user);
	}
	
	public AgentID getConversationUser(String convoId) {
		return conversationMap.get(convoId);
	}
	
	public CachedModel getCachedModel(String conversation) {
		return queryCachedModels.get(conversation);
	}
	
	public void removeCachedModel(String conversation) {
		queryCachedModels.remove(conversation);
	}
	
	public CachedModel createCachedModel(String conversation, UserQueryTrigger trigger, QueryInfo queryInfo) {
		CachedModel model = null;
		if(!queryCachedModels.containsKey(conversation)) {
			//different reasoners can be used based on the conversation/query
			model = new CachedModel(queryInfo, trigger, this.owlSameAsReasoner);
			model.addLinksetAsModel(this.linkset);	//adds linkset as a partial model
			queryCachedModels.put(conversation, model);
		}
		return model;
	}
	
	public void addDbAgentExpertise(AgentID dbAgentID, DbAgentExpertise dbAgentExpertise) {
		this.dbAgentExpertises.put(dbAgentID, dbAgentExpertise);
	}

	public Map<AgentID, DbAgentExpertise> getDbAgentExpertises() {
		return dbAgentExpertises;
	}
	
	public Map<AgentID, List<String>> getDbAgentCapabilities(){
		Map<AgentID, List<String>> dbAgentCapabilities = new HashMap<>();
		this.dbAgentExpertises.forEach((agentID, expertise) -> {
			dbAgentCapabilities.put(agentID, expertise.getCapabilities());

		});
		return dbAgentCapabilities;
	}

	public void addQueryID(String conversationID, String queryID) {
		this.queryIDMap.put(conversationID, queryID);
	}

	public String getQueryID(String conversationID) {
		return this.queryIDMap.get(conversationID);
	}

	/**
	 * Set the set of classes and properties used in the top level ontology
	 * @param classes 		Set of classes used in top level ontology
	 * @param properties	Set of properties used in top level ontology
	 */
	public void setClassesAndProperties(Set<OntClass> classes, Set<OntProperty> properties) {
		this.ontologyClasses = classes;
		this.ontologyProperties = properties;
	}

	public Set<OntClass> getOntologyClasses() {
		return this.ontologyClasses;
	}

	public Set<OntProperty> getOntologyProperties() {
		return this.ontologyProperties;
	}

	/**
	 * This method checks if minimal functionality required by the agent implementing this
	 * class is ready. Without this functionality, this agent cannot function, but even
	 * with this functionality, some other functionality not required for basic functioning may
	 * not be ready yet
	 *
	 * @return True iff all minimal functionality required for this agent to function is ready
	 */
	@Override
	public boolean minimalFunctionalityReady() {
		return true;
	}

	/**
	 * Check if all functionality, both minimal required for this agent to function, and additional
	 * functionality, is ready, so this agent can function to its full potential.
	 *
	 * @return True iff all functionality potentially provided by this agent is ready
	 */
	@Override
	public boolean fullFunctionalityReady() {
		return this.dbAgentExpertises.size() > 0 && this.ontologyClasses != null && this.ontologyProperties != null;
	}

	public ExpertiseGraph<String> getExpertiseGraph() {
		return expertiseGraph;
	}

	public void setExpertiseGraph(ExpertiseGraph<String> expertiseGraph) {
		this.expertiseGraph = expertiseGraph;
	}

	public List<RdfSourceConfig> getOntologyConfigs() {
		return ontologyConfigs;
	}

	public OntModel getOntology() {
		return ontology;
	}

	public void setOntology(OntModel ontology) {
		this.ontology = ontology;
	}

	public void addOntology(OntModel ontology) {
		if(this.ontology == null) {
			this.ontology = ontology;
		} else {
			this.ontology.add(ontology);
		}
	}

	public void addDbSuggestionsForQuery(String conversationID, AgentID dbAID, AqlDbTypeSuggestionWrapper suggestions) {
		if(!this.aqlSuggestionMap.containsKey(conversationID)) {
			this.aqlSuggestionMap.put(conversationID, new HashMap<>());
		}
		this.aqlSuggestionMap.get(conversationID).put(dbAID, suggestions);
	}

	public HashMap<AgentID, AqlDbTypeSuggestionWrapper> getDbSuggestionsForQuery(String conversationID) {
		if(this.aqlSuggestionMap.containsKey(conversationID)) {
			return new HashMap<>(this.aqlSuggestionMap.get(conversationID));
		}
		return null;
	}
	public List<RdfSourceConfig> getLinksetConfigs() {
		return linksetConfigs;
	}

	public Map<String, Set<String>> getLinkMap() {
		return linkMap;
	}

	public boolean isAllOntologiesLoaded() {
		return this.ontologyConfigs.stream().allMatch(RdfSourceConfig::isLoaded);
	}
}