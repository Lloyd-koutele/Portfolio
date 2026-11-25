package com.portfolio.auth.internal;

import com.portfolio.auth.AdminPrincipal;
import com.portfolio.common.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController
{
    static final String LOGIN_PAGE = "/admin/login";
    static final String HOME_AFTER_LOGIN = "/admin/pages";

    private final AuthService authService;
    private final SessionCookies cookies;
    private final JwtService jwtService;
    private final ClientIpResolver clientIpResolver;
    private final GoogleSignIn google;

    AuthController(AuthService authService, SessionCookies cookies, JwtService jwtService,
        ClientIpResolver clientIpResolver, GoogleSignIn google)
    {
        this.authService = authService;
        this.cookies = cookies;
        this.jwtService = jwtService;
        this.clientIpResolver = clientIpResolver;
        this.google = google;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request)
    {
        AuthService.LoginResult result = authService.login(body.email(), body.password(),
            clientIpResolver.resolve(request), body.rememberMe());
        return ResponseEntity.ok().headers(cookiesFor(result)).body(LoginResponse.of(result));
    }

    /** Second step: the authenticator code, after the password or Google. */
    @PostMapping("/2fa/verify")
    ResponseEntity<LoginResponse> verifyTwoFactor(@Valid @RequestBody CodeRequest body, HttpServletRequest request)
    {
        AuthService.LoginResult result = authService.verifyTwoFactor(cookies.readMfa(request).orElse(null),
            body.code(), clientIpResolver.resolve(request));
        return ResponseEntity.ok().headers(cookiesFor(result)).body(LoginResponse.of(result));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout()
    {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        headers.add(HttpHeaders.SET_COOKIE, cookies.clearMfa().toString());
        return ResponseEntity.noContent().headers(headers).build();
    }

    @GetMapping("/me")
    AdminResponse me(@AuthenticationPrincipal AdminPrincipal principal)
    {
        if (principal == null)
        {
            throw new NotAuthenticatedException();
        }
        return authService.currentAdmin(principal).map(AdminResponse::from)
            .orElseThrow(NotAuthenticatedException::new);
    }

    /** Sign-in methods offered by the login page. */
    @GetMapping("/providers")
    Map<String, Boolean> providers()
    {
        return Map.of("google", google.enabled());
    }

    /**
     * Lets the client obtain the {@code XSRF-TOKEN} cookie before its first state-changing request.
     */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf()
    {
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Google

    @GetMapping("/google")
    ResponseEntity<Void> startGoogle(@RequestParam(defaultValue = "false") boolean remember)
    {
        if (!google.enabled())
        {
            return redirect(LOGIN_PAGE + "?error=google_disabled", new HttpHeaders());
        }
        GoogleSignIn.Start start = google.start(remember);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
            cookies.createGoogleBinding(start.binding(), GoogleSignIn.FLOW_LIFETIME).toString());
        return redirect(start.authorizationUrl(), headers);
    }

    /** Google's redirect URI: may live on another host (such as localhost), hence the absolute redirect. */
    @GetMapping("/google/callback")
    ResponseEntity<Void> googleCallback(@RequestParam(required = false) String state,
        @RequestParam(required = false) String code)
    {
        String base = google.adminBase();
        return google.callback(state, code)
            .map(ticket -> redirect(base + "/api/auth/google/complete?ticket=" + ticket, new HttpHeaders()))
            .orElseGet(() -> redirect(base + LOGIN_PAGE + "?error=google_failed", new HttpHeaders()));
    }

    @GetMapping("/google/complete")
    ResponseEntity<Void> completeGoogle(@RequestParam(required = false) String ticket, HttpServletRequest request)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookies.clearGoogleBinding().toString());
        GoogleSignIn.Completed completed = google.complete(ticket, cookies.readGoogleBinding(request).orElse(null))
            .orElse(null);
        if (completed == null)
        {
            return redirect(LOGIN_PAGE + "?error=google_failed", headers);
        }
        AuthService.LoginResult result = authService.loginWithGoogle(completed.email(), completed.remember())
            .orElse(null);
        if (result == null)
        {
            return redirect(LOGIN_PAGE + "?error=google_account", headers);
        }
        headers.addAll(cookiesFor(result));
        return redirect(result.mfaRequired() ? LOGIN_PAGE + "?step=2fa" : HOME_AFTER_LOGIN, headers);
    }

    private HttpHeaders cookiesFor(AuthService.LoginResult result)
    {
        HttpHeaders headers = new HttpHeaders();
        if (result.mfaRequired())
        {
            headers.add(HttpHeaders.SET_COOKIE,
                cookies.createMfa(result.mfaToken(), jwtService.mfaLifetime()).toString());
        }
        else
        {
            headers.add(HttpHeaders.SET_COOKIE, cookies.create(result.token(), result.lifetime()).toString());
            headers.add(HttpHeaders.SET_COOKIE, cookies.clearMfa().toString());
        }
        return headers;
    }

    private static ResponseEntity<Void> redirect(String location, HttpHeaders headers)
    {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).headers(headers).build();
    }
}
