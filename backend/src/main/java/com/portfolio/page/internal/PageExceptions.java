package com.portfolio.page.internal;

import com.portfolio.common.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;

final class PageExceptions
{
    private PageExceptions()
    {
    }

    static class PageNotFoundException extends ApiException
    {
        PageNotFoundException()
        {
            super(HttpStatus.NOT_FOUND, "Not found", "Page not found");
        }
    }

    static class ProjectNotFoundException extends ApiException
    {
        ProjectNotFoundException()
        {
            super(HttpStatus.NOT_FOUND, "Not found", "Project not found");
        }
    }

    /** Cover, table of contents, summary and contact are part of the book's structure. */
    static class FixedPageException extends ApiException
    {
        FixedPageException()
        {
            super(HttpStatus.CONFLICT, "Fixed page", "This page is part of the book's structure and cannot be "
                + "deleted or duplicated");
        }

        @Override
        public Map<String, Object> properties()
        {
            return Map.of("code", "FIXED_PAGE");
        }
    }

    static class VersionConflictException extends ApiException
    {
        private final int currentVersion;

        VersionConflictException(int currentVersion)
        {
            super(HttpStatus.CONFLICT, "Conflict", "The page was modified elsewhere");
            this.currentVersion = currentVersion;
        }

        @Override
        public Map<String, Object> properties()
        {
            return Map.of("code", "VERSION_CONFLICT", "currentVersion", currentVersion);
        }
    }

    static class InvalidFieldException extends ApiException
    {
        private final String field;
        private final String message;

        InvalidFieldException(String field, String message)
        {
            super(HttpStatus.BAD_REQUEST, "Bad Request", "Request validation failed");
            this.field = field;
            this.message = message;
        }

        @Override
        public Map<String, Object> properties()
        {
            return Map.of("errors", Map.of(field, message));
        }
    }
}
