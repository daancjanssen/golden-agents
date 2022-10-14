package org.uu.nl.goldenagents.util.agentconfiguration;

import org.tomlj.TomlTable;
import org.uu.nl.goldenagents.agent.context.UIContext;
import org.uu.nl.goldenagents.netmodels.angular.CrudAgent;
import org.uu.nl.goldenagents.util.TomlConfigurationParser;
import org.uu.nl.net2apl.core.agent.AgentArguments;
import org.uu.nl.net2apl.core.platform.Platform;

import java.util.Arrays;
import java.util.stream.Collectors;

public class UiAgentConfig implements IParseAgentConfiguration {

    public static final String CONF_NAME_KEY = "localname";
    public static final String CONF_ICON_KEY = "icon";
    public static final String CONF_DESCRIPTION_KEY = "description";
    public static final String CONF_HOMEPAGE_KEY = "homepage";

    private String localName;
    private String icon;
    private CrudAgent.AgentType agentType;
    private String description;
    private String homepage;

    public UiAgentConfig(TomlTable configuration) {
        this(configuration, null, CrudAgent.AgentType.USER);
    }

    public UiAgentConfig(String localName, String icon, CrudAgent.AgentType agentType, String description, String homepage) {
        this.localName = localName;
        this.icon = icon;
        this.agentType = agentType;
        this.description = description;
        this.homepage = homepage;
    }

    public UiAgentConfig(TomlTable configuration, String icon, CrudAgent.AgentType agentType) {
        if(!configuration.contains(CONF_NAME_KEY))
            throw new IllegalArgumentException("Missing key " + CONF_NAME_KEY);

        if(icon == null && !configuration.contains(CONF_ICON_KEY))
            throw new IllegalArgumentException("Missing key " + CONF_ICON_KEY);

        this.agentType = agentType;

        this.localName = configuration.getString(CONF_NAME_KEY);
        this.icon = icon == null ? configuration.getString(CONF_ICON_KEY) : icon;
        this.description = configuration.getString(CONF_DESCRIPTION_KEY, () -> "");
        this.homepage = configuration.getString(CONF_HOMEPAGE_KEY, () -> "");

        if(configuration.contains(TomlConfigurationParser.CONF_AGENT_TYPE_KEY)) {
            String agentTypeStr = configuration.getString(TomlConfigurationParser.CONF_AGENT_TYPE_KEY, () -> "");
            try {
                this.agentType = CrudAgent.AgentType.fromString(agentTypeStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Agent type '%s' not recognized. Choose one of %s",
                                agentTypeStr,
                                Arrays.stream(CrudAgent.AgentType.values()).
                                        map(CrudAgent.AgentType::getType).collect(Collectors.joining(", "))
                        )
                );
            }
        }
    }

    @Override
    public AgentArguments addConfigurationToArguments(AgentArguments arguments) {
        UIContext c = new UIContext(this.icon, this.localName, this.agentType);
        arguments.addContext(c);
        return arguments;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
