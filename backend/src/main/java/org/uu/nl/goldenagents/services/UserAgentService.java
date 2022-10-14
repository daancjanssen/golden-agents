package org.uu.nl.goldenagents.services;

import ch.rasc.sse.eventbus.SseEventBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.uu.nl.goldenagents.agent.context.PrefixNSListenerContext;
import org.uu.nl.goldenagents.agent.context.query.AQLQueryContext;
import org.uu.nl.goldenagents.agent.context.query.QueryResultContext;
import org.uu.nl.goldenagents.agent.trigger.user.AQLQueryChangedExternalTrigger;
import org.uu.nl.goldenagents.aql.AQLQuery;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.SPARQLTranslation;
import org.uu.nl.goldenagents.exceptions.AgentNotFoundException;
import org.uu.nl.goldenagents.netmodels.angular.*;
import org.uu.nl.goldenagents.netmodels.datatables.DataTableRequest;
import org.uu.nl.goldenagents.netmodels.datatables.DataTableResult;
import org.uu.nl.goldenagents.netmodels.fipa.QueryResult;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.goldenagents.util.TomlConfigurationParser;
import org.uu.nl.goldenagents.util.agentconfiguration.UiAgentConfig;
import org.uu.nl.goldenagents.util.agentconfiguration.UserAgentConfig;
import org.uu.nl.net2apl.core.agent.Agent;
import org.uu.nl.net2apl.core.agent.AgentArguments;
import org.uu.nl.net2apl.core.agent.AgentCreationFailedException;
import org.uu.nl.net2apl.core.platform.Platform;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;

@Service
public class UserAgentService {

    private final Platform platform;
    private final SseEventBus serverEventBus;

    @Autowired
    public UserAgentService(Platform platform, SseEventBus serverEventBus) {
        this.platform = platform;
        this.serverEventBus = serverEventBus;
    }

    public UserQueryTrigger initiateQuery(UUID agentID, UserQueryTrigger request) throws AgentNotFoundException{
        Agent agent = getUserAgentFromUUID(agentID);
        agent.addExternalTrigger(request);
        return request;
    }

    public CrudAgent getUserAgent(@Nullable UUID agentID) throws AgentCreationFailedException {
        if (agentID != null) {
            Agent agent = this.platform.getLocalAgent(agentID);
            if (agent != null) {
                Platform.getLogger().log(this.getClass(), String.format(
                        "Found previously used user agent with ID %s",
                        agentID
                ));
                return new CrudAgent(agent, true);
            } else {
                Platform.getLogger().log(this.getClass(), String.format(
                        "Agent with UUID %s no longer exists",
                        agentID
                ));
            }
        } else {
            Platform.getLogger().log(this.getClass(), Level.INFO, "User requested a user agent but did not previously use one");
        }

        // If we reach here, either the agentID was null, or no corresponding agent exists on the platform.
        // Let's not disappoint the user and create one!
        Platform.getLogger().log(getClass(), "Creating a new User agent");

        AgentArguments arguments = new AgentArguments();
        UiAgentConfig uiAgentConfig = new UiAgentConfig("User", "person", CrudAgent.AgentType.USER, null, null);
        uiAgentConfig.addConfigurationToArguments(arguments);

        new UserAgentConfig(null).addConfigurationToArguments(arguments);

        Agent agent = TomlConfigurationParser.createAgent(platform, arguments, "User", false, serverEventBus);

        return new CrudAgent(agent, true);
    }

    /*
            QUERY HISTORY
     */

    public String getLastQueryID(UUID agentID) {
        QueryResultContext context = getQueryResultContextForAgentID(agentID);
        if(context == null) return null;

        return context.getLastFinishedQueryString();
    }

    public String getLastQuery(UUID agentID) {
        QueryResultContext context = getQueryResultContextForAgentID(agentID);
        if(context == null) return null;

        return context.getLastQueryString();
    }

    public String getQueryForQueryID(UUID agentID, String queryID) {
        QueryResultContext context = getQueryResultContextForAgentID(agentID);
        if(context == null) return null;

        return context.getQueryString(queryID);
    }

    /*
            QUERY RESULTS
    */

    public ByteArrayOutputStream getResutlsAsCSV(UUID agentID, String queryID) {
        QueryResultContext context = getQueryResultContextForAgentID(agentID);
        if(context == null) return null;

        return context.getCSVStreamForQuery(queryID);
    }

    public ByteArrayOutputStream getResutlsAsXML(UUID agentID, String queryID) {
        QueryResultContext context = getQueryResultContextForAgentID(agentID);
        if(context == null) return null;

        return context.getXMLStreamForQuery(queryID);
    }

    public DataTableResult getPaginatedQueryResults(HttpServletRequest request) {
        DataTableRequest dtRequest = new DataTableRequest(request);

        Agent agent = getUserAgentFromUUID(dtRequest.getAgentUUID());
        QueryResultContext context = getQueryResultContextForAgentID(dtRequest.getAgentUUID());
        if(context == null) throw new IllegalArgumentException();

        QueryResult queryResult = context.getResult(dtRequest.getQueryID());
        if(queryResult == null) throw new IllegalArgumentException();

        return queryResult.getResults(dtRequest);
    }

    public Collection<CachedQueryInfo> queryHistory(UUID agentID) {
        QueryResultContext context = getQueryResultContextForAgentID(agentID);
        return context.queryHistory();
    }

    public AQLQueryObject getCurrentQuery(UUID agentID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        if(context.getCurrentQueryIndex() < 0) {
            // TODO, ideally only use the prefixes of the broker agent that will answer this query?
            AQLQuery newQuery = context.createQuery(getPrefixContextForAgentID(agentID).getPrefixMap());
            notifyAQLQueryChanged(agentID, newQuery);
        }
        return context.serializeCurrentQuery();
    }

    public AQLSuggestions getSuggestions(UUID agentID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        return context.getCurrentQuery().getSuggestions();
    }

    public String getSparqlTranslation(UUID agentID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
//        Op algebra = context.getCurrentQuery().getSparqlAlgebra();
//
//        Op op = Algebra.optimize(algebra);
//        Query q = OpAsQuery.asQuery(op);
//        PrefixMapping mapping = context.getCurrentQuery().getPrefixMapping();
//        q.setPrefixMapping(mapping);
//
//        return q.toString(Syntax.syntaxSPARQL_11);
        SPARQLTranslation translation = context.getCurrentQuery().getSparqlAlgebra();
        return translation.getQueryString();
    }

    public AQLQueryObject intersect(UUID agentID, AQLTree transformation) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().intersection(transformation);
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    public AQLQueryObject cross(UUID agentID, AQLResource property, boolean crossForward) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().cross(property, crossForward);
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    public AQLQueryObject exclude(UUID agentID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().negativeLookup();
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    public AQLQueryObject union(UUID agentID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().union();
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    public AQLQueryObject changeFocus(UUID agentID, UUID focusID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().setFocus(focusID);
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    public AQLQueryObject deleteCurrentFocus(UUID agentID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().delete();
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    public AQLQueryObject deleteQueryFocus(UUID agentID, UUID focusID) {
        AQLQueryContext context = getAQLQueryContextForAgentID(agentID);
        context.getCurrentQuery().delete(focusID);
        notifyAQLQueryChanged(agentID, context.getCurrentQuery());
        return context.serializeCurrentQuery();
    }

    /*
            HELPERS
     */
    private Agent getUserAgentFromUUID(UUID agentID) throws AgentNotFoundException {
        Agent agent = platform.getLocalAgent(agentID);
        if(agent == null) throw new AgentNotFoundException("This user agent could not be found on this platform");
        return agent;
    }

    private QueryResultContext getQueryResultContextForAgentID(UUID agentID) {
        Agent agent = getUserAgentFromUUID(agentID);
        return agent.getContext(QueryResultContext.class);
    }

    private AQLQueryContext getAQLQueryContextForAgentID(UUID agentID) {
        Agent agent = getUserAgentFromUUID(agentID);
        return agent.getContext(AQLQueryContext.class);
    }

    private PrefixNSListenerContext getPrefixContextForAgentID(UUID agentID) {
        Agent agent = getUserAgentFromUUID(agentID);
        return agent.getContext(PrefixNSListenerContext.class);
    }

    private void notifyAQLQueryChanged(UUID agentID, AQLQuery query) {
        platform.getLocalAgent(agentID).addExternalTrigger(
                new AQLQueryChangedExternalTrigger(query)
        );
    }

    // TODO create methods to intersect with classes, and to apply forward and backward crossing
}
