package com.portfolio.auth.internal;

import java.util.UUID;

/**
 * Login outcome: the administrator, or {@code mfaRequired} when the authenticator code is still expected.
 */
record LoginResponse(UUID id, String email, boolean mfaRequired)
{
    static LoginResponse of(AuthService.LoginResult result)
    {
        if (result.mfaRequired())
        {
            return new LoginResponse(null, null, true);
        }
        return new LoginResponse(result.admin().id(), result.admin().email(), false);
    }
}
