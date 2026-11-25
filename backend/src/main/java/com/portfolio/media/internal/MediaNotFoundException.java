package com.portfolio.media.internal;

import com.portfolio.common.ApiException;
import org.springframework.http.HttpStatus;

class MediaNotFoundException extends ApiException
{
    MediaNotFoundException()
    {
        super(HttpStatus.NOT_FOUND, "Not found", "Media not found");
    }
}
