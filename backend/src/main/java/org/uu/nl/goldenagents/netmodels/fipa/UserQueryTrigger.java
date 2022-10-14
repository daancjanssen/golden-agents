package org.uu.nl.goldenagents.netmodels.fipa;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.jena.query.Query;
import org.apache.jena.query.Syntax;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.core.Var;
import org.springframework.util.DigestUtils;
import org.uu.nl.goldenagents.aql.AQLQuery;
import org.uu.nl.goldenagents.aql.SPARQLTranslation;
import org.uu.nl.net2apl.core.agent.Trigger;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.fipa.acl.UnreadableException;
import org.uu.nl.net2apl.core.platform.Platform;

public class UserQueryTrigger implements FIPASendableObject, Trigger {

	private static final long serialVersionUID = 1L;
	
	private String query;
    private GAMessageHeader queryType;
    private String[] selectedSources;
    private final String queryID;

    private AQLQuery aql;
    private SPARQLTranslation sparqlTranslation;

    public UserQueryTrigger(
            @JsonProperty("query") String query,
            @JsonProperty("queryType") GAMessageHeader queryType,
            @JsonProperty("selectedSources") String[] selectedSources)
    {
        this.query = query;
        this.queryType = queryType;
        this.selectedSources = selectedSources;
        this.queryID = this.generateQueryID();
    }

    public UserQueryTrigger(AQLQuery query, GAMessageHeader queryType) {
        this.aql = query;
        this.sparqlTranslation = query.getSparqlAlgebra();
        this.query = this.sparqlTranslation.getQueryString();
        this.queryID = this.generateQueryID();
        this.selectedSources = new String[0];
        this.queryType = queryType;
    }

    public String getQuery() {
        return this.query;
    }

    public GAMessageHeader getQueryType() {
        return this.queryType;
    }

    public String getQueryID() {
        return this.queryID;
    }

	public String[] getSelectedSources() {
		return selectedSources;
	}

    /**
     * Generate a unique ID for this query. This ID cannot be guaranteed to be deterministic for the same query string,
     * so do not regenerate after the ID has been set.
     */
    private String generateQueryID() {
        String toHash = this.query + System.currentTimeMillis();
        return DigestUtils.md5DigestAsHex(toHash.getBytes());
    }

    public static UserQueryTrigger fromACLMessage(ACLMessage messageWithQueryTrigger) {
        try {
            return (UserQueryTrigger) ((GAMessageContentWrapper) messageWithQueryTrigger.getContentObject()).getContent();
        } catch (UnreadableException e) {
            Platform.getLogger().log(UserQueryTrigger.class, e);
            return null;
        }
    }

    public AQLQuery getAql() {
        return aql;
    }

    public void setAql(AQLQuery aql) {
        this.aql = aql;
    }

    public SPARQLTranslation getSparqlTranslation() {
        return sparqlTranslation;
    }

    public void setSparqlTranslation(SPARQLTranslation sparqlTranslation) {
        this.sparqlTranslation = sparqlTranslation;
    }
}
