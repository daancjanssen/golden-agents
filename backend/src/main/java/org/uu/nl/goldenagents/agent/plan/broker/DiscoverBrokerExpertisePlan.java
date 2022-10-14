package org.uu.nl.goldenagents.agent.plan.broker;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.decompose.expertise.DbAgentExpertise;
import org.uu.nl.goldenagents.decompose.expertise.ExpertiseGraphHandler;
import org.uu.nl.goldenagents.decompose.expertise.model.ExpertiseGraph;
import org.uu.nl.goldenagents.decompose.expertise.model.ExpertiseNode;
import org.uu.nl.goldenagents.sparql.OntologicalConceptInfo;
import org.uu.nl.goldenagents.util.CollectionUtils;
import org.uu.nl.goldenagents.decompose.expertise.model.ExpertiseEdge;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.logging.Loggable;
import org.uu.nl.net2apl.core.plan.Plan;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.platform.Platform;

/**
 * Plan to discover expertise of Broker Agent in the form of expertise graph
 * 
 * @author Golden Agents Group, Utrecht University
 */
public class DiscoverBrokerExpertisePlan extends Plan {
	
	private static Loggable LOGGER = Platform.getLogger();
	private ExpertiseGraph<String> brokerExpertise;
	
	@Override
	public void execute(PlanToAgentInterface planInterface) throws PlanExecutionError {
		//TODO Implement reading from a file
		BrokerContext context = planInterface.getContext(BrokerContext.class);
		if((brokerExpertise = context.getExpertiseGraph()) == null) {
			if (initExpertise(context.getDbAgentExpertises())) {
				context.setExpertiseGraph(this.brokerExpertise);
			}
		}
		if(context.getDbAgentExpertises().size() == 1) {
			context.getDbAgentExpertises().forEach(
					(aid, expertise) -> addAgent(aid, expertise));
		}
		else {
			addNewcomer(context.getDbAgentExpertises(), context.getLinkMap());
		}
		ExpertiseGraphHandler<String> egHandler = new ExpertiseGraphHandler<>(this.brokerExpertise);
		egHandler.fullAnalysis();
		LOGGER.log(this.getClass(), Level.INFO, "Broker Agent updated the graph:\n" + 
				context.getExpertiseGraph().summarizeSize());
	}
	
	/**
	 * Initializes the broker expertise
	 * @param dbExpertises
	 * @return the initialized broker expertise
	 */
	private boolean initExpertise(Map<AgentID, DbAgentExpertise> dbExpertises) {
		try {
			this.brokerExpertise = new ExpertiseGraph<>();
		}
		catch (Exception e) {
			LOGGER.log(this.getClass(), Level.WARNING, "Broker Agent could not initialize the Expertise Graph!");
			LOGGER.log(this.getClass(), e);
			return false;
		}
		return true;
	}
	
	private void addAgent(AgentID aid, DbAgentExpertise expertise) {
		addNodes(aid, expertise);
		addSelfEdges(aid, expertise);
	}
	
	private void addNodes(AgentID aid, DbAgentExpertise expertise) {
		ExpertiseNode<String> node = new ExpertiseNode<>(aid);
		expertise.getCapabilities().forEach(capability -> {
			node.setCount(capability, expertise.getCountOfConcept(capability));
		});
		this.brokerExpertise.addNode(node);
	}
	
	private void addSelfEdges(AgentID aid, DbAgentExpertise expertise) {
		for(OntologicalConceptInfo info : expertise.getExpertInfo()) {
			info.getStarCombinationCounts().forEach((label, value) -> {
				addEdge(aid, aid, info.getLabel(), label, value);
			});
		}
	}
	
	private void addEdge(AgentID source, AgentID target, String sourceProperty, String targetProperty, int value) {
		ExpertiseEdge<String> edge = 
				new ExpertiseEdge<>(source, target, sourceProperty, targetProperty);
		edge.setValue(value);
		this.brokerExpertise.addEdge(edge);
		this.brokerExpertise.addEdge(edge.reverseCopy());
	}
	
	/**
	 * Adds the new DB Agent to the broker expertise
	 * @param dbExpertises expertise info of db agents
	 * @param linkMap map of the linksets 
	 */
	private void addNewcomer(Map<AgentID, DbAgentExpertise> dbExpertises, Map<String, Set<String>> linkMap) {
		Set<AgentID> added = new HashSet<>(dbExpertises.keySet());
		added.removeAll(this.brokerExpertise.getAgents());
		LOGGER.log(this.getClass(), Level.INFO, "Agents: " + this.brokerExpertise.getAgents() 
				+ " and the new one:" + added.toString());
		for(AgentID aid : added) {
			addAgent(aid, dbExpertises.get(aid));
			discoverEdges(dbExpertises, aid, linkMap);
		}	
	}
	
	/**
	 * Discover the relations between data sources for property pairs and
	 * creates an edge for each pair
	 * @param dbExpertises expertise info of db agents
	 * @param newcomer ID of the agent that will be added
	 * @param linkMap map of the linksets 
	 */
	private void discoverEdges(Map<AgentID, DbAgentExpertise> dbExpertises, AgentID newcomer, Map<String, Set<String>> linkMap) {
		DbAgentExpertise newcomerExpertise = dbExpertises.get(newcomer);
		for(AgentID aid : dbExpertises.keySet()) {
			if(aid.equals(newcomer)) {
				continue;
			}
			DbAgentExpertise otherExpertise = dbExpertises.get(aid);
			for(OntologicalConceptInfo concept1 : newcomerExpertise.getExpertInfo()) {
				for(OntologicalConceptInfo concept2 : otherExpertise.getExpertInfo()) {
					if(concept1.getLabel().equals(concept2.getLabel())) {
						Set<String> union = CollectionUtils.union(concept1.getEntities(), concept2.getEntities());
						addEdge(newcomer, aid, concept1.getLabel(), concept2.getLabel(), union.size());
					} else {
						/* Finds the entities that have the same URI in both of the databases */
						Set<String> intersection = CollectionUtils.intersect(
								concept1.getEntities(), concept2.getEntities());
						/* Finds the entities that do not have the same URI but linked in the linksets */
						Set<String> entitiesFromLinkset = CollectionUtils.jointRemainAll(
								linkMap.values(), concept1.getEntities(), concept2.getEntities());
						intersection.addAll(entitiesFromLinkset);
						/* Adds the total number of entities as the edge value */
						addEdge(newcomer, aid, concept1.getLabel(), concept2.getLabel(), intersection.size());
					}
				}
			}
		}
	}
}
