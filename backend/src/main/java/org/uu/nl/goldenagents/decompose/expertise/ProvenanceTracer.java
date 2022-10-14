package org.uu.nl.goldenagents.decompose.expertise;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.uu.nl.goldenagents.sparql.BindInfo;
import org.uu.nl.goldenagents.sparql.QueryInfo;
import org.uu.nl.goldenagents.sparql.TripleInfo;
import org.uu.nl.net2apl.core.agent.AgentID;
import org.uu.nl.net2apl.core.logging.Loggable;
import org.uu.nl.net2apl.core.platform.Platform;

/**
 *	A class that takes {@code QueryInfo} and traces the provenance
 *	information of variables that are used to project the results
 *
 *	@author Golden Agents Group, Utrecht University
 */
public class ProvenanceTracer {
	
	/**
	 * Query information
	 */
	private QueryInfo qInfo;
	/**
	 * Map of variables to the agents that provides data to the results
	 */
	private Map<String, Set<AgentID>> mappedSources = new LinkedHashMap<>();
	/**.
	 * Logger of the platform
	 */
	private static final Loggable LOGGER = Platform.getLogger();

	/**
	 * Basic constructor
	 * @param qInfo query info
	 */
	public ProvenanceTracer(QueryInfo qInfo) {
		super();
		this.qInfo = qInfo;
	}
	
	/**
	 *	Traces sources providing data to a variable in results
	 */
	public void trace() {
		for(String var : this.qInfo.getVarTripleInfoMapping().keySet()) {
			Set<AgentID> sources = new LinkedHashSet<>();
			for(TripleInfo ti : this.qInfo.getVarTripleInfoMapping().get(var)) {
				sources.addAll(ti.getChosenSources());
			}
			this.mappedSources.put(var, sources);
		}
		findSourcesOfBindExpressions();
		findSourcesOfAsExpressions();
	}
	
	/**
	 * Returns sets of agent IDs mapped to variable names
	 * @return sets of agent IDs mapped to variable names
	 */
	public Map<String, Set<AgentID>> getMappedSources() {	
		return this.mappedSources;
	}
	
	/**
	 * Finds the sources of the variables that are defined in 
	 * the "BIND" statements of the where clause.
	 */
	private void findSourcesOfBindExpressions() {
		for(BindInfo bi : this.qInfo.getBinds()) {
			Set<String> toBeAdded = new LinkedHashSet<>(bi.getVariables());
			toBeAdded.removeAll(this.mappedSources.keySet());
			Set<String> mappingFromVars = new LinkedHashSet<>(bi.getVariables());
			mappingFromVars.retainAll(this.mappedSources.keySet());
			for(String varToAdd : toBeAdded) {
				mapSources(varToAdd, mappingFromVars);
			}
		}
	}
	
	/**
	 * Maps the sources of variables that are related to the variable that is without a known source.
	 * 
	 * @param varToAdd variable that will be added to mapping
	 * @param mappingFromVars variables that have known sources and are related to the {@code varToAdd}
	 */
	private void mapSources(String varToAdd, Set<String> mappingFromVars) {
		Set<AgentID> sources = new LinkedHashSet<>();
		for(String knownVar : mappingFromVars) {
			if(this.mappedSources.containsKey(knownVar)) {
				sources.addAll(this.mappedSources.get(knownVar));
			}
			else {
				LOGGER.log(ProvenanceTracer.class, Level.WARNING, 
						this.mappedSources.keySet() + " does not contain " + knownVar);
			}
		}
		this.mappedSources.put(varToAdd, sources);
	}
	
	/**
	 * Finds the sources of the variables that are defined in the "AS" expressions of the select clause.
	 */
	private void findSourcesOfAsExpressions() {
		Map<String, Set<String>> varsToAdd = findVarsInAsExpressions();
		varsToAdd.forEach((varToAdd, mappingFromVars) -> {
			mapSources(varToAdd, mappingFromVars);
		});
	}
	
	/**
	 * Finds new variables that are defined in "AS" expressions of the select clause.
	 * Maps these variables to the variables used in the expression to calculate the new variable. 
	 *
	 * @return mapping from variables defined in select clause to variables defined in where clause 
	 */
	private Map<String, Set<String>> findVarsInAsExpressions() {
		Query q = this.qInfo.getAliasedJenaQuery();
		Map<String,Set<String>> found = new LinkedHashMap<>();
		Set<String> notFoundVars = new LinkedHashSet<>();
		for(Var var : q.getProject().getExprs().keySet()) {
			Expr exp = q.getProject().getExprs().get(var);
			if(exp.getVarsMentioned().isEmpty()) {
				if(exp instanceof ExprAggregator) {
					ExprAggregator expAgg = (ExprAggregator) exp;
					found.put(var.toString(), castToStrSet(expAgg.getAggregator().getExprList().getVarsMentioned()));
				}
				else {
					notFoundVars.add(var.toString());
				}
			}
			else {
				found.put(var.toString(), castToStrSet(exp.getVarsMentioned()));
			}
		}
		if(!notFoundVars.isEmpty()) {
			LOGGER.log(ProvenanceTracer.class, Level.WARNING, "The following variables, which are in select clause,"
					+ " could not be mapped to other variables: " + notFoundVars);
		}
		return found;
	}
	
	/**
	 * Casts variables to their names as string
	 * @param	vars set of variables
	 * @return	set of variable names
	 */
	public Set<String> castToStrSet(Set<Var> vars){
		return vars.stream().map(x -> x.toString()).collect(Collectors.toSet());
	}
}
