package com.portfolio.auth;

import java.util.UUID;

/**
 * The authenticated administrator, available as the security principal on authenticated requests.
 *
 * @param id administrator id
 * @param email administrator e-mail
 */
public record AdminPrincipal(UUID id, String email)
{
}
