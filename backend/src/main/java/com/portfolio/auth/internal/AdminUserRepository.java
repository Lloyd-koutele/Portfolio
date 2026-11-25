package com.portfolio.auth.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminUserRepository extends JpaRepository<AdminUser, UUID>
{
    Optional<AdminUser> findByEmail(String email);
}
