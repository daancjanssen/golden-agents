package org.uu.nl.goldenagents.agent.context.query;

import org.apache.jena.graph.Node;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.*;
import org.uu.nl.goldenagents.agent.context.DBAgentContext;
import org.uu.nl.goldenagents.agent.context.PrefixNSListenerContext;
import org.uu.nl.goldenagents.util.SparqlUtils;
import org.uu.nl.net2apl.core.agent.Context;
import org.uu.nl.net2apl.core.platform.Platform;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * This class stores the mapping model in a way that allows translating concepts quickly
 */
public class DbTranslationContext implements Context {

    private Model mappingModel;
    private HashMap<String, Translation> localToGlobalMapping = new HashMap<>();
    private HashMap<String, Translation> globalToLocalMapping = new HashMap<>();
    private final PrefixNSListenerContext prefixContext;

    public DbTranslationContext(Model mappingModel, PrefixNSListenerContext prefixContext) {
        this.mappingModel = mappingModel;
        this.prefixContext = prefixContext;
        StmtIterator it = mappingModel.listStatements();
        while(it.hasNext()) {
            processStatement(it.nextStatement());
        }
    }

    private void processStatement(Statement stmt) {
        Translation translation = new Translation(stmt, this.prefixContext);
        this.localToGlobalMapping.put(shortForm(translation.localConcept), translation);
        this.globalToLocalMapping.put(shortForm(translation.globalConcept), translation);
    }

    /**
     * Translate a concept as used in the local data source to its equivalent concept in the General Ontology
     * @param resource  Resource from local model to translate to General Ontology
     * @return          Equivalent resource from the General Ontology
     */
    public Resource translateLocalToGlobal(@NotNull Resource resource) {
        Translation translation = this.getLocalToGlobalTranslation(resource);
        if(translation != null) {
            return translation.getGlobalConcept();
        } else {
            Platform.getLogger().log(getClass(), Level.FINE,"No translation found for local concept " + resource.getURI());
            return null;
        }
    }

    public Translation getLocalToGlobalTranslation(Resource resource) {
        return this.localToGlobalMapping.get(shortForm(resource));
    }

    /**
     * Translate a concept as used in the General Ontology to its equivalent concept in the local data source
     * @param resource  Resource from General Ontology to translate to local model
     * @return          Equivalent resource from the local model
     */
    public Resource translateGlobalToLocal(Resource resource) {
        Translation translation = getGlobalToLocalTranslation(resource);
        if(translation != null) {
            return translation.getLocalConcept();
        } else {
            Platform.getLogger().log(getClass(), Level.FINE, "No translation found for global concept " + resource.getURI());
            return null;
        }
    }

    public Translation getGlobalToLocalTranslation(Resource resource) {
        return this.globalToLocalMapping.get(shortForm(resource));
    }

    /**
     * Get the short form of a resource URL
     * @param resource  Resource
     * @return          Short form (i.e. prefixed local name)
     */
    private String shortForm(Resource resource) {
        return this.mappingModel.shortForm(resource.getURI());
    }

    /**
     * This class contains tuples of concepts, one from the local data source, the other from the global ontology,
     * that share an equivalence-type relation
     */
    public class Translation {
        private Statement equivalenceStatement;
        private Resource localConcept;
        private Resource globalConcept;
        private Property originalEquivalenceRelation;
        private final PrefixNSListenerContext prefixContext;
        private boolean inverse = false;

        public Translation(Statement statement, PrefixNSListenerContext prefixContext) {
            this.prefixContext = prefixContext;
            this.originalEquivalenceRelation = statement.getPredicate();
            Resource subject = statement.getSubject();
            Resource object = statement.getObject().asResource();
            if(isGeneralConcept(subject.asNode()) && !isGeneralConcept(object.asNode())) {
                this.globalConcept = subject;
                this.localConcept = object;
            } else if (isGeneralConcept(object.asNode()) && !isGeneralConcept(subject.asNode())) {
                this.globalConcept = object;
                this.localConcept = subject;
            } else {
                this.globalConcept = subject;
                this.localConcept = object;
//                throw new IllegalStateException("Exactly one local and one global concept required");
                // TODO there are now concepts that both have the general ontology as prefix. Maybe translation should go both ways. In the mean time, just picking random
            }

            this.inverse = mappingModel.shortForm(this.originalEquivalenceRelation.asResource().getURI())
                    .equals("owl:inverseOf");
        }

        /**
         * Get the concept used in the local data source
         * @return  Resource
         */
        public Resource getLocalConcept() {
            return localConcept;
        }

        /**
         * Get the concept used in the global ontology
         * @return Resource
         */
        public Resource getGlobalConcept() {
            return globalConcept;
        }

        /**
         * Are these properties the inverse of each other?
         * @return  True if global and local property are each others inverses
         */
        public boolean isInverse() {
            return inverse;
        }

        public boolean isClassEquivalence() {
            return localConcept instanceof OntClass && globalConcept instanceof OntClass;
        }

        public boolean isPropertyEquivalence() {
            return localConcept instanceof OntProperty && globalConcept instanceof OntProperty;
        }

        public Statement getEquivalenceStatement() {
            return equivalenceStatement;
        }

        public Property getOriginalEquivalenceRelation() {
            return originalEquivalenceRelation;
        }

        /**
         * Test of a concept belongs to the General Ontology at use
         * @param node  Node to verify
         * @return      True iff Node is concept from the General Ontology at use
         */
        private boolean isGeneralConcept(Node node) {
            Set<String> namespaces = this.prefixContext.getNamespaces();
            return namespaces.contains(node.getNameSpace());
        }
    }

}
