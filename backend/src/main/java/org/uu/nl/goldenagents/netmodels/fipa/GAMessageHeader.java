package org.uu.nl.goldenagents.netmodels.fipa;

public enum GAMessageHeader {
	
	/*------------------------------
	 ********** DB AGENT **********
	------------------------------*/
	
	/**
	 * This content in this message is the start of a new data transmission
	 */
	DB_DATA_START(200),
	/**
	 * The content in this message is an intermittent data package in the transmission 
	 */
	DB_DATA_CONTINUE(201),
	/**
	 * The content in this message is the end of a data transmission
	 */
	DB_DATA_END(202),
	/**
	 * There was an error with sending data
	 */
	DB_ERROR(203),
	/**
	 * Data could not be sent because the sender was too busy
	 */
	DB_BUSY(204),
	/**
	 * Used for asking and sending the capabilities of a DB-agent
	 */
	//DB_CAPABILITIES(205),
	/**
	 * Used for asking and sending the expert information of a DB-agent
	 */
	DB_EXPERTISE(206),

	/*----------------------------
	 ********** BROKER **********
	----------------------------*/
	
	/**
	 * A query is sent from the broker to a db-agent
	 */
	BROKER_QUERY(300),
	
	/**
	 * A broker acknowledges to the db-agent it received part of a data transmission
	 */
	BROKER_ACK(301),
	
	/**
	 * The broker sends the final answer back to the user
	 */
	BROKER_RESULTSET(302),
	
	/**
	 * The user sends a query to the broker
	 */
	USER_QUERY(303),
	
	/**
	 * The broker could not parse the SPARQL query
	 */
	QUERY_SYNTAX_ERROR(304),
	
	/**
	 * The user requests intelligent search from the broker for a query 
	 * TODO This conceptually does not fit here, needs a better name or different solution
	 */
	USER_INTELLIGENT_SEARCH(305),

	/**
	 * The broker agent keeps track of the RDF prefixes (namespaces) it knows. To ensure data comes back properly,
	 * this prefix mapping should be used by other agents as well for the same IRI's. This performative is used to
	 * request that mapping from the agent
	 */
	REQUEST_PREFIX_MAPPING(400),

	/**
	 * The user requests a partial search for a partial query constructed in the query builder.
	 * Apart from results, suggestions for the next change in the query should be provided
	 */
	USER_PARTIAL_SEARCH(306),

	/**
	 * Fallback
	 */
	UNKNOWN(0),

	/*----------------------------
	 ********** AQL **********
	----------------------------*/

	/**
	 * Request suggestions for classes, properties and instances for a given query
	 */
	REQUEST_SUGGESTIONS(401),

	/**
	 * Request additional suggestions with more detail for classes, properties and instances. Calculating suggestions
	 * is allowed to take more time
	 */
	REQUEST_IMPROVE_SUGGESTIONS(402),

	/**
	 * A reply to REQUEST_SUGGESTIONS or REQUEST_IMPROVE_SUGGESTIONS, containing a list of classes, properties, and
	 * instances that can be intersected with the query that was sent in one of those requests at the focus that was
	 * active at the time
	 */
	INFORM_SUGGESTIONS(403),

	/**
	 * Used to indicate suggestions should use the convoluted approach of querying each individual db
	 */
	REQUEST_DATA_BASED_SUGGESTIONS(406),

	/**
	 * A request to execute the given AQL query.
	 */
	REQUEST_PERFORM_QUERY(404);
	
	final int index;
	
	private GAMessageHeader(int index) {
		this.index = index;
	}
	
	public int toIndex() {
		return this.index;
	}
	
	/**
	 * Get the enum value from the index, this iterates over all enums so perhaps don't do this too often
	 * @param index
	 * @return
	 */
	public static GAMessageHeader fromIndex(int index) {
		for(GAMessageHeader header : GAMessageHeader.values()) {
			if(header.index == index) return header;
		}
		return UNKNOWN;
	}
}
