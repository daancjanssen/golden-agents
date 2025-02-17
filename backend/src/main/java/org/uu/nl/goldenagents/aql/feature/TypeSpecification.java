package org.uu.nl.goldenagents.aql.feature;

import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.RDF;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.VariableController;
import org.uu.nl.goldenagents.netmodels.jena.SerializableResourceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TypeSpecification extends hasResource {

    public TypeSpecification(SerializableResourceImpl resource) {
        super(resource);
        this.label = resource.getLocalName();
        this.type = TYPE.CLASS;
    }

    public TypeSpecification(SerializableResourceImpl resource, String label) {
        super(resource, label);
        this.type = TYPE.CLASS;
    }

    private TypeSpecification(SerializableResourceImpl resource, String label, ID focusName, ID parent) {
        super(resource, label, focusName, parent);
        this.type = TYPE.CLASS;
    }

    /**
     * AQL label representing this node in the AQL query
     *
     * @return String
     */
    @Override
    public String getAQLLabel() {
        return super.getAQLLabel();
    }

    public Op toARQ(Var var, VariableController controller) {
        checkIfFocus(var, controller);
        Triple t = new Triple(var, RDF.type.asNode(), this.resource.asNode());
        BasicPattern bp = new BasicPattern();
        bp.add(t);
        return new OpBGP(bp);
    }

    /**
     * Convert this query to an AQL string recursively
     *
     * @return An AQL query string
     */
    @Override
    public String toAQLString() {
        return "a " + this.label;
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
     * Get the subqueries for this tree. Subqueries are the edges of this node.
     *
     * @return List of subqueries (i.e. sub trees) for this node
     */
    @Override
    public List<AQLTree> getSubqueries() {
        return new ArrayList<>();
    }

    @Override
    public AQLTree copy(ID parent, HashMap<ID, AQLTree> foci) {
        TypeSpecification copy = new TypeSpecification(this.resource, this.label, this.getFocusName(), parent);
        foci.put(copy.getFocusName(), copy);
        return copy;
    }

}
