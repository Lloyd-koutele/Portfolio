package com.portfolio.profile.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.common.ApiException;
import com.portfolio.media.MediaApi;
import com.portfolio.media.MediaInfo;
import com.portfolio.media.MediaUsage;
import com.portfolio.page.PageApi;
import com.portfolio.profile.internal.ProfileDtos.Experience;
import com.portfolio.profile.internal.ProfileDtos.ProfileRequest;
import com.portfolio.profile.internal.ProfileDtos.ProfileResponse;
import com.portfolio.profile.internal.ProfileDtos.PublicProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileService implements MediaUsage
{
    private static final TypeReference<List<String>> SKILLS = new TypeReference<>()
    {
    };
    private static final TypeReference<List<Experience>> EXPERIENCES = new TypeReference<>()
    {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MediaApi mediaApi;
    private final PageApi pageApi;
    private final Clock clock;

    ProfileService(JdbcTemplate jdbc, ObjectMapper objectMapper, MediaApi mediaApi, PageApi pageApi, Clock clock)
    {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mediaApi = mediaApi;
        this.pageApi = pageApi;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    ProfileResponse read()
    {
        Row row = load();
        return new ProfileResponse(row.fullName(), row.headline(), row.intro(), row.availability(), row.skills(),
            row.experiences(), row.photoMediaId(), photoUrl(row.photoMediaId()), row.updatedAt());
    }

    @Transactional(readOnly = true)
    PublicProfile publicProfile()
    {
        Row row = load();
        return new PublicProfile(row.fullName(), row.headline(), row.intro(), row.availability(), row.skills(),
            row.experiences(), photoUrl(row.photoMediaId()), pageApi.publishedProjects());
    }

    @Transactional
    ProfileResponse update(ProfileRequest request)
    {
        if (request.photoMediaId() != null && mediaApi.existing(List.of(request.photoMediaId())).isEmpty())
        {
            throw new UnknownPhotoException();
        }
        List<String> skills = request.skills().stream().map(String::trim).distinct().toList();
        jdbc.update("""
            UPDATE profile SET full_name = ?, headline = ?, intro = ?, availability = ?, skills = ?::jsonb,
                experiences = ?::jsonb, photo_media_id = ?, updated_at = ?
            WHERE id = 1
            """, request.fullName().trim(), request.headline().trim(), request.intro().trim(),
            request.availability().trim(), json(skills), json(request.experiences()), request.photoMediaId(),
            Timestamp.from(clock.instant()));
        return read();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsed(UUID mediaId)
    {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM profile WHERE photo_media_id = ?", Integer.class,
            mediaId);
        return count != null && count > 0;
    }

    private String photoUrl(UUID mediaId)
    {
        if (mediaId == null)
        {
            return null;
        }
        MediaInfo media = mediaApi.describe(List.of(mediaId)).get(mediaId);
        return media == null ? null : media.url();
    }

    private Row load()
    {
        return jdbc.queryForObject("""
            SELECT full_name, headline, intro, availability, skills::text, experiences::text, photo_media_id,
                updated_at
            FROM profile WHERE id = 1
            """, this::row);
    }

    private Row row(ResultSet rs, int index) throws SQLException
    {
        try
        {
            return new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                objectMapper.readValue(rs.getString(5), SKILLS),
                objectMapper.readValue(rs.getString(6), EXPERIENCES),
                rs.getObject(7, UUID.class), rs.getTimestamp(8).toInstant());
        }
        catch (JsonProcessingException exception)
        {
            throw new IllegalStateException("Unreadable profile", exception);
        }
    }

    private String json(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    private record Row(String fullName, String headline, String intro, String availability, List<String> skills,
        List<Experience> experiences, UUID photoMediaId, Instant updatedAt)
    {
    }

    static class UnknownPhotoException extends ApiException
    {
        UnknownPhotoException()
        {
            super(HttpStatus.BAD_REQUEST, "Invalid profile", "Photo media does not exist");
        }
    }
}
