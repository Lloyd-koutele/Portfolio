package com.portfolio.auth.internal;

import com.portfolio.auth.AdminPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two-factor authentication settings of the signed-in administrator.
 */
@RestController
@RequestMapping("/api/admin/security/2fa")
class TwoFactorController
{
    private final TwoFactorService service;

    TwoFactorController(TwoFactorService service)
    {
        this.service = service;
    }

    @GetMapping
    TwoFactorService.Status status(@AuthenticationPrincipal AdminPrincipal admin)
    {
        return service.status(admin.id());
    }

    @PostMapping("/setup")
    TwoFactorService.Setup setup(@AuthenticationPrincipal AdminPrincipal admin)
    {
        return service.setup(admin.id());
    }

    @PostMapping("/enable")
    RecoveryCodesResponse enable(@AuthenticationPrincipal AdminPrincipal admin, @Valid @RequestBody CodeRequest body)
    {
        return new RecoveryCodesResponse(service.enable(admin.id(), body.code()));
    }

    @PostMapping("/disable")
    ResponseEntity<Void> disable(@AuthenticationPrincipal AdminPrincipal admin, @Valid @RequestBody CodeRequest body)
    {
        service.disable(admin.id(), body.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes")
    RecoveryCodesResponse regenerate(@AuthenticationPrincipal AdminPrincipal admin,
        @Valid @RequestBody CodeRequest body)
    {
        return new RecoveryCodesResponse(service.regenerateRecoveryCodes(admin.id(), body.code()));
    }

    record RecoveryCodesResponse(List<String> recoveryCodes)
    {
    }
}
