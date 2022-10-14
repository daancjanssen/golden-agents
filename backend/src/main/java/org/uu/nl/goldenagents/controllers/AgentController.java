package org.uu.nl.goldenagents.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.uu.nl.goldenagents.exceptions.AgentNotFoundException;
import org.uu.nl.goldenagents.exceptions.InvalidIdException;
import org.uu.nl.goldenagents.netmodels.angular.CrudAgent;
import org.uu.nl.goldenagents.netmodels.angular.SparqlResult;
import org.uu.nl.goldenagents.services.AgentService;
import org.uu.nl.goldenagents.services.DfService;
import org.uu.nl.net2apl.core.agent.AgentCreationFailedException;
import org.uu.nl.net2apl.core.platform.Platform;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/agent")
public class AgentController  {
	
	private final AgentService agentService;
	private final DfService dfService;

	@Autowired
	public AgentController(AgentService agentService, DfService dfService) {
		this.agentService = agentService;
		this.dfService = dfService;
	}

	@GetMapping("")
	public CrudAgent index(@RequestParam(value = "agentID", required = true) String agentIDString) throws AgentNotFoundException, InvalidIdException {
		try {
			UUID agentID = UUID.fromString(agentIDString);
			return agentService.get(agentID);
		} catch (IllegalArgumentException e) {
			throw new InvalidIdException();
		}
	}
	
	@PostMapping("update")
	@ResponseBody
	public CrudAgent update(@RequestBody CrudAgent request) throws AgentCreationFailedException, URISyntaxException {
		return agentService.update(request);
	}
	
	@GetMapping(value = "list")
	@ResponseBody
	public CrudAgent[] listAgents() {
		return agentService.getAll();
	}
	
	@PostMapping("kill")
	@ResponseBody
	public CrudAgent kill(@RequestBody CrudAgent request) throws URISyntaxException {
		agentService.delete(request);
		return request;
	}
	
	@GetMapping("types")
	public List<String> listAgentTypes() {
		return agentService.types();
	}

	@GetMapping("df/{agentID}/subscriptions")
	public Map<String, List<String>> dfSubList(@PathVariable("agentID") String agentID ) throws URISyntaxException {
		// TODO: re-implement for DF info page
		return dfService.getServiceMap(agentID);
	}

	// TODO move to user agent controller & service
	@PostMapping("results")
	@ResponseBody
	@Deprecated
	// For real, what does this one do???
	public SparqlResult getResults(@RequestBody SparqlResult request) throws URISyntaxException {
		Platform.getLogger().log(getClass(), "Method getResults marked as deprecated but still used. Where?");
		return agentService.getResults(request);
	}

}
