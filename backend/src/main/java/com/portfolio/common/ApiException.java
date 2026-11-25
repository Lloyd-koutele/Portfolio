package com.portfolio.common;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Business error turned into an RFC 7807 response by {@link GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException
{
    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String detail)
    {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus status()
    {
        return status;
    }

    public String title()
    {
        return title;
    }

    /**
     * Extra response headers, such as {@code Retry-After}. Empty by default.
     */
    public HttpHeaders headers()
    {
        return HttpHeaders.EMPTY;
    }

    /**
     * Extra problem members, such as {@code errors} or {@code code}. Empty by default.
     */
    public Map<String, Object> properties()
    {
        return Map.of();
    }
}
