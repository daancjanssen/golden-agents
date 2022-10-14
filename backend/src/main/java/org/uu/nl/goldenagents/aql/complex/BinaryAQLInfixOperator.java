package org.uu.nl.goldenagents.aql.complex;

import org.uu.nl.goldenagents.aql.AQLTree;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public abstract class BinaryAQLInfixOperator extends AQLTree {

    private static final int N_SUB_TREES = 2;

    AQLTree leftChild;
    AQLTree rightChild;

    BinaryAQLInfixOperator(AQLTree leftChild, AQLTree rightChild) {
        this.leftChild = leftChild;
        this.rightChild = rightChild;
        this.leftChild.setParent(getFocusID());
        this.rightChild.setParent(getFocusID());
    }

    @Override
    public int nSubtrees() {
        return N_SUB_TREES;
    }

    @Override
    public String toAQLString() {
        String b = insertBrackets(leftChild) +
                getAQLLabel() +
                insertBrackets(rightChild);

        return b;
    }

    @Override
    public List<AQLTree> getSubqueries() {
        return Arrays.asList(leftChild, rightChild);
    }

    public AQLTree getLeftChild() {
        return this.leftChild;
    }

    public AQLTree getRightChild() {
        return this.rightChild;
    }

    /**
     * Translate a subquery to an AQL string and add brackets if the subquery itself is also a complex query
     * @param t     The sub tree to translate and add brackets to
     * @return      String, may or may not include brackets.
     */
    private String insertBrackets(AQLTree t) {
        return String.format(t.nSubtrees() > 1 ? " (%s) " : " %s ", t.toAQLString());
    }

    /**
     * Replace a child of this node with a new sub tree
     *
     * @param child    Child to be replaced
     * @param newChild New sub tree
     */
    @Override
    public void replaceChild(UUID child, AQLTree newChild) throws IllegalArgumentException {
        if(this.leftChild.getFocusID().equals(child)) {
            this.leftChild = newChild;
            this.leftChild.setParent(getFocusID());
        } else if (this.rightChild.getFocusID().equals(child)) {
            this.rightChild = newChild;
            this.rightChild.setParent(getFocusID());
        } else {
            throw new IllegalArgumentException("Child to be replaced does not exist on this node");
        }
    }
}
