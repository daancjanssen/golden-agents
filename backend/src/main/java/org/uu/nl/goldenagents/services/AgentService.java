package org.uu.nl.goldenagents.services;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import org.apache.jena.atlas.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.uu.nl.goldenagents.agent.context.query.QueryResultContext;
import org.uu.nl.goldenagents.exceptions.AgentNotFoundException;
import org.uu.nl.goldenagents.netmodels.angular.CrudAgent;
import org.uu.nl.goldenagents.netmodels.angular.CrudAgent.AgentType;
import org.uu.nl.goldenagents.netmodels.angular.SparqlResult;
import org.uu.nl.goldenagents.netmodels.fipa.QueryResult;
import org.uu.nl.net2apl.core.agent.Agent;
import org.uu.nl.net2apl.core.agent.AgentCreationFailedException;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.platform.Platform;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class AgentService {

	private final Platform platform;
	private final SseEventBus serverEventBus;

	@Autowired
	public AgentService(Platform platform, SseEventBus serverEventBus) {
		this.platform = platform;
		this.serverEventBus = serverEventBus;
	}

	public CrudAgent get(UUID id) throws AgentNotFoundException {
		Agent agent = platform.getLocalAgent(id);
		if( agent == null) throw new AgentNotFoundException("Agent not found.");
		return new CrudAgent(agent, true);
	}

	public CrudAgent[] getAll() {
		return platform.getLocalAgentsList()
				.stream()
				.map(id -> {
					try {
						return new CrudAgent(platform.getLocalAgent(id));
					} catch (URISyntaxException e) {
						e.printStackTrace();
						return null;
					}
				})
				.toArray(CrudAgent[]::new);
	}
	
	public void delete(CrudAgent agent) throws URISyntaxException {
		platform.killAgent(new AgentID(agent.createURI()));
		Platform.getLogger().log(AgentService.class, Level.INFO, "Deleting agent " + agent);
		serverEventBus.handleEvent(SseEvent.of("agent_delete", agent));
	}

	public CrudAgent update(CrudAgent template) throws URISyntaxException {
		platform.updateNickName(new AgentID(template.createURI()));
		Platform.getLogger().log(AgentService.class, Level.INFO, "Updating " + template);
		serverEventBus.handleEvent(SseEvent.of("agent_update", template));
		return template;
	}
	
	public List<String> types() {
		return Arrays.stream(AgentType.values()).map(AgentType::getType).collect(Collectors.toList());
	}

	@Deprecated
	public SparqlResult getResults(SparqlResult template) throws URISyntaxException {
		UUID agentID = UUID.fromString(template.getUuid());
		Agent agent = platform.getLocalAgent(agentID);
		QueryResultContext results = agent.getContext(QueryResultContext.class);
		QueryResult qResult = results.getResult(template.getConversationId());
		template.setResults(qResult.getResultsAsString());
		return template; 
	}

}
