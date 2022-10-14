package org.uu.nl.goldenagents.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.uu.nl.goldenagents.services.SparqlEndpointService;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("sparql")
public class SparqlEndpointController {

    private final SparqlEndpointService service;

    @Autowired
    public SparqlEndpointController(SparqlEndpointService service) {
        this.service = service;
    }

    /**
     * Protocol clients may send protocol requests via the HTTP GET method. When using the GET method,
     * clients must URL percent encode all parameters and include them as query parameter
     * strings with the names given above [RFC3986].
     *
     * HTTP query string parameters must be separated with the ampersand (&) character. Clients may include the query string parameters in any order.
     *
     * The HTTP request MUST NOT include a message body.
     */
    @GetMapping(value = "")
    public ResponseEntity<String> getQuery(
            @RequestParam(value = "query", required = true) String encodedQuery,
            @RequestParam(value = "default-graph-uri", required = false) String defaultGraphUri,
            @RequestParam(value = "named-graph-uri", required = false) String namedGraphUri,
            @RequestParam(value = "use-expertise-search", required = false, defaultValue = "false") boolean useExpertiseSearch,
            @RequestParam(value = "format", defaultValue = "text") String format
    ) throws
            IOException
    {
        String queryString = URLDecoder.decode(encodedQuery, StandardCharsets.UTF_8);
        return service.evaluateSparqlQuery(
                queryString,
                defaultGraphUri,
                namedGraphUri,
                useExpertiseSearch,
                format
        );
    }

    /**
     * Protocol clients may send protocol requests via the HTTP POST method by including the query directly and
     * unencoded as the HTTP request message body. When using this approach, clients must include the SPARQL query string,
     * unencoded, and nothing else as the message body of the request.
     * Clients must set the content type header of the HTTP request to application/sparql-query.
     * Clients may include the optional default-graph-uri and named-graph-uri parameters as HTTP query string
     * parameters in the request URI. Note that UTF-8 is the only valid charset here.
     */
    @PostMapping("")
    public ResponseEntity<String> postQuery(
            @RequestBody(required = true) String queryString,
            @RequestParam(value = "default-graph-uri", required = false) String defaultGraphUri,
            @RequestParam(value = "named-graph-uri", required = false) String namedGraphUri,
            @RequestParam(value = "use-expertise-search", required = false, defaultValue = "false") boolean useExpertiseSearch,
            @RequestParam(value = "format", defaultValue = "text") String format
        ) throws
            IOException
    {
        return service.evaluateSparqlQuery(
                queryString,
                defaultGraphUri,
                namedGraphUri,
                useExpertiseSearch,
                format
        );
    }

}
