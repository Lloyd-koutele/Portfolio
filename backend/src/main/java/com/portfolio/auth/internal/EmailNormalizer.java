package com.portfolio.auth.internal;

import java.util.Locale;

final class EmailNormalizer
{
    private EmailNormalizer()
    {
    }

    static String normalize(String email)
    {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
