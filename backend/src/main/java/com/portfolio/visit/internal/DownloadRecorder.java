package com.portfolio.visit.internal;

import com.portfolio.auth.AdminPrincipal;
import com.portfolio.settings.SettingsApi;
import com.portfolio.visit.DeviceType;
import com.portfolio.visit.DownloadLog;
import com.portfolio.visit.DownloadSource;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DownloadRecorder implements DownloadLog
{
    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_FILE_NAME = 160;

    private final JdbcTemplate jdbc;
    private final SettingsApi settingsApi;
    private final IpAnonymizer anonymizer;
    private final DeviceDetector deviceDetector;
    private final Clock clock;

    DownloadRecorder(JdbcTemplate jdbc, SettingsApi settingsApi, IpAnonymizer anonymizer,
        DeviceDetector deviceDetector, Clock clock)
    {
        this.jdbc = jdbc;
        this.settingsApi = settingsApi;
        this.anonymizer = anonymizer;
        this.deviceDetector = deviceDetector;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean recordCvDownload(String fileName, DownloadSource source, String clientIp, String userAgent,
        String referer, Authentication authentication)
    {
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal)
        {
            return false;
        }
        DeviceType device = deviceDetector.detect(userAgent);
        String hash = anonymizer.hash(clientIp);
        String display = anonymizer.display(clientIp, hash, settingsApi.current().ipMode());
        jdbc.update("""
            INSERT INTO download (occurred_at, file, source, file_name, ip_display, ip_hash, user_agent,
                                  device_type, is_bot, referer)
            VALUES (?, 'CV', ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Timestamp.from(clock.instant()), source.name(), truncate(fileName, MAX_FILE_NAME), display, hash,
            truncate(userAgent, MAX_USER_AGENT), device.name(), device == DeviceType.BOT,
            VisitRecorder.cleanReferer(referer));
        return true;
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
