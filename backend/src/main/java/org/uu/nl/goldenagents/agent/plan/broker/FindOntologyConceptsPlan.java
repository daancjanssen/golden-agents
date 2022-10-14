package org.uu.nl.goldenagents.agent.plan.broker;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.context.BrokerPrefixNamespaceContext;
import org.uu.nl.goldenagents.agent.plan.LoadRDFSourcePlan;
import org.uu.nl.goldenagents.agent.trigger.goal.broker.LoadConceptsGoal;
import org.uu.nl.goldenagents.decompose.expertise.DbAgentExpertise;
import org.uu.nl.goldenagents.netmodels.angular.ExpertiseModel;
import org.uu.nl.goldenagents.util.SparqlUtils;
import org.uu.nl.goldenagents.util.agentconfiguration.RdfSourceConfig;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This plan reads the ontology to find all concepts (classes and properties) that can be used
 * in a query, such that the broker agent is able to parse the query. These base concepts can be used
 * by the QuerySuggestionPlan and are stored in the BrokerContext
 *
 * It is executed every time a new data source agent announces itself to the broker
 */
public class FindOntologyConceptsPlan extends LoadRDFSourcePlan {

	private final LoadConceptsGoal goal;

	public FindOntologyConceptsPlan(LoadConceptsGoal goal) {
		this.goal = goal;
	}

	/**
	 * Execute the business logic of the plan. Make sure that when you implement this
	 * method that the method will return. Otherwise it will hold up other agents that
	 * are executed in the same thread. Also, if the plan should only be executed once,
	 * then make sure that somewhere in the method it calls the setFinished(true) method.
	 *
	 * @param planInterface
	 * @throws PlanExecutionError If you throw this error than it will be automatically adopted as an internal trigger.
	 */
	@Override
	public void executeOnce(PlanToAgentInterface planInterface) throws PlanExecutionError {
		BrokerContext brokerContext = planInterface.getAgent().getContext(BrokerContext.class);
		OntModel model;
//<<<<<<< HEAD
		if(!brokerContext.isAllOntologiesLoaded()) {
			BrokerPrefixNamespaceContext prefixContext = planInterface.getContext(BrokerPrefixNamespaceContext.class);
			if ((model = brokerContext.getOntology()) == null) {
				model = ModelFactory.createOntologyModel();
				brokerContext.setOntology(model);
			}
			loadModel(planInterface, model, brokerContext.getOntologyConfigs());
			boolean shouldUpdatePrefixes = findPreferredPrefix(prefixContext, model);
			shouldUpdatePrefixes |= storePrefixesFromSourceConfiguration(brokerContext, prefixContext);
			SparqlUtils.updatePrefixesInModel(model, prefixContext.getOntologyPrefixes());

			Map<String, String> modelPrefixes = model.getNsPrefixMap();
			shouldUpdatePrefixes |= addMissingPrefixesToMap(model, prefixContext);
			Map<String, String> updatedModelPrefixes = model.getNsPrefixMap();

			if(shouldUpdatePrefixes) {
				planInterface.adoptPlan(new SendPreferredNamespacePrefixesPlan());
			}

		} else {
			model = brokerContext.getOntology();
		}

		processOntology(brokerContext, model);

//=======
//		if((model = brokerContext.getOntology()) == null) {
//			model = createOntModel(planInterface, c);
//			brokerContext.setOntology(model);
//		}
//
//		processOntology(c, model);
//>>>>>>> feature/UI
		// TODO somehow re-trigger the SimpleSuggestSearchOptionsPlan? If it had already been triggered? If so, with what query?

		// Done!
		this.goal.setAchieved(true);
	}

	/**
	 * Store all prefixes specified in the configuration of this RDF source in the broker NS prefix map
	 * @param brokerContext	Broker context
	 * @param prefixContext Broker NS prefix context
	 * @return 	True iff the prefix map has been updated, indicating subscribing agents should be updated with the
	 * new NS map
	 */
	private boolean storePrefixesFromSourceConfiguration(BrokerContext brokerContext, BrokerPrefixNamespaceContext prefixContext) {
		boolean shouldUpdatePrefixes = false;
		for(RdfSourceConfig conf : brokerContext.getOntologyConfigs()) {
			shouldUpdatePrefixes |= prefixContext.addAllPrefixes(conf.getPrefixes());
		}
		return shouldUpdatePrefixes;
	}

	/**
	 * The model may contain prefixes not officially specified as core prefixes. To ensure data source agents use
	 * the same prefixes for the same IRI's, add those prefixes and namespaces to the NS prefix map
	 * @param model			Model with prefixes and namespaces
	 * @param prefixContext Broker NS prefix context
	 * @return 	True iff the broker NS prefix map has changed
	 */
	private boolean addMissingPrefixesToMap(Model model, BrokerPrefixNamespaceContext prefixContext) {
		Map<String, String> prefixMap = model.getNsPrefixMap();
		boolean shouldUpdate = false;
		for(String prefix : prefixMap.keySet()) {
			if (!prefixContext.getOntologyPrefixes().containsKey(prefix)) {
				shouldUpdate |= prefixContext.setPrefix(prefix, prefixMap.get(prefix));
			}
		}
		return shouldUpdate;
	}

	/**
	 * If the ontology specifies the preferred prefix using the PURL vocab, try to extract it
	 * @param prefixContext Prefix context of broker agent
	 * @param model		The loaded ontology
	 */
	private boolean findPreferredPrefix(BrokerPrefixNamespaceContext prefixContext, OntModel model) {
		Query preferredNsQuery = SparqlUtils.createPreferredPrefixQuery("prefix", "ns");
		ResultSet result = QueryExecutionFactory.create(preferredNsQuery, model).execSelect();
		boolean shouldUpdatePrefixes = false;
		while(result.hasNext()) {
			QuerySolution solution = result.next();
			String prefix = solution.get("?prefix").asLiteral().getString();
			String namespace = ensureNamespaceComplete(model, solution.get("?ns").asResource().getURI());;
			shouldUpdatePrefixes |= prefixContext.setPrefix(prefix, namespace);
		}
		return shouldUpdatePrefixes;
	}

	/**
	 * A namespace should end with a hashtag ('#') or forward slashs ('/') to work to abbreviate IRI's in the model.
	 * However, when specifying such a namespace while not testing, this trailing character is easy to miss.
	 * This method tries to complete the namespace with the trailing character if missing, by finding a resource
	 * that starts with the provided namespace, and getting the official namespace for that IRI.
	 *
	 * @param model		Model with resources from the namespace
	 * @param namespace The namespace to complete
	 * @return	Namespace with trailing character added if missing
	 */
	private String ensureNamespaceComplete(OntModel model, String namespace) {
		if(SparqlUtils.NAMESPACE_ENDS_WITH.contains(namespace.charAt(namespace.length() - 1))) {
			return namespace;
		}
		String resolvedNamespace = returnFirstNamespaceStartingWith(model.listClasses(), namespace);
		if(resolvedNamespace == null) {
			resolvedNamespace = returnFirstNamespaceStartingWith(model.listAllOntProperties(), namespace);
		}

		// If its still null, we did the best we could
		return resolvedNamespace == null ? namespace : resolvedNamespace;
	}

	/**
	 * Select the first resource in an iterator from which the IRI starts with the passed namespace string
	 * @param iterator	Resource iterator
	 * @param namespace	Namespace to match on
	 * @return Official namespace of resource corresponding to the passed namespace
	 */
	private String returnFirstNamespaceStartingWith(ExtendedIterator<? extends OntResource> iterator, String namespace) {
		while(iterator.hasNext()) {
			OntResource next = iterator.next();
			if (next.getNameSpace().startsWith(namespace)) {
				return next.getNameSpace();
			}
		}
		return null;
	}

	private void processOntology(BrokerContext c, OntModel model) {
		// Prepare aggregators for classes and properties used in ontology
		Set<String> mappedClassLabels = new HashSet<>();
		Set<String> mappedPropertyLabels = new HashSet<>();

		// Find classes and properties that actually have a mapping to some database
		for(DbAgentExpertise expertise : c.getDbAgentExpertises().values()) {
			ExpertiseModel[] netmodel = expertise.toNetModel();
			for(ExpertiseModel expertiseModel : netmodel) {
				if(expertiseModel.isClass()) {
					mappedClassLabels.add(expertiseModel.getLabel());
				} else {
					mappedPropertyLabels.add(expertiseModel.getLabel());
				}
			}
		}

		if(mappedClassLabels.isEmpty() && mappedPropertyLabels.isEmpty()) {
			// If no mapping is available, display everything as a suggestion
			c.setClassesAndProperties(model.listClasses().toSet(), model.listAllOntProperties().toSet());
		} else {
			// Filter classes and properties from the ontology that do not have a mapping to any database in the final list
			Set<OntClass> availableClasses = extractAvailableOntologyResources(model, model.listClasses(), mappedClassLabels);
			Set<OntProperty> availableProperties = extractAvailableOntologyResources(model, model.listAllOntProperties(), mappedPropertyLabels);

			// Set the remaining classes and properties as potential suggestions for this query
			c.setClassesAndProperties(availableClasses, availableProperties);
		}
	}

	/**
	 * Find the ontology resources from our model, that are also mapped to at least one data source agent
	 * @param iterator			Ontology resource iterator from model
	 * @param mappedLabels		List of labels of ontology resource type that are mapped to at least one data source agent
	 * @param <T>				Ontology resource type
	 * @return					Set of ontology resources
	 */
	private <T extends OntResource> Set<T> extractAvailableOntologyResources(Model model, ExtendedIterator<T> iterator, Set<String> mappedLabels) {
		Set<T> availableClasses = new HashSet<>();
		while(iterator.hasNext()) {
			T nextOntResource = iterator.next();
			if (!nextOntResource.isAnon()) {
				String shortForm = model.shortForm(nextOntResource.getURI());
				if (mappedLabels.contains(shortForm)) {
					availableClasses.add(nextOntResource);
				}
			}
		}
		return availableClasses;
	}
}
