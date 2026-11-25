package com.portfolio.media.internal;

import com.portfolio.common.ApiException;
import org.springframework.http.HttpStatus;

class InvalidVideoException extends ApiException
{
    InvalidVideoException(String detail)
    {
        super(HttpStatus.BAD_REQUEST, "Invalid video", detail);
    }
}
