package org.uu.nl.goldenagents.netmodels.jena;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.rdf.model.impl.ResourceImpl;

import java.io.Serializable;

public class SerializableResourceImpl extends ResourceImpl implements Serializable {
    /**
     * the master constructor: make a new Resource in the given model,
     * rooted in the given node.
     * <p>
     * NOT FOR PUBLIC USE - used in ModelCom [and ContainerImpl]
     *
     * @param n
     * @param m
     */
    public SerializableResourceImpl(Node n, ModelCom m) {
        super(n, m);
    }

    /**
     * Creates new ResourceImpl
     */
    public SerializableResourceImpl() {
    }

    public SerializableResourceImpl(ModelCom m) {
        super(m);
    }

    public SerializableResourceImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    public SerializableResourceImpl(String uri) {
        super(uri);
    }

    public SerializableResourceImpl(String nameSpace, String localName) {
        super(nameSpace, localName);
    }

    public SerializableResourceImpl(AnonId id) {
        super(id);
    }

    public SerializableResourceImpl(AnonId id, ModelCom m) {
        super(id, m);
    }

    public SerializableResourceImpl(String uri, ModelCom m) {
        super(uri, m);
    }

    public SerializableResourceImpl(Resource r, ModelCom m) {
        super(r, m);
    }

    public SerializableResourceImpl(String nameSpace, String localName, ModelCom m) {
        super(nameSpace, localName, m);
    }
}
