package org.uu.nl.goldenagents.util;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.tomlj.*;
import org.uu.nl.goldenagents.agent.context.CrudMessagePublisher;
import org.uu.nl.goldenagents.agent.context.DirectSsePublisher;
import org.uu.nl.goldenagents.netmodels.angular.CrudAgent;
import org.uu.nl.goldenagents.services.AgentService;
import org.uu.nl.goldenagents.util.agentconfiguration.*;
import org.uu.nl.net2apl.core.agent.Agent;
import org.uu.nl.net2apl.core.agent.AgentArguments;
import org.uu.nl.net2apl.core.agent.AgentCreationFailedException;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.logging.Loggable;
import org.uu.nl.net2apl.core.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class TomlConfigurationParser {

    public static final String CONF_AGENT_TYPE_KEY = "type";
    public static final String CONF_ONTOLOGY_KEY = "ontology";
    public static final String CONF_LINKSET_KEY = "linkset";
    public static final String CONF_DATABASE_KEY = "databases";
    public static final String CONF_AGENTS_KEY = "agents";
    public static final String CONF_DB_ICON_KEY = "database_icon";

    private static final Loggable LOGGER = Platform.getLogger();

    private final ArgumentParser parser;
    private final SseEventBus serverEventBus;
    private final Argument agentConfigurationArgument;

    private final List<RdfSourceConfig> ontologyConfigList = new ArrayList<>();
    private final List<RdfSourceConfig> linksetConfigList = new ArrayList<>();

    public TomlConfigurationParser(ArgumentParser parser, SseEventBus serverEventBus) {
        this.parser = parser;
        this.serverEventBus = serverEventBus;
        this.agentConfigurationArgument = createAgentConfigurationArgument();
    }

    public void parseAgentConfiguration(Platform platform, File tomlFile) throws ArgumentParserException {
        TomlParseResult result = parseTomlConfiguration(tomlFile);
        try {
            parseOntologies(result);
            parseLinkSets(result);
            parseDataSources(platform, result);
            parseAgents(platform, result);
        } catch (IllegalArgumentException e) {
            throw new ArgumentParserException(
                    "Failed to load agent configuration\n" + e.getMessage(),
                    e,
                    this.parser,
                    this.agentConfigurationArgument
            );
        }
    }

    private void parseOntologies(TomlParseResult result) throws IllegalArgumentException {
        if (!result.contains(CONF_ONTOLOGY_KEY))
            throw new IllegalArgumentException("At least one ontology is required");
        TomlArray ontologies = result.getArrayOrEmpty(CONF_ONTOLOGY_KEY);
        for(int i = 0; i < ontologies.size(); i++) {
            try {
                this.ontologyConfigList.add(new RdfSourceConfig(ontologies.getTable(i)));
            } catch (IllegalArgumentException e) {
                TomlPosition pos = result.inputPositionOf(CONF_ONTOLOGY_KEY);
                throw new IllegalArgumentException(
                        String.format("Error encountered in the %dth ontology starting on line %d:\n%s",
                                i,
                                pos.line(),
                                e.getMessage()
                        )
                );
            }
        }
    }

    private void parseLinkSets(TomlParseResult result)  throws IllegalArgumentException {
        TomlArray linksets = result.getArrayOrEmpty(CONF_LINKSET_KEY);
        for(int i = 0; i < linksets.size(); i++) {
            try {
                this.linksetConfigList.add(new RdfSourceConfig(linksets.getTable(i)));
            } catch (IllegalArgumentException e) {
                TomlPosition pos = result.inputPositionOf(CONF_LINKSET_KEY);
                throw new IllegalArgumentException(
                        String.format("Error encountered in the %dth linkset starting on line %d:\n%s",
                                i,
                                pos.line(),
                                e.getMessage()
                        )
                );
            }
        }
    }

    private void parseDataSources(Platform platform, TomlParseResult result)  throws IllegalArgumentException {
        String icon = result.contains(CONF_DB_ICON_KEY) ? result.getString(CONF_DB_ICON_KEY) : null;
        TomlTable dataSources = result.getTableOrEmpty(CONF_DATABASE_KEY);
        for(String dataSourceKey : dataSources.keySet()) {
            TomlTable dataSource = dataSources.getTable(dataSourceKey);
            // Create agent arguments
            try {
                AgentArguments arguments = new AgentArguments();
                UiAgentConfig config = new UiAgentConfig(dataSource, icon, CrudAgent.AgentType.DB);
                config.addConfigurationToArguments(arguments);
                new SparqlAgentConfig(dataSource).addConfigurationToArguments(arguments);
                createAgent(platform, arguments, config.getLocalName(), false, this.serverEventBus);
            } catch (IllegalArgumentException | AgentCreationFailedException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Failed to create agent for data source [%s.%s] starting on line %d:\n%s",
                                CONF_DATABASE_KEY,
                                dataSourceKey,
                                result.inputPositionOf(String.format("%s.%s", CONF_DATABASE_KEY, dataSourceKey)).line(),
                                e.getMessage()
                        )
                );
            }
        }
    }

    public static Agent createAgent(
            Platform platform,
            AgentArguments arguments,
            String localName,
            boolean isDF,
            SseEventBus eventBus
    ) throws AgentCreationFailedException {
        AgentID agentID = createAgentID(platform, localName);
        Agent agent;
        if (isDF) {
            agent = platform.newDirectoryFacilitator(agentID);
        } else {
            agent = new Agent(platform, arguments, agentID);
        }

        agent.addContext(new DirectSsePublisher(eventBus, agent.getAID()));
        agent.addContext(new CrudMessagePublisher(agent));

        Platform.getLogger().log(
                AgentService.class, Level.INFO,
                String.format("Created %s agent from startup configuration", localName)
        );
        eventBus.handleEvent(SseEvent.of("agent_create", localName));

        return agent;
    }

    private void parseAgents(Platform platform, TomlParseResult result) throws IllegalArgumentException {
        TomlTable agents = result.getTableOrEmpty(CONF_AGENTS_KEY);
        for(String agentKey : agents.keySet()) {
            TomlTable agent = agents.getTable(agentKey);
            boolean isDF = false;
            CrudAgent.AgentType agentType;
            try {
                agentType = CrudAgent.AgentType.fromString(agent.getString(CONF_AGENT_TYPE_KEY, () -> ""));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Agent type '%s' on configuration of '[%s.%s]' unrecognized. Choose one of %s",
                                CONF_AGENT_TYPE_KEY,
                                agent.getString(CONF_AGENT_TYPE_KEY, () -> ""),
                                agentKey,
                                Arrays.stream(CrudAgent.AgentType.values()).map(CrudAgent.AgentType::getType).collect(Collectors.joining(", "))
                        )
                );
            }

            AgentArguments arguments = new AgentArguments();
            UiAgentConfig uiAgentConfig = new UiAgentConfig(agent);
            uiAgentConfig.addConfigurationToArguments(arguments);

            switch (agentType) {
                case BROKER:
                    new BrokerAgentConfig(agent, this.ontologyConfigList, this.linksetConfigList).addConfigurationToArguments(arguments);
                    break;
                case USER:
                    new UserAgentConfig(agent).addConfigurationToArguments(arguments);
                    break;
                case DF:
                    isDF = true;
                    break;
                case EMBEDDING:
                    LOGGER.log(getClass(), Level.WARNING, "EMBEDDING agent specified in configuration, but no way to resolve");
                    break;
            }
            try {
                createAgent(platform, arguments, uiAgentConfig.getLocalName(), isDF, this.serverEventBus);
            } catch (AgentCreationFailedException e) {
                throw new IllegalArgumentException(
                    String.format(
                            "Failed to create agent from configuration of '[%s.%s]' starting on line %d:\n%s",
                            CONF_AGENTS_KEY,
                            agentKey,
                            result.inputPositionOf(String.format("%s.%s", CONF_AGENTS_KEY, agentKey)).line(),
                            e.getMessage()
                    )
                );
            }
        }
    }

    private TomlParseResult parseTomlConfiguration(File tomlFile) throws ArgumentParserException {
        try {
            return Toml.parse(tomlFile.toPath());
        } catch (IOException e) {
            throw new ArgumentParserException(
                    "Failed to parse TOML configuration file",
                    e,
                    this.parser,
                    this.agentConfigurationArgument
            );
        }
    }

    private Argument createAgentConfigurationArgument() {
        LOGGER.log(getClass(), Level.INFO, "Adding agent configuration argument to startup arguments parser");
        return parser.addArgument("-c", "--configuration")
                .type(net.sourceforge.argparse4j.impl.Arguments.fileType().verifyExists().verifyCanRead())
                .required(true)
                .help("Specify the location of the configuration TOML file. This file should specify all agents")
                .dest("agentConfiguration");
    }

    private static AgentID createAgentID(Platform platform, String localName) {
        AgentID agentID = null;
        try {
            if (localName == null) {
                agentID = AgentID.createEmpty();
            } else {
                URI uri = new URI(
                        null,
                        UUID.randomUUID().toString(),
                        platform.getHost(),
                        platform.getPort(),
                        null,
                        null,
                        localName
                );
                agentID = new AgentID(uri);
            }
        } catch (URISyntaxException e) {
            LOGGER.log(TomlConfigurationParser.class, Level.SEVERE, "Failed to create agent ID for local name " + localName);
            LOGGER.log(TomlConfigurationParser.class, Level.SEVERE, e);
        }
        return agentID;
    }


}
