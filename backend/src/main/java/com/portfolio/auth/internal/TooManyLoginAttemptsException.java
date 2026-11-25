package com.portfolio.auth.internal;

import com.portfolio.common.ApiException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class TooManyLoginAttemptsException extends ApiException
{
    private final long retryAfterSeconds;

    TooManyLoginAttemptsException(Duration retryAfter)
    {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts", "Too many login attempts, try again later");
        this.retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    long retryAfterSeconds()
    {
        return retryAfterSeconds;
    }

    @Override
    public HttpHeaders headers()
    {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }
}
