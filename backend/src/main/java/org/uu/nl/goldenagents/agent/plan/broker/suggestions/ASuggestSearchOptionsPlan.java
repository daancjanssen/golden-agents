package org.uu.nl.goldenagents.agent.plan.broker.suggestions;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.netmodels.angular.AQLSuggestions;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.defaults.messenger.MessageReceiverNotFoundException;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.Performative;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.platform.Platform;
import org.uu.nl.net2apl.core.platform.PlatformNotFoundException;

import java.io.IOException;
import java.util.*;

/**
 * This plan is executed to find query suggestions for a given AQL query received from the USER agent.
 *
 * This plan handles the message parsing and passing, but extraction of query suggestions still needs to be implemented
 */
public abstract class ASuggestSearchOptionsPlan extends MessagePlan {

    protected PlanToAgentInterface planInterface;
    protected AQLTree query;
    protected UUID focus;
    protected BrokerContext context;

    public ASuggestSearchOptionsPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
        super(message, header, content);
    }

    /**
     * This method is executed when a message has been received
     *
     * @param planInterface   An interface to the agent in order to access context, etc
     * @param receivedMessage The message that triggered this plan
     * @param header          The header of the message
     * @param content         The content of the message
     * @throws PlanExecutionError Exception thrown when executing this plan goes totally awry
     */
    @Override
    public void executeOnce(PlanToAgentInterface planInterface, ACLMessage receivedMessage, GAMessageHeader header, FIPASendableObject content) throws PlanExecutionError {
        this.planInterface = planInterface;
        this.context = planInterface.getContext(BrokerContext.class);
        this.query = (AQLTree) content;
        if(this.query == null) {
            this.query = context.getCachedModel(this.message.getConversationId()).getUserQueryTrigger().getAql().getQueryTree();
        }

        logger.log(getClass(), "Starting to aggregate classes suggestions for search");
        List<AQLSuggestions.TypeSuggestion> classList = generateClassList();
        logger.log(getClass(), "Starting to aggregate properties suggestions for search");
        List<AQLSuggestions.TypeSuggestion> propertyList = generatePropertyList();
        logger.log(getClass(), "Starting to aggregate entities suggestions for search");
        List<AQLSuggestions.InstanceSuggestion> instanceList = generateInstanceList();

        logger.log(getClass(), String.format("Sending suggestions with %d classes, %d properties and %d instances as message to user",
                classList.size(), propertyList.size(), instanceList.size()
        ));
        returnMessage(new AQLSuggestions(classList, propertyList, instanceList, this.query.getFocusID()));
    }



    protected Set<OntClass> findUpperClasses(Iterator<OntClass> iterator) {
        final Set<OntClass> upperClasses = new HashSet<>();
        while(iterator.hasNext()) {
            upperClasses.addAll(findUpperClasses(iterator.next()));
        }
        return upperClasses;
    }

    protected Set<OntProperty> findUpperProperties(Iterator<OntProperty> iterator) {
        final Set<OntProperty> upperProperties = new HashSet<>();
        while(iterator.hasNext()) {
            upperProperties.addAll(findUpperProperties(iterator.next()));
        }
        return upperProperties;
    }

    protected Set<OntClass> findUpperClasses(OntClass ontClass) {
        if(!ontClass.hasSuperClass() || ontClass.equals(ontClass.getSuperClass())) {
            return ontClass.isAnon() || !this.context.getOntologyClasses().contains(ontClass)
                    ? Collections.emptySet() : Collections.singleton(ontClass);
        } else {
            final Set<OntClass> superClasses = new HashSet<>();
            ontClass.listSuperClasses(true).forEachRemaining(x -> superClasses.addAll(findUpperClasses(x)));
            if(superClasses.isEmpty() && this.context.getOntologyClasses().contains(ontClass))
                superClasses.add(ontClass);
            return superClasses;
        }
    }

    protected Set<OntProperty> findUpperProperties(OntProperty ontProperty) {
        if(ontProperty.getSuperProperty() == null || ontProperty.getSubProperty().equals(ontProperty)) {
            return ontProperty.isAnon() ? Collections.emptySet() : Collections.singleton(ontProperty);
        } else {
            final Set<OntProperty> superProperties = new HashSet<>();
            ontProperty.listSuperProperties(true).forEachRemaining(x -> superProperties.addAll(findUpperProperties(ontProperty)));
            return superProperties;
        }
    }

    private void returnMessage(AQLSuggestions suggestions) {
        ACLMessage reply = this.message.createReply(this.planInterface.getAgentID(), Performative.INFORM_REF);
        try {
            reply.setContentObject(new GAMessageContentWrapper(GAMessageHeader.INFORM_SUGGESTIONS, suggestions));
            this.planInterface.getAgent().sendMessage(reply);
        } catch (IOException | MessageReceiverNotFoundException | PlatformNotFoundException e) {
            Platform.getLogger().log(getClass(), e);
        }
    }

    /**
     * Generate a list of classes the current query can be intersected with at the current focus
     */
    protected abstract List<AQLSuggestions.TypeSuggestion> generateClassList();

    /**
     * Generate a list of properties the current query can be intersected with at the current focus
     */
    protected abstract List<AQLSuggestions.TypeSuggestion> generatePropertyList();

    /**
     * Generate a list of instances the current query can be intersected with at the current focus
     */
    protected abstract List<AQLSuggestions.InstanceSuggestion> generateInstanceList();
}
