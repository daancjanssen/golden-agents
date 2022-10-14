package org.uu.nl.goldenagents.agent.plan.dbagent;

import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.arq.querybuilder.WhereBuilder;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.RDF;
import org.uu.nl.goldenagents.agent.context.DBAgentContext;
import org.uu.nl.goldenagents.agent.context.query.DbTranslationContext;
import org.uu.nl.goldenagents.agent.plan.MessagePlan;
import org.uu.nl.goldenagents.netmodels.AqlDbTypeSuggestionWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.sparql.CachedModel;
import org.uu.nl.goldenagents.util.Pair;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.defaults.messenger.MessageReceiverNotFoundException;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.Performative;
import org.uu.nl.net2apl.core.fipa.acl.UnreadableException;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;
import org.uu.nl.net2apl.core.platform.Platform;
import org.uu.nl.net2apl.core.platform.PlatformNotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * This plan uses SPARKLIS-type queries to find suggestions for an entity
 */
public class SparklisTypeSuggestionsPlan extends MessagePlan {

    private PlanToAgentInterface planInterface;

    public SparklisTypeSuggestionsPlan(ACLMessage message, GAMessageHeader header, FIPASendableObject content) {
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
        DBAgentContext context = planInterface.getContext(DBAgentContext.class);
        DbTranslationContext translator = planInterface.getContext(DbTranslationContext.class);
        List<String> entitiesValues = this.getEntities(300); // TODO what value can / should we use here?

        // Create query builders for each suggestion type
        Var var = Var.alloc("x");
        SelectBuilder classFinder = new SelectBuilder().addVar(var).setDistinct(true);
        SelectBuilder forwardCrossingFinder = new SelectBuilder().addVar(var).setDistinct(true);
        SelectBuilder backwardCrossingFinder = new SelectBuilder().addVar(var).setDistinct(true);

        logger.log(getClass(), String.format("Starting process to find suggestions for %d entities", entitiesValues.size()));

        int blankNodeIndex = 0;
        for (String entityValue : entitiesValues) {
            // NodeFactory.createBlankNode() is bugged and will not produce results. This hack does
            Var blankNode = Var.alloc(String.format("a%d", blankNodeIndex++));
            Node entityUri = NodeFactory.createURI(entityValue);

            // Add UNION WHERE clauses to each query builder
            classFinder.addUnion(new WhereBuilder().addWhere(entityUri, RDF.type, var));

            // p of q  --> ?y p ?x, with ?y fresh var
            forwardCrossingFinder.addUnion(new WhereBuilder().addWhere(blankNode, var, entityUri));

            // p : q --> ?x p ?y, with ?y fresh
            backwardCrossingFinder.addUnion(new WhereBuilder().addWhere(entityUri, var, blankNode));
        }

        Pair<ArrayList<Resource>> classSuggestions =
                this.executeQuery(context, translator, classFinder, var);
        Pair<ArrayList<Resource>> forwardCrossSuggestions =
                this.executeQuery(context, translator, forwardCrossingFinder, var);
        Pair<ArrayList<Resource>> backwardCrossSuggestions =
                this.executeQuery(context, translator, backwardCrossingFinder, var);

        // TODO did I switch these around the wrong way?
        ArrayList<Resource> forwardCrossingProperties = forwardCrossSuggestions.getFirst();
        ArrayList<Resource> backwardCrossingProperties = backwardCrossSuggestions.getFirst();

        // Translations can be the inverse, in which case they should be added to opposite list
        forwardCrossingProperties.addAll(backwardCrossSuggestions.getSecond());
        backwardCrossingProperties.addAll(forwardCrossSuggestions.getSecond());

        // Construct reply message by finding suggestions
        AqlDbTypeSuggestionWrapper messageWrapper = new AqlDbTypeSuggestionWrapper(
                classSuggestions.getFirst(),
                forwardCrossingProperties,
                backwardCrossingProperties,
                UUID.fromString(this.message.getConversationId())
        );

        ACLMessage response = message.createReply(planInterface.getAgentID(), Performative.INFORM_REF);
        try {
            response.setContentObject(new GAMessageContentWrapper(GAMessageHeader.INFORM_SUGGESTIONS, messageWrapper));
            logger.log(getClass(), "Sending query suggestions for query to broker");
            planInterface.getAgent().sendMessage(response);
        } catch (IOException | MessageReceiverNotFoundException | PlatformNotFoundException e) {
            logger.log(getClass(), e);
        }
    }

    /**
     * Parses broker message for entity list
     *
     * @param maxSize Maximum number of entities in this list (to avoid stack overflow in query construction)
     * @return List of entities, reduced to maxSize
     */
    private List<String> getEntities(int maxSize) {
        CachedModel.EntityList<String> entityList = new CachedModel.EntityList<>(new ArrayList<>());
        try {
            entityList = (CachedModel.EntityList<String>) ((GAMessageContentWrapper) message.getContentObject()).getContent();
        } catch (UnreadableException e) {
            logger.log(getClass(), e);
        }

        // Limit number of entities queried to avoid StackOverflow in Jena ElementWalker
        return entityList.getEntities().subList(0, Math.min(entityList.getEntities().size(), maxSize));
    }

    /**
     * Performs a query to find query suggestions on the database of this agent, and collects the results in a list
     *
     * @param context DBAgentContext of the current DB agent
     * @param query   A SelectBuilder query that requests the desired properties
     * @param getVar  The VAR used in the @{query} to collect the suggestions
     * @return Pair of List of suggestions generated by this query. The first element of the pair are the suggestions
     * themselves, the second element are inversed through translation and should thus be added to the opposite list
     */
    private <NodeType extends RDFNode> Pair<ArrayList<NodeType>> executeQuery(
            DBAgentContext context,
            DbTranslationContext translator,
            SelectBuilder query, Var getVar) {
        ArrayList<NodeType> result = new ArrayList<>();
        ArrayList<NodeType> inverse = new ArrayList<>();
        try (DBAgentContext.DbQuery q = context.getDbQuery(query.build())) {
            ResultSet results = q.queryExecution.execSelect();
            while (results.hasNext()) {
                Resource item = results.next().get(getVar.toString()).asResource();
                DbTranslationContext.Translation translation = translator.getLocalToGlobalTranslation(item);
                if(translation != null && !translation.isInverse()) {
                    result.add((NodeType) translation.getGlobalConcept());
                } else if (translation != null) {
                    inverse.add((NodeType) translation.getGlobalConcept());
                } else {
                    Platform.getLogger().log(getClass(), "Dropping resource " + item.toString() + " because it has no translation");
                }
            }
        } catch (Exception e) {
            String dataUri = this.planInterface.getContext(DBAgentContext.class).getRdfDataURI();
            String reason = e.getMessage() == null ? e.toString() : e.getMessage();
            Platform.getLogger().log(getClass(), Level.SEVERE, String.format("Error executing DB Suggestion query on %s! Because %s", dataUri, reason));
            Platform.getLogger().log(getClass(), Level.SEVERE, query.toString());
        }
        logger.log(getClass(), String.format("Suggestions for %s are %s", getVar.toString(), result.toString()));
        return new Pair<>(result, inverse);
    }
}
