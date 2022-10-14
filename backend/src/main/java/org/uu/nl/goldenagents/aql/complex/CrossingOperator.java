package org.uu.nl.goldenagents.aql.complex;

import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.netmodels.jena.SerializableResourceImpl;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class CrossingOperator extends AQLTree {

    protected SerializableResourceImpl resource;
    protected AQLTree subquery;
    protected String label;

    public CrossingOperator(SerializableResourceImpl resource, AQLTree subquery) {
        this.resource = resource;
        this.subquery = subquery;
        this.label = resource.getLocalName();
        this.subquery.setParent(getFocusID());
        this.type = TYPE.PROPERTY;
    }

    public CrossingOperator(SerializableResourceImpl resource, AQLTree subquery, String label) {
        this.resource = resource;
        this.subquery = subquery;
        this.label = label;
        this.subquery.setParent(getFocusID());
        this.type = TYPE.PROPERTY;
    }

    /**
     * Number of sub trees of this node type
     *
     * @return Integer indicating number of sub trees for this node type
     */
    @Override
    public int nSubtrees() {
        return 1;
    }

    /**
     * Replace a child of this node with a new sub tree
     *
     * @param child    Child to be replaced
     * @param newChild New sub tree
     */
    @Override
    public void replaceChild(UUID child, AQLTree newChild) throws IllegalArgumentException {
        if(this.subquery.getFocusID().equals(child)) {
            this.subquery = newChild;
            this.subquery.setParent(getFocusID());
        }
        else throw new IllegalArgumentException("Child to be replaced does not exist on this node");
    }

    /**
     * Get the subqueries for this tree. Subqueries are the edges of this node.
     *
     * @return List of subqueries (i.e. sub trees) for this node
     */
    @Override
    public List<AQLTree> getSubqueries() {
        return Collections.singletonList(this.subquery);
    }

    public AQLTree getSubquery() {
        return this.subquery;
    }

    public SerializableResourceImpl getResource() {
        return this.resource;
    }

    @Override
    public String getFirstResourceLabel() {
        return this.resource.getLocalName();
    }
}
