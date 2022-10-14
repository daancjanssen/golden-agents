package org.uu.nl.goldenagents.agent.context.query;

import org.uu.nl.goldenagents.aql.AQLQuery;
import org.uu.nl.goldenagents.netmodels.angular.AQLQueryObject;
import org.uu.nl.goldenagents.netmodels.angular.AQLSuggestions;
import org.uu.nl.net2apl.core.agent.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AQLQueryContext implements Context {
    private final List<AQLQuery> queries;
    private int currentQueryIndex;

    public AQLQueryContext() {
        this.queries = new ArrayList<>();
        this.currentQueryIndex = -1;
    }

    public synchronized AQLQuery createQuery(Map<String, String> prefixMap) {
        AQLQuery newQuery = new AQLQuery(prefixMap);
        synchronized (this.queries) {
            this.queries.add(newQuery);
            this.currentQueryIndex = this.queries.lastIndexOf(newQuery);
        }
        return newQuery;
    }

    public synchronized int getCurrentQueryIndex() {
        return this.currentQueryIndex;
    }

    public synchronized boolean changeCurrentQuery(AQLQuery query) {
        synchronized (this.queries) {
            if (this.queries.contains(query)) {
                this.currentQueryIndex = this.queries.indexOf(query);
                return true;
            }
        }
        return false;
    }

    public synchronized AQLQuery getCurrentQuery() {
        synchronized (this.queries) {
            return this.queries.get(this.currentQueryIndex);
        }
    }

    public AQLQuery getQueryForIndex(int index) {
        synchronized (this.queries) {
            if(index < 0 || index > this.queries.size())
                throw new IndexOutOfBoundsException();
            return this.queries.get(index);
        }
    }

    public AQLQueryObject serializeCurrentQuery() {
//        return new AQLQueryObject(getCurrentQuery().getQueryTree(), getCurrentQuery().getFocusName());
        return new AQLQueryObject(getCurrentQuery());
    }

    public AQLQueryObject serializeQuery(int index) {
        AQLQuery q = this.getQueryForIndex(index);
        return new AQLQueryObject(q);
    }

    /**
     * Find the query object for the root node ID
     * @param nodeID
     * @return
     */
    public AQLQuery getQueryObjectForNodeID(UUID nodeID) {
        synchronized (this.queries) {
            if (this.getCurrentQuery().getQueryTree().getFocusID().equals(nodeID)) {
                return this.getCurrentQuery();
            } else {
                for(AQLQuery q : this.queries) {
                    if(q.getQueryTree().getFocusID().equals(nodeID))
                        return q;
                }
            }
        }
        return null;
    }

    public void setSuggestionsForNodeID(AQLSuggestions suggestions) {
        this.getQueryObjectForNodeID(suggestions.getFocusID()).setSuggestions(suggestions);
    }
}
