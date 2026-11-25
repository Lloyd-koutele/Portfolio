/**
 * Authentication: the single administrator account, login / logout, JWT session cookie, CSRF and login
 * rate limiting. Other modules only see {@link com.portfolio.auth.AdminPrincipal}.
 */
@ApplicationModule(displayName = "Authentication")
package com.portfolio.auth;

import org.springframework.modulith.ApplicationModule;
