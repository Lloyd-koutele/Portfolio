package com.portfolio.visit.internal;

import com.portfolio.auth.AdminPrincipal;
import com.portfolio.page.PageApi;
import com.portfolio.page.PageRef;
import com.portfolio.settings.SettingsApi;
import com.portfolio.visit.DeviceType;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a page view. Nothing is stored for the logged-in administrator, an unknown or unpublished page, or a
 * session that already viewed the same page within the deduplication window.
 */
@Service
class VisitRecorder
{
    private static final int MAX_USER_AGENT = 512;

    private final JdbcTemplate jdbc;
    private final PageApi pageApi;
    private final SettingsApi settingsApi;
    private final IpAnonymizer anonymizer;
    private final DeviceDetector deviceDetector;
    private final TrackingProperties properties;
    private final Clock clock;

    VisitRecorder(JdbcTemplate jdbc, PageApi pageApi, SettingsApi settingsApi, IpAnonymizer anonymizer,
        DeviceDetector deviceDetector, TrackingProperties properties, Clock clock)
    {
        this.jdbc = jdbc;
        this.pageApi = pageApi;
        this.settingsApi = settingsApi;
        this.anonymizer = anonymizer;
        this.deviceDetector = deviceDetector;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return whether a row was stored
     */
    @Transactional
    boolean record(VisitRequest request, String clientIp, String userAgent, Authentication authentication)
    {
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal)
        {
            return false;
        }
        Optional<PageRef> page = pageApi.findPublishedBySlug(request.pageSlug());
        if (page.isEmpty())
        {
            return false;
        }
        Instant now = clock.instant();
        Boolean seen = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM visit WHERE session_id = ? AND page_slug = ? AND occurred_at > ?)",
            Boolean.class, request.sessionId(), page.get().slug(), Timestamp.from(now.minus(properties.dedupWindow())));
        if (Boolean.TRUE.equals(seen))
        {
            return false;
        }
        DeviceType device = deviceDetector.detect(userAgent);
        String hash = anonymizer.hash(clientIp);
        String display = anonymizer.display(clientIp, hash, settingsApi.current().ipMode());
        jdbc.update("""
            INSERT INTO visit (occurred_at, ip_display, ip_hash, user_agent, device_type, is_bot, referer, page_id,
                               page_slug, session_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, (SELECT id FROM page WHERE id = ?), ?, ?)
            """,
            Timestamp.from(now), display, hash, truncate(userAgent, MAX_USER_AGENT), device.name(),
            device == DeviceType.BOT, cleanReferer(request.referer()), page.get().id(), page.get().slug(),
            request.sessionId());
        return true;
    }

    /** Keeps only scheme, host and path of an http(s) referrer; drops query strings that may carry personal data. */
    static String cleanReferer(String referer)
    {
        if (referer == null || referer.isBlank())
        {
            return null;
        }
        try
        {
            URI uri = new URI(referer.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!"http".equals(scheme) && !"https".equals(scheme)) || uri.getHost() == null)
            {
                return null;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return truncate(scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + path, 2048);
        }
        catch (URISyntaxException exception)
        {
            return null;
        }
    }

    private static String truncate(String value, int max)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
