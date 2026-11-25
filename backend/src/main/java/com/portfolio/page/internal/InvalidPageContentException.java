package com.portfolio.page.internal;

import com.portfolio.common.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;

class InvalidPageContentException extends ApiException
{
    private final Map<String, String> errors;

    InvalidPageContentException(Map<String, String> errors)
    {
        super(HttpStatus.BAD_REQUEST, "Invalid page content", "The page content is invalid");
        this.errors = Map.copyOf(errors);
    }

    @Override
    public Map<String, Object> properties()
    {
        return Map.of("errors", errors);
    }
}
