package org.uu.nl.goldenagents.agent.plan.broker.suggestions;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.Resource;
import org.uu.nl.goldenagents.agent.context.BrokerSearchSuggestionsContext;
import org.uu.nl.goldenagents.netmodels.AqlDbTypeSuggestionWrapper;
import org.uu.nl.goldenagents.netmodels.angular.AQLSuggestions;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.sparql.CachedModel;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;

import java.util.*;
import java.util.stream.Collectors;

public class DbBasedSuggestSearchOptionsPlan extends ASuggestSearchOptionsPlan {

    public DbBasedSuggestSearchOptionsPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
        super(message, header, content);
    }

    /**
     * Generate a list of classes the current query can be intersected with at the current focus
     */
    @Override
    protected List<AQLSuggestions.TypeSuggestion> generateClassList() {
        Map<AgentID, AqlDbTypeSuggestionWrapper> dbSuggestions =
            searchSuggestionsContext.getDbSuggestionsForQuery(message.getConversationId(), this.userQueryTrigger.getAql());

        // Join suggested classes from all DB agents
        Set<OntClass> allClasses = new HashSet<>();
        List<AgentID> agentKeys = new ArrayList<>(dbSuggestions.keySet());
        for(AgentID aid : agentKeys) {
            allClasses.addAll(dbSuggestions.get(aid).getClassSuggestions(brokerContext.getOntology()));
        }

        // Create list of AQL type suggestions, filtering out all classes not in general ontology
        return findUpperClasses(allClasses.iterator()).stream()
                .map(x -> new AQLSuggestions.TypeSuggestion(x, this.brokerContext.getOntologyClasses()))
                .collect(Collectors.toList());
    }

    /**
     * Generate a list of properties the current query can be intersected with at the current focus
     */
    @Override
    protected List<AQLSuggestions.TypeSuggestion> generatePropertyList() {
        // Preparation
        Map<AgentID, AqlDbTypeSuggestionWrapper> dbSuggestions =
                searchSuggestionsContext.getDbSuggestionsForQuery(message.getConversationId(), this.userQueryTrigger.getAql());

        // Join suggested properties from all DB agents
        Set<OntProperty> forwardProperties = new HashSet<>();
        Set<OntProperty> backwardProperties = new HashSet<>();
        List<AgentID> agentKeys = new ArrayList<>(dbSuggestions.keySet());
        for(AgentID aid : agentKeys) {
            forwardProperties.addAll(dbSuggestions.get(aid).getForwardCrossingOntologyProperties(this.brokerContext.getOntology()));
            backwardProperties.addAll(dbSuggestions.get(aid).getBackwardCrossingOntologyProperties(this.brokerContext.getOntology()));
        }

        // Reduce set to upper properties only
        forwardProperties = findUpperProperties(forwardProperties.iterator());
        backwardProperties = findUpperProperties(backwardProperties.iterator());

        List<AQLSuggestions.TypeSuggestion> suggestions = new ArrayList<>();

        // Create suggestion object for each remaining property (both forwards and backwards crossing), filtering
        // out all properties that are not in the general ontology
        for(OntProperty prop : forwardProperties) {
            suggestions.add(new AQLSuggestions.TypeSuggestion(prop, true, this.brokerContext.getOntologyProperties()));
        }
        for(OntProperty prop : backwardProperties) {
            suggestions.add(new AQLSuggestions.TypeSuggestion(prop, false, this.brokerContext.getOntologyProperties()));
        }

        return suggestions;
    }

    /**
     * Generate a list of instances the current query can be intersected with at the current focus
     */
    @Deprecated
    @Override
    protected List<AQLSuggestions.InstanceSuggestion> generateInstanceList() {
        List<AQLSuggestions.InstanceSuggestion> suggestions = new ArrayList<>();
        BrokerSearchSuggestionsContext.SearchSuggestionSubscription subscription =
                this.searchSuggestionsContext.getSubscription(this.conversationID);
        CachedModel m = subscription.getModel();
        if (m != null) {
            ArrayList<Resource> individuals = subscription.getSearchSuggestions(this.userQueryTrigger.getAql()).getEntitiesAtFocus();
            suggestions = individuals.subList(0, Math.min(individuals.size(), 200)).stream().map(AQLSuggestions.InstanceSuggestion::new).collect(Collectors.toList());
        }
        return suggestions;
    }
}
