package org.uu.nl.goldenagents.aql.misc;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.VariableController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Exclusion extends AQLTree {

    private static final String AQL_LABEL = "not";
    private AQLTree negatedQuery;

    public Exclusion(AQLTree negatedQuery) {
        this.negatedQuery = negatedQuery;
        this.negatedQuery.setParent(getFocusID());
    }

    /**
     * AQL label representing this node in the AQL query
     *
     * @return String
     */
    @Override
    public String getAQLLabel() {
        return AQL_LABEL;
    }

    public Op toARQ(Var var, VariableController controller) {
        // TODO FILTER needs both an OP and an expression. However, the child of this node is only
        // an expression. Do we somehow need to add the query the filter is being applied on to this
        // node as well? That is a problem, as that query is everything in the tree <i>except</i> this
        // node, i.e. everything that is inaccessible from here.

        // Another idea that fails: Keeping track of filter expressions and adding them at the end to the
        // entire query, as this breaks nested filters. E.G.:
        /*
        FILTER (NOT EXISTS {?a ex:prop ?b FILTER (NOT EXISTS {?a ex:prop ex:specificitem})})
         */
        // The above BGP should filter all ?a's from the result that have a property ex:prop, except
        // if the object of that property is ex:specificitem. This would be flattened if added at the
        // end
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
        return "not ( " + negatedQuery.toAQLString() + " )";
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
        if(this.negatedQuery.getFocusID().equals(child)) {
            this.negatedQuery = newChild;
            this.negatedQuery.setParent(getFocusID());
        } else {
            throw new IllegalArgumentException("The passed argument is not a direct child of this node");
        }
    }

    /**
     * Get the subqueries for this tree. Subqueries are the edges of this node.
     *
     * @return List of subqueries (i.e. sub trees) for this node
     */
    @Override
    public List<AQLTree> getSubqueries() {
        return Collections.singletonList(negatedQuery);
    }

    public AQLTree getNegatedQuery() {
        return negatedQuery;
    }
}
