package org.uu.nl.goldenagents.aql.complex;

import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.MostGeneralQuery;
import org.uu.nl.goldenagents.aql.VariableController;
import org.uu.nl.goldenagents.netmodels.jena.SerializableResourceImpl;

/**
 * Backward crossing properties are those properties p(.,S)
 * This means for a feature f, the triple becomes { f p ?value }, or
 *
 * GP(x, p : q1) = ?x p ?y . GP(y, q1)             where y is a fresh variable
 *
 */
public class CrossingBackwards extends CrossingOperator {
    private static final String AQL_LABEL = ":";

    public CrossingBackwards(SerializableResourceImpl resource, AQLTree subquery) {
        super(resource, subquery);
    }

    public CrossingBackwards(SerializableResourceImpl resource, AQLTree subquery, String label) {
        super(resource, subquery, label);
    }

    /**
     * AQL label representing this node in the AQL query
     *
     * @return String
     */
    @Override
    public String getAQLLabel() {
        return this.getSubquery() instanceof MostGeneralQuery ? "that has a " + this.label : "whose " + this.label;
    }

    public Op toARQ(Var var, VariableController controller) {
        checkIfFocus(var, controller);
        Var freshVar = controller.getVariableForLabel(this.resource.getLocalName());
        Triple t = new Triple(var, this.resource.asNode(), freshVar);
        BasicPattern bp = new BasicPattern();
        bp.add(t);
        Op child = this.subquery.toARQ(freshVar, controller);
        return OpJoin.createReduce(new OpBGP(bp), child);
    }

    /**
     * Convert this query to an AQL string recursively
     *
     * @return An AQL query string
     */
    @Override
    public String toAQLString() {
        return null;
    }

    /**
     * Convert this tree to a natural language representation
     *
     * @return NL representation of this query
     */
    @Override
    public String toNLQuery() {
        // TODO
        return "";
    }
}
