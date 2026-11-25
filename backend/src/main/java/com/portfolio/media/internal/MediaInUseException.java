package com.portfolio.media.internal;

import com.portfolio.common.ApiException;
import org.springframework.http.HttpStatus;

class MediaInUseException extends ApiException
{
    MediaInUseException()
    {
        super(HttpStatus.CONFLICT, "Media in use", "This media is used by a page or the site settings");
    }
}
