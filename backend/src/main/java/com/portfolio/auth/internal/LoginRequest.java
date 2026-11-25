package com.portfolio.auth.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param remember "Se souvenir de moi": a longer session (30 days instead of 8 hours)
 */
record LoginRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String password,
    Boolean remember)
{
    boolean rememberMe()
    {
        return Boolean.TRUE.equals(remember);
    }

    @Override
    public String toString()
    {
        return "LoginRequest[email=" + email + ", remember=" + remember + "]";
    }
}
