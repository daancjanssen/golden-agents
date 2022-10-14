package org.uu.nl.goldenagents.aql.feature;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.VariableController;
import org.uu.nl.goldenagents.netmodels.jena.SerializableResourceImpl;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class NamedResource extends hasResource {

    public NamedResource(SerializableResourceImpl resource) {
        super(resource);
    }

    public NamedResource(SerializableResourceImpl resource, String label) {
        super(resource, label);
    }

    public Op toARQ(Var var, VariableController controller) {
        // See @code{Exclusion} for the issue here
        checkIfFocus(var, controller);
        return null;
    }

    /**
     * Convert this query to an AQL string recursively
     *
     * @return An AQL query string
     */
    @Override
    public String toAQLString() {
        return getAQLLabel();
    }

    /**
     * Convert this tree to a natural language representation
     *
     * @return NL representation of this query
     */
    @Override
    public String toNLQuery() {
        return null;
    }

    /**
     * Number of sub trees of this node type
     *
     * @return Integer indicating number of sub trees for this node type
     */
    @Override
    public int nSubtrees() {
        return 0;
    }

    /**
     * Replace a child of this node with a new sub tree
     *
     * @param child    Child to be replaced
     * @param newChild New sub tree
     */
    @Override
    public void replaceChild(UUID child, AQLTree newChild) throws IllegalArgumentException {
        throw new IllegalArgumentException("Named resource does not have any children that can be renamed");
    }

    /**
     * Get the subqueries for this tree. Subqueries are the edges of this node.
     *
     * @return List of subqueries (i.e. sub trees) for this node
     */
    @Override
    public List<AQLTree> getSubqueries() {
        return new LinkedList<>();
    }


}
