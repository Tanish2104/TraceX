package com.tracex.config;

import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Shows a readable page when the graph database is down or rejects a query. */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Neo4jException.class)
    public ModelAndView handleNeo4j(Neo4jException ex) {
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        mv.addObject("message", "The graph database is not reachable or rejected the request. "
                + "Check that Neo4j is running and the password in application.properties is correct. "
                + "Details: " + ex.getMessage());
        return mv;
    }
}
