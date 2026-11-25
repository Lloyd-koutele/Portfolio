package com.portfolio.auth.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A six-digit code from the authenticator app, or a recovery code ({@code xxxx-xxxx}).
 */
record CodeRequest(@NotBlank @Size(max = 20) String code)
{
    @Override
    public String toString()
    {
        return "CodeRequest[]";
    }
}
