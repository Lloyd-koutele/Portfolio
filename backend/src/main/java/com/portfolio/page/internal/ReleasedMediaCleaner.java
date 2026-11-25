package com.portfolio.page.internal;

import com.portfolio.media.MediaApi;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ReleasedMediaCleaner
{
    private final MediaApi mediaApi;

    ReleasedMediaCleaner(MediaApi mediaApi)
    {
        this.mediaApi = mediaApi;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onReleased(PageMediaReleased event)
    {
        for (UUID id : event.mediaIds())
        {
            mediaApi.deleteIfUnused(id);
        }
    }
}
