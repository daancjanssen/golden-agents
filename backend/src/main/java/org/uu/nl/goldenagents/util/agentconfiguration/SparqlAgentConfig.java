package org.uu.nl.goldenagents.util.agentconfiguration;

import org.tomlj.TomlArray;
import org.tomlj.TomlTable;
import org.uu.nl.goldenagents.agent.args.DBAgentArguments;
import org.uu.nl.goldenagents.agent.context.DBAgentContext;
import org.uu.nl.goldenagents.agent.context.PrefixNSListenerContext;
import org.uu.nl.goldenagents.agent.plan.dbagent.LoadMappingsPlan;
import org.uu.nl.goldenagents.agent.context.query.DbTranslationContext;
import org.uu.nl.goldenagents.util.DatabaseConfig;
import org.uu.nl.net2apl.core.agent.AgentArguments;

import java.util.ArrayList;
import java.util.List;

public class SparqlAgentConfig implements IParseAgentConfiguration {

    public static final String CONF_ENDPOINT_KEY = "endpoint";
    public static final String CONF_MAPPING_KEY = "mapping";
    public static final String CONF_USE_ENTITIES_FOR_EXPERTISE = "entity_expertise";

    private TomlTable dataSourceConfiguration;

    private DatabaseConfig databaseConfig;
    private List<RdfSourceConfig> mappingFileLocations;

    public SparqlAgentConfig(TomlTable dataSourceConfiguration) {
        this.dataSourceConfiguration = dataSourceConfiguration;
        testConfigValidity();
        this.databaseConfig = new DatabaseConfig(this.dataSourceConfiguration.getTable(CONF_ENDPOINT_KEY));
        this.mappingFileLocations = parseMappingFileLocations();
    }

    @Override
    public AgentArguments addConfigurationToArguments(AgentArguments arguments) throws IllegalArgumentException {
        if(this.databaseConfig == null)
            throw new IllegalArgumentException("Agent configuration not parsed. Database not properly configured");
        if(this.mappingFileLocations.isEmpty())
            throw new IllegalArgumentException("Agent configuration not parsed. No mapping provided for database agent");


        DBAgentContext c = new DBAgentContext(this.databaseConfig, this.mappingFileLocations);
        PrefixNSListenerContext prefixContext = new PrefixNSListenerContext();
        DbTranslationContext translationContext = new DbTranslationContext(c.getOntologyModel(), prefixContext);
        if(dataSourceConfiguration.contains(CONF_USE_ENTITIES_FOR_EXPERTISE)) {
            c.setLoadEntitiesForExpertise(this.dataSourceConfiguration.getBoolean(CONF_USE_ENTITIES_FOR_EXPERTISE));
        } else {
            c.setLoadEntitiesForExpertise(false);
        }

        DBAgentArguments sparqlAgentArguments = new DBAgentArguments(c);
        sparqlAgentArguments.addContext(prefixContext);
        sparqlAgentArguments.addContext(translationContext);
        arguments.include(sparqlAgentArguments);
        arguments.addInitialPlan(new LoadMappingsPlan());

        return arguments;
    }

    private void testConfigValidity() {
        if(!this.dataSourceConfiguration.contains(CONF_ENDPOINT_KEY))
            throw new IllegalArgumentException("Agent configuration does not contain database configuration object");

        if(!this.dataSourceConfiguration.contains(CONF_MAPPING_KEY))
            throw new IllegalArgumentException("Agent mapping file not specified. Database agent cannot participate without mapping file");
    }

    private List<RdfSourceConfig> parseMappingFileLocations() {
        List<RdfSourceConfig> mappingFileLocations = new ArrayList<>();
        TomlArray mappingConfiguration = this.dataSourceConfiguration.getArray(CONF_MAPPING_KEY);
        for(int i = 0; i < mappingConfiguration.size(); i++) {
            mappingFileLocations.add(new RdfSourceConfig(mappingConfiguration.getTable(i)));
        }
        return mappingFileLocations;
    }
}
