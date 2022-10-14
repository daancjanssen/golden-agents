package org.uu.nl.goldenagents.aql;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;

import java.util.List;
import java.util.UUID;

/**
 * Node for an Agent Query Language (AQL) tree. The AQL query is built up of these nodes
 */
public abstract class AQLTree implements FIPASendableObject {

    private final UUID id = UUID.randomUUID();

    private UUID parent;

    public UUID getParentID() {
        return this.parent;
    }

    public TYPE type = TYPE.UNMARKED;

    public void setParent(UUID parent) {
        this.parent = parent;
        if(this.parent == this.id) try {
            throw new Exception("Setting this random ID as parent of itself!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * AQL label representing this node in the AQL query
     * @return  String
     */
    public abstract String getAQLLabel();

    /**
     * Construct Sparql Algebra for this query (sub) tree recursively
     * @param var   An ARQ variable for this (sub) tree
     * @return      Jena Sparql Algebra Op object
     */
    public abstract Op toARQ(Var var, VariableController controller);

    /**
     * Convert this query to an AQL string recursively
     * @return An AQL query string
     */
    public abstract String toAQLString();

    /**
     * Convert this tree to a natural language representation
     * @return NL representation of this query
     */
    public abstract String toNLQuery();

    /**
     * Number of sub trees of this node type
     * @return  Integer indicating number of sub trees for this node type
     */
    public abstract int nSubtrees();

    /**
     * Replace a child of this node with a new sub tree
     * @param childFocusID         FocusID of child to be replaced
     * @param newChild      New sub tree
     */
    public abstract void replaceChild(UUID childFocusID, AQLTree newChild) throws IllegalArgumentException;

    /**
     * Get the subqueries for this tree. Subqueries are the edges of this node.
     * @return List of subqueries (i.e. sub trees) for this node
     */
    public abstract List<AQLTree> getSubqueries();

    public UUID getFocusID() {
        return this.id;
    }

    /**
     * Recursively try to find the first resource label that could describe a variable
     * @return
     */
    public String getFirstResourceLabel() {
        for(AQLTree subquery : this.getSubqueries()) {
             String label = subquery.getFirstResourceLabel();
             if(label != null) return label;
        }
        return null;
    }

    public enum TYPE {

        CLASS("class"),
        PROPERTY("prop"),
        MODIFIER("modifier"),
        UNMARKED("");

        private String label;

        TYPE(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    protected void checkIfFocus(Var var, VariableController controller) {
        if(this.id.equals(controller.getQueryFocusID())) {
            controller.setFocusVariable(var);
        }
    }
}
