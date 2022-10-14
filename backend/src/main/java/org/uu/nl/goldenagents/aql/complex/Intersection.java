package org.uu.nl.goldenagents.aql.complex;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.MostGeneralQuery;
import org.uu.nl.goldenagents.aql.VariableController;

public class Intersection extends BinaryAQLInfixOperator {

    private static final String AQL_LABEL = "and";

    public Intersection(AQLTree leftChild, AQLTree rightChild) {
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
//        return leftChild instanceof Intersection || rightChild instanceof Intersection ?
//                "" : AQL_LABEL;
    }

    @Override
    public String toAQLString() {
        // Avoid showing all the redundant question marks
        if(leftChild instanceof MostGeneralQuery) {
            return rightChild.toAQLString();
        } else if (rightChild instanceof MostGeneralQuery) {
            return leftChild.toAQLString();
        } else {
            return super.toAQLString();
        }
    }

    public Op toARQ(Var var, VariableController controller) {
        checkIfFocus(var, controller);
        Op left = leftChild.toARQ(var, controller);
        Op right = rightChild.toARQ(var, controller);
        return OpJoin.createReduce(left, right);
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
