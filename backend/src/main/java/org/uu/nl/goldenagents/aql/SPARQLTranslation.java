package org.uu.nl.goldenagents.aql;

import org.apache.jena.query.Query;
import org.apache.jena.query.Syntax;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.core.Var;
import org.uu.nl.net2apl.core.platform.Platform;

import java.io.Serializable;
import java.util.logging.Level;

public class SPARQLTranslation implements Serializable {

    private AQLQuery query;
    private transient Op sparqlTranslation;
    private Var focusVar;

    public SPARQLTranslation(AQLQuery query) {
        this.query = query;
        VariableController controller = new VariableController();
        controller.setQueryFocusID(query.getFocusName());
        String firstLabel = query.getQueryTree().getFirstResourceLabel();
        Var firstVar = firstLabel == null ? controller.getVariable() : controller.getVariableForLabel(firstLabel);
        Op algebra = this.query.getQueryTree().toARQ(firstVar, controller);
        this.focusVar = controller.getFocusVariable();

        Platform.getLogger().log(getClass(), Level.SEVERE, "Focus variable is now " + this.focusVar.getVarName());

        this.sparqlTranslation = Algebra.optimize(algebra);
    }

    public Query getQuery() {
        Query q = OpAsQuery.asQuery(this.sparqlTranslation);
        PrefixMapping mapping = this.query.getPrefixMapping();
        q.setPrefixMapping(mapping);
        return q;
    }

    public String getQueryString() {
        return this.getQuery().toString(Syntax.syntaxSPARQL_11);
    }


    public Op getSparqlTranslation() {
        return sparqlTranslation;
    }

    public Var getFocusVar() {
        return focusVar;
    }
}
