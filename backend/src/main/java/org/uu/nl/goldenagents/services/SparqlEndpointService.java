package org.uu.nl.goldenagents.services;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.apache.jena.sparql.util.TranslationTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.uu.nl.goldenagents.agent.context.BrokerContext;
import org.uu.nl.goldenagents.agent.context.DirectSsePublisher;
import org.uu.nl.goldenagents.agent.context.UIContext;
import org.uu.nl.goldenagents.agent.context.query.QueryProgressType;
import org.uu.nl.goldenagents.exceptions.AgentNotFoundException;
import org.uu.nl.goldenagents.netmodels.angular.AQLSuggestions;
import org.uu.nl.goldenagents.netmodels.angular.CrudAgent;
import org.uu.nl.goldenagents.netmodels.angular.CrudMessage;
import org.uu.nl.goldenagents.netmodels.angular.QueryProgress;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageContentWrapper;
import org.uu.nl.goldenagents.netmodels.fipa.GAMessageHeader;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.net2apl.core.agent.Agent;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.fipa.acl.ACLMessage;
import org.uu.nl.net2apl.core.fipa.acl.Performative;
import org.uu.nl.net2apl.core.fipa.mts.Envelope;
import org.uu.nl.net2apl.core.platform.Platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@Service
public class SparqlEndpointService {
    // https://www.w3.org/TR/sparql11-protocol/#query-via-post-direct

    private final Platform platform;
    private final Map<String, ResultsFormat> availableFormats = getAvailableFormats();

    @Autowired
    public SparqlEndpointService(Platform platform) {
        this.platform = platform;
    }

    /**
     * 400 if the SPARQL query supplied in the request is not a legal sequence of characters in the language defined by the SPARQL grammar; or,
     * 500 if the service fails to execute the query. SPARQL Protocol services may also return a 500 response code if they refuse to execute a query.
     *      This response does not indicate whether the server may or may not process a subsequent, identical request or requests.
     */
    public ResponseEntity<String> evaluateSparqlQuery(
            String sparqlQueryString,
            String defaultGraphUri,
            String namedGraphUri,
            boolean useExpertiseSearch,
            String format
    ) throws IOException
    {
        // Pre-processing. IF this fails, nothing will work with the current query request
        Agent broker = getRandomBrokerAgent();
        ResultsFormat resultsFormat;
        try {
            resultsFormat = parseResultsFormat(format);
        } catch (IllegalArgumentException e) {
            return handleBadQuery(e.getMessage());
        }

        // The query trigger is used to inform the broker of a query request.
        // If "useExpertiseSearch" is selected, the best data source for each triple will be selected,
        // otherwise, each data source agent receives a conjunctive query containing all triples in the original query
        // that it knows.
        UserQueryTrigger trigger = new UserQueryTrigger(
                sparqlQueryString,
                useExpertiseSearch ? GAMessageHeader.USER_INTELLIGENT_SEARCH : GAMessageHeader.USER_QUERY,
                getDataSourceArray() // TODO, should this be an option in the query parameters?
        );

        // We try to send the trigger to the broker using a message, and register a listener for when the broker
        // signals it is ready
        ACLMessage messageToBroker = createAclMessageForQuery(broker, trigger);
        QueryFinishedListener listener = new QueryFinishedListener(trigger.getQueryID());

        try {
            //noinspection unchecked
            platform.getMessenger().deliverMessage(broker.getAID(), messageToBroker);
        } catch (Exception e) {
            listener.cancel();
            // Error automatically resolves with status 500
            throw new AgentNotFoundException("Communication with broker who is supposed to handle this query failed");
        }

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.initialize();

        // Using a task executor to wait for the process to be finished
        try {
            taskExecutor.submit(listener).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            listener.cancel();
            return handleBadQuery(e.getCause().getMessage());
        }

        // Set the response encoding to the requested encoding
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(HttpHeaders.CONTENT_ENCODING, resultsFormat.getSymbol());

        // Try to parse the query results now that the broker is done, or fail with an exception, that will result
        // in a 500 status code, as per SPARQL specification
        return new ResponseEntity<>(
                getResults(sparqlQueryString, broker, messageToBroker.getConversationId(), resultsFormat).toString(),
                responseHeaders,
                HttpStatus.OK
        );
    }

    /**
     * Returns with an error message and a 400 status code
     * @param cause Error message causing this request to fail
     * @return  ResponseEntity with error message and 400 status code
     */
    private ResponseEntity<String> handleBadQuery(String cause) {
        return new ResponseEntity<>(cause, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tries to map the requested format to a Apache Jena ResultsFormat object. If no mapping can be made, throws
     * an illegal argument exception
     * @param format            The requested format passed as a URL query parameter
     * @return                  The ResultsFormat object corresponding to the passed format string(s)
     * @throws IllegalArgumentException If the passed format string(s) cannot be mapped to a ResultsFormat object
     */
    private ResultsFormat parseResultsFormat(String format) throws IllegalArgumentException {
        ResultsFormat resultsFormat = availableFormats.get(format);
        if (resultsFormat == null) {
            throw new IllegalArgumentException(String.format(
                    "Requested format '%s' not known. Available formats are:\n\t%s",
                    format,
                    String.join("\n\t", availableFormats.keySet())
            ));
        }

        return resultsFormat;
    }

    /**
     * When the broker indicates the query execution has finished, in reality they have just collected all the relevant
     * results from the data source agents, and aggregated them in a Jena model.
     *
     * This method evaluates the original query on that cached model, and returns a byte stream in the requested format
     *
     * @param queryString       Query string to evaluate
     * @param broker            Broker who handled the query request
     * @param conversationID    Conversation ID used in the query request
     * @param resultsFormat     Output format of the query result
     * @return                  Byte stream with query results in requested format
     *
     * @throws IOException      If something went wrong in collecting the results from the cached model
     */
    private ByteArrayOutputStream getResults(String queryString, Agent broker, String conversationID, ResultsFormat resultsFormat) throws IOException {
        BrokerContext context = broker.getContext(BrokerContext.class);
        InfModel model = context.getCachedModel(conversationID).getCachedModel();
        model.enterCriticalSection(Lock.READ);

        try(QueryExecution exec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = exec.execSelect();
            try(ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                ResultSetFormatter.output(byteArrayOutputStream, results, resultsFormat);
                return byteArrayOutputStream;
            }
        } finally {
            model.leaveCriticalSection();
        }
    }

    /**
     * Gets a random broker agent from the platform to handle the query request.
     * With the current version of the system, multiple brokers can exist, but they would be identical in
     * functionality, so what broker agent is selected does not matter.
     *
     * Usually, the system is instantiated with one broker agent. At least one broker agent is required.
     *
     * @return  A broker agent that can handle the query request
     * @throws AgentNotFoundException If no broker agent exists on the platform yet
     */
    private Agent getRandomBrokerAgent() throws AgentNotFoundException {
        List<Agent> brokers = getAgentsOfType(CrudAgent.AgentType.BROKER);
        if (brokers.isEmpty()) {
            throw new AgentNotFoundException("No Broker agent found to handle this query");
        }

        return brokers.get(0);
    }

    /**
     * Finds all data source agents, and constructs an array of identifiers for those agents that can be interpreted
     * by the broker agent.
     * Each of the agents in that array is considered as a possible contributor to the query.
     *
     * @return  String of agent identifiers that can be interpreted by the broker agent
     */
    private String[] getDataSourceArray() {
        List<Agent> dataSourceAgents = getAgentsOfType(CrudAgent.AgentType.DB);
        return dataSourceAgents.stream().map(agent -> agent.getAID().getName().getUserInfo()).toArray(String[]::new);
    }

    private List<Agent> getAgentsOfType(CrudAgent.AgentType type) throws AgentNotFoundException {
        List<AgentID> localAgents = platform.getLocalAgentsList();
        List<Agent> agentsOfType = new ArrayList<>();
        for(AgentID agentID : localAgents) {
            try {
                Agent agent = platform.getLocalAgent(agentID);
                UIContext context = agent.getContext(UIContext.class);
                if (context != null && context.getType().equals(type)) {
                    agentsOfType.add(agent);
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return agentsOfType;
    }

    /**
     * Create the message to send to the broker to start the query request
     * @param broker    Broker agent to send message to
     * @param trigger   Trigger containing all query details
     * @return          ACLMessage that can be sent to broker to start query process
     */
    private ACLMessage createAclMessageForQuery(Agent broker, UserQueryTrigger trigger) throws IOException {
        Envelope envelope = new Envelope();
        envelope.setFrom(broker.getAID());
        envelope.addTo(broker.getAID());
        envelope.addIntendedReceiver(broker.getAID());

        GAMessageContentWrapper wrapper = new GAMessageContentWrapper(
                trigger.getQueryType(), trigger);

        ACLMessage message = new ACLMessage(Performative.QUERY_REF);
        message.setEnvelope(envelope);
        message.setContentObject(wrapper);

        message.addReceiver(broker.getAID());
        message.addReplyTo(broker.getAID());
        message.setSender(broker.getAID());
        message.addUserDefinedParameter("X-messageID", message.getConversationId());

        return message;
    }

    /**
     * Class that waits for the broker to signal the querying process to be finished, or that throws an error if
     * something went wrong (e.g., incorrect query syntax)
     */
    static class QueryFinishedListener implements DirectSsePublisher.SSeListener, Callable<Void> {

        String queryID;

        private volatile boolean finished = false;
        private Throwable cause = null;

        QueryFinishedListener(String queryID) {
            this.queryID = queryID;
            DirectSsePublisher.addListener(this);
        }

        @Override
        public void onQueryProgress(QueryProgress<?> progress) {
            if (progress.getQueryID().equals(queryID) && QueryProgressType.RESULTS_RETURNED.name().equals(progress.getType())) {
                finished = true;
            }
        }

        @Override
        public void onCrudMessage(String involvedAgentUUID, CrudMessage message) { }

        @Override
        public void onStateReady(AgentID agentID) { }

        @Override
        public void onSuggestionsReady(AgentID agentID, AQLSuggestions suggestions) { }

        @Override
        public void onQueryFailed(String queryID, Throwable cause) {
            if(queryID.equals(this.queryID)) {
                this.cause = cause;
                finished = true;
            }
        }

        public void cancel() {
            DirectSsePublisher.removeListener(this);
        }

        @Override
        public Void call() throws Exception {
            while (!finished) {
                Thread.onSpinWait();
            }
            cancel();
            if (cause != null) {
                throw new ExecutionException(cause);
            }
            return null;
        }
    }

    /**
     * Uses reflection to get the identifiers associated with the various ResultsFormats that Jena uses to
     * format query results. These are the presentation styles we can use as well
     *
     * @return Map
     */
    private Map<String, ResultsFormat> getAvailableFormats() {
        Map<String, ResultsFormat> formats = new HashMap<>();
        Field[] fields = ResultsFormat.class.getDeclaredFields();
        for(Field field : fields) {
            if (field.getType().equals(ResultsFormat.class)) {
                try {
                    ResultsFormat format = (ResultsFormat) field.get(null);
                    String symbol = format.getSymbol();
                    if (!symbol.equals("none") && !symbol.equals("unknown")) {
                        formats.put(symbol, format);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            Field namesField = ResultsFormat.class.getDeclaredField("names");
            namesField.setAccessible(true);
            TranslationTable<ResultsFormat> translationTable = (TranslationTable<ResultsFormat>) namesField.get(null);
            for (Iterator<String> it = translationTable.keys(); it.hasNext(); ) {
                String key = it.next();
                formats.put(key, translationTable.lookup(key));
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        return formats;
    }

}
