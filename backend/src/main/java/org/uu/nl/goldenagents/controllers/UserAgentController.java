package org.uu.nl.goldenagents.controllers;

import org.apache.jena.graph.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.uu.nl.goldenagents.aql.AQLTree;
import org.uu.nl.goldenagents.aql.feature.Feature;
import org.uu.nl.goldenagents.aql.feature.NamedLiteral;
import org.uu.nl.goldenagents.aql.feature.NamedResource;
import org.uu.nl.goldenagents.aql.feature.TypeSpecification;
import org.uu.nl.goldenagents.exceptions.AgentNotFoundException;
import org.uu.nl.goldenagents.netmodels.angular.*;
import org.uu.nl.goldenagents.netmodels.angular.aql.AQLJsonObject;
import org.uu.nl.goldenagents.netmodels.datatables.DataTableResult;
import org.uu.nl.goldenagents.netmodels.fipa.EntityList;
import org.uu.nl.goldenagents.netmodels.fipa.UserQueryTrigger;
import org.uu.nl.goldenagents.netmodels.jena.SerializableResourceImpl;
import org.uu.nl.goldenagents.services.UserAgentService;
import org.uu.nl.net2apl.core.agent.AgentCreationFailedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("api/agent/user")
public class UserAgentController {

    private final UserAgentService service;

    @Autowired
    public UserAgentController(UserAgentService service) {
        this.service = service;
    }

    private UUID resolveUUID(String agentID) {
        return UUID.fromString(agentID);
    }

    @GetMapping("")
    public CrudAgent getUserAgent(@RequestParam(value = "agentID", required = false) String agentIDString) throws AgentCreationFailedException {
        UUID agentID = agentIDString == null ? null : UUID.fromString(agentIDString);
        return service.getUserAgent(agentID);
    }

    @PostMapping(value = "/{agentid}/query")
    public UserQueryTrigger query(@PathVariable(value = "agentid", required = false) String agentID, @RequestBody UserQueryTrigger request) throws AgentNotFoundException, IllegalArgumentException {
        if(request == null) throw new IllegalArgumentException("No request body provided");
        return service.initiateQuery(resolveUUID(agentID), request);
    }

    @GetMapping("/{agentid}/history")
    public Collection<CachedQueryInfo> getQueryHistory(@PathVariable(value = "agentid", required = false) String agentID) throws AgentNotFoundException, IllegalArgumentException {
        return service.queryHistory(resolveUUID(agentID));
    }

    @GetMapping("/{agentid}/lastQuery")
    public String lastQuery(@PathVariable(value = "agentid", required = false) String agentID) throws AgentNotFoundException, IllegalArgumentException{
        return service.getLastQuery(resolveUUID(agentID));
    }

    @GetMapping("/{agentid}/lastQueryID")
    public String lastQueryID(@PathVariable(value = "agentid", required = false) String agentID) throws AgentNotFoundException, IllegalArgumentException {
        return service.getLastQueryID(resolveUUID(agentID));
    }

    @PostMapping("/queryresult")
    @ResponseBody
    public DataTableResult listQueryResultsPaginated(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return service.getPaginatedQueryResults(request);
    }

    @GetMapping("/{agentid}/csv/{queryid}")
    public String getResultsAsCSV(@PathVariable("agentid") String agentID, @PathVariable("queryid") String queryID, HttpServletResponse response) throws AgentNotFoundException, IllegalArgumentException {
        response.addHeader("Content-Disposition", "attachment;filename=GoldenAgentQueryResults.csv");
        response.setContentType("text/csv");
        return service.getResutlsAsCSV(UUID.fromString(agentID), queryID).toString();
    }

    @GetMapping("/{agentid}/xml/{queryid}")
    public String getResultsAsXML(@PathVariable("agentid") String agentID, @PathVariable("queryid") String queryID, HttpServletResponse response) throws AgentNotFoundException, IllegalArgumentException {
        response.addHeader("Content-Disposition", "attachment;filename=GoldenAgentQueryResults.xml");
        response.setContentType("text/xml");
        return service.getResutlsAsXML(UUID.fromString(agentID), queryID).toString();
    }

    /****************************************
     * START OF AQL RELATED INTERFACES
     * ***************************************/

    @Deprecated
    @GetMapping("/{agentid}/aqlquery")
    @ResponseBody
    public AQLQueryObject getAQLQuery(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.getCurrentQuery(UUID.fromString(agentID));
    }

    @GetMapping("/{agentid}/aqlqueryjson")
    public AQLJsonObject getAQLJSONQuery(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.getCurrentQueryAsJson(UUID.fromString(agentID));
    }

    @GetMapping("/{agentid}/aqlsuggestions")
    @ResponseBody
    public AQLSuggestions getSuggestions(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.getSuggestions(UUID.fromString(agentID));
    }

    @GetMapping(value="/{agentid}/aqlquery/sparqltranslation", produces="text/plain;charset=UTF-8")
    @ResponseBody
    public String getSparqlTranslation(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.getSparqlTranslation(UUID.fromString(agentID));
    }

    @PostMapping("/{agentid}/aqlquery/addClass")
    @ResponseBody
    public AQLJsonObject addClass(@PathVariable("agentid") String agentID, @RequestBody AQLResource classUri) {
        SerializableResourceImpl resource = new SerializableResourceImpl(classUri.uri);
        TypeSpecification feature = new TypeSpecification(resource, classUri.label);
        return service.intersect(UUID.fromString(agentID), feature);
    }

    @PostMapping("/{agentid}/aqlquery/addProperty/{forwards}")
    @ResponseBody
    public AQLJsonObject addProperty(@PathVariable("agentid") String agentID, @PathVariable("forwards") boolean forwards, @RequestBody AQLResource propertyUri) {
        return service.cross(UUID.fromString(agentID), propertyUri, forwards);
    }

    @PostMapping("/{agentid}/aqlquery/addEntity")
    @ResponseBody
    public AQLJsonObject addEntity(@PathVariable("agentid") String agentID, @RequestBody AQLSuggestions.InstanceSuggestion entity) {
        EntityList.Entity e = new EntityList.Entity(entity);
        Feature feature;
        if (e.isUri()) {
            feature = new NamedResource(new SerializableResourceImpl(e.getValue()));
        } else {
            feature = new NamedLiteral(e);
        }

        return service.intersect(UUID.fromString(agentID), feature);
    }

    @PostMapping("/{agentid}/aqlquery/intersect")
    @ResponseBody
    public AQLJsonObject intersect(@PathVariable("agentid") String agentID, Feature feature) throws URISyntaxException {
        return service.intersect(UUID.fromString(agentID), feature);
    }

    @PostMapping("/{agentid}/aqlquery/exclude")
    @ResponseBody
    public AQLJsonObject exclude(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.exclude(UUID.fromString(agentID));
    }

    @PostMapping("/{agentid}/aqlquery/union")
    @ResponseBody
    public AQLJsonObject union(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.union(UUID.fromString(agentID));
    }

    @PostMapping("/{agentid}/aqlquery/focus/{focus}")
    @ResponseBody
    public AQLJsonObject focus(@PathVariable("agentid") String agentID, @PathVariable("focus") String focus) throws URISyntaxException {
        AQLTree.ID focusName = AQLTree.ID.fromString(focus);
        return service.changeFocus(UUID.fromString(agentID), focusName);
    }

    @PostMapping("/{agentid}/aqlquery/delete")
    @ResponseBody
    public AQLJsonObject deleteCurrentFocus(@PathVariable("agentid") String agentID) throws URISyntaxException {
        return service.deleteCurrentFocus(UUID.fromString(agentID));
    }

    @PostMapping("/{agentid}/aqlquery/delete/{focus}")
    @ResponseBody
    public AQLJsonObject delete(@PathVariable("agentid") String agentID, @PathVariable("focus") String focus) throws URISyntaxException {
        AQLTree.ID focusName = AQLTree.ID.fromString(focus);
        return service.deleteQueryFocus(UUID.fromString(agentID), focusName);
    }

}
