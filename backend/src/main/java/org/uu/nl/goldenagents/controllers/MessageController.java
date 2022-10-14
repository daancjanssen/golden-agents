package org.uu.nl.goldenagents.controllers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.uu.nl.goldenagents.exceptions.AgentNotFoundException;
import org.uu.nl.goldenagents.exceptions.InvalidIdException;
import org.uu.nl.goldenagents.netmodels.angular.CrudMessage;
import org.uu.nl.goldenagents.services.MessageService;
import org.uu.nl.goldenagents.services.UserAgentService;
import org.uu.nl.net2apl.core.defaults.messenger.MessageReceiverNotFoundException;
import org.uu.nl.net2apl.core.platform.PlatformNotFoundException;

@RestController
@RequestMapping("api/message")
public class MessageController {
	
	private final MessageService service;
	private final UserAgentService userAgentService;

	@Autowired
	public MessageController(MessageService service, UserAgentService userAgentService) {
		this.service = service;
		this.userAgentService = userAgentService;
	}

	@GetMapping("agenthistory")
	public List<CrudMessage> agentMessageHistory(@RequestParam(value = "agentID", required = true) String agentIDString) throws AgentNotFoundException {
		try {
			UUID agentID = UUID.fromString(agentIDString);
			return service.agentHistory(agentID);
		} catch (IllegalArgumentException e) {
			throw new InvalidIdException();
		}
	}
	
	@GetMapping("performatives")
	public Map<String, Integer> getPerformatives() {
		return service.performatives();
	}
	
	@PostMapping("send")
	@Deprecated // TODO Really should not use this to communicatie with a multi-agent system. Especially if exposed over internet
	public CrudMessage send(@RequestBody CrudMessage msg) throws MessageReceiverNotFoundException, PlatformNotFoundException, URISyntaxException, IOException {
		return service.sendMessage(msg);
	}
}
