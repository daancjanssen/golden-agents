package org.uu.nl.goldenagents.agent.plan.dbagent;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.uu.nl.goldenagents.agent.context.DBAgentContext;
import org.uu.nl.goldenagents.agent.plan.LoadRDFSourcePlan;
import org.uu.nl.net2apl.core.agent.PlanToAgentInterface;
import org.uu.nl.net2apl.core.plan.PlanExecutionError;

public class LoadMappingsPlan extends LoadRDFSourcePlan {

    @Override
    public void executeOnce(PlanToAgentInterface planToAgentInterface) throws PlanExecutionError {
        DBAgentContext c = planToAgentInterface.getAgent().getContext(DBAgentContext.class);
        Model model = ModelFactory.createDefaultModel();
        this.loadModel(planToAgentInterface, model, c.getMappingFiles());
        c.addMapping(model);
    }
}
