package org.uu.nl.goldenagents.aql.feature;

import org.uu.nl.goldenagents.netmodels.jena.SerializableResourceImpl;

/**
 * An IRI Reference is anything that can be denoted with an IRI in an RDF graph
 */
public abstract class hasResource extends Feature {

    protected SerializableResourceImpl resource;

    /**
     * An optional NL label for easy representation. Provided by the graph as rdfs:label. If none is present,
     * the formal name of this resource will be used.
     */
    protected String label;

    public SerializableResourceImpl getResource() {
        return this.resource;
    }

    public hasResource(SerializableResourceImpl resource) {
        this.resource = resource;
    }

    public hasResource(SerializableResourceImpl resource, String label) {
        this.resource = resource;
        this.label = label;
    }

    @Override
    public String getFirstResourceLabel() {
        return this.resource.getLocalName();
    }

    /**
     * AQL label representing this node in the AQL query
     *
     * @return String
     */
    @Override
    public String getAQLLabel() {
        return label == null ? resource.getLocalName() : label;
    }
}
