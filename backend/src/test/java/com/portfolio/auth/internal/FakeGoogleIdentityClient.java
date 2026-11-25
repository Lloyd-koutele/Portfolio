package com.portfolio.auth.internal;

import java.util.Optional;

/**
 * Stands in for Google in integration tests: returns whatever identity the test set, and records the nonce.
 */
public class FakeGoogleIdentityClient implements GoogleIdentityClient
{
    private volatile GoogleIdentity next;
    private volatile String lastCode;

    @Override
    public Optional<GoogleIdentity> exchange(String code, String codeVerifier, String nonce)
    {
        lastCode = code;
        return Optional.ofNullable(next);
    }

    void willReturn(String email, boolean verified)
    {
        next = new GoogleIdentity(email, verified);
    }

    void willFail()
    {
        next = null;
    }

    String lastCode()
    {
        return lastCode;
    }
}
