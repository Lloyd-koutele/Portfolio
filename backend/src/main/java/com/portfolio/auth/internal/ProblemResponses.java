package com.portfolio.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes security failures (401, 403) as RFC 7807 problems, like every other API error.
 */
@Component
class ProblemResponses
{
    private final ObjectMapper objectMapper;

    ProblemResponses(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    AuthenticationEntryPoint unauthorized()
    {
        return (request, response, exception) ->
            write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
    }

    AccessDeniedHandler forbidden()
    {
        return (request, response, exception) ->
            write(response, HttpStatus.FORBIDDEN, "Forbidden", "Access denied or missing CSRF token");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
        throws IOException
    {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
