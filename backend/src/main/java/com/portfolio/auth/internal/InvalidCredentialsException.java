package com.portfolio.auth.internal;

import com.portfolio.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Same response for an unknown e-mail and a wrong password, so accounts cannot be enumerated.
 */
class InvalidCredentialsException extends ApiException
{
    InvalidCredentialsException()
    {
        super(HttpStatus.UNAUTHORIZED, "Invalid credentials", "E-mail or password is incorrect");
    }
}
