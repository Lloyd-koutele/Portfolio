package com.portfolio.auth.internal;

import java.util.Optional;

/**
 * Exchanges the authorisation code returned by Google for the signed-in identity (checked ID token).
 */
interface GoogleIdentityClient
{
    /**
     * @return the verified identity, or empty when Google refuses the code or the ID token is not valid
     */
    Optional<GoogleIdentity> exchange(String code, String codeVerifier, String nonce);

    record GoogleIdentity(String email, boolean emailVerified)
    {
    }
}
