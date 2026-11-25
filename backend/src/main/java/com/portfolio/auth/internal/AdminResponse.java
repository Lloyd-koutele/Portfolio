package com.portfolio.auth.internal;

import com.portfolio.auth.AdminPrincipal;
import java.util.UUID;

record AdminResponse(UUID id, String email)
{
    static AdminResponse from(AdminPrincipal principal)
    {
        return new AdminResponse(principal.id(), principal.email());
    }
}
