package org.uu.nl.goldenagents.aql.complex;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.VariableController;

public class Union extends BinaryAQLInfixOperator {

    private static final String AQL_LABEL = "or";

    public Union(AQLTree leftChild, AQLTree rightChild) {
        super(leftChild, rightChild);
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
        checkIfFocus(var, controller);
        Op left = leftChild.toARQ(var, controller);
        Op right = rightChild.toARQ(var, controller);
        return new OpUnion(left, right);
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
}
