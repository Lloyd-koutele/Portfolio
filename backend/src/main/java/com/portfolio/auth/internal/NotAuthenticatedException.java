package com.portfolio.auth.internal;

import com.portfolio.common.ApiException;
import org.springframework.http.HttpStatus;

class NotAuthenticatedException extends ApiException
{
    NotAuthenticatedException()
    {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
    }
}
