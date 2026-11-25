package com.portfolio.media.internal;

import com.portfolio.common.ApiException;
import org.springframework.http.HttpStatus;

class InvalidImageException extends ApiException
{
    InvalidImageException(String detail)
    {
        super(HttpStatus.BAD_REQUEST, "Invalid image", detail);
    }
}
