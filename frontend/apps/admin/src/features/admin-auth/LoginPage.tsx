import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '@shared/api/http';
import { messages } from '../../i18n';
import { googleSignInUrl, useLogin, useProviders, useSession, useVerifyTwoFactor } from './authApi';

const t = messages.auth;

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return t.invalidCredentials;
    if (error.status === 429) return t.tooManyAttempts;
    if (error.status === 400) return t.invalidForm;
  }
  return t.unexpectedError;
}

function mfaErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return t.mfa.expired;
    if (error.status === 429) return t.tooManyAttempts;
    if (error.status === 400) return t.mfa.invalid;
  }
  return t.unexpectedError;
}

const input =
  'w-full rounded-full border border-white/30 bg-white/5 py-3 pr-12 pl-5 text-white placeholder-white/70 transition-colors outline-none focus:border-accent focus:bg-white/10';
const primary =
  'w-full rounded-full bg-white py-3 font-semibold text-ink transition-colors hover:bg-accent hover:text-white disabled:opacity-60';

/**
 * Sign-in: e-mail and password, or Google; then the authenticator code when two-factor authentication is on.
 */
export function LoginPage() {
  const session = useSession();
  const location = useLocation();
  const [params] = useSearchParams();
  const [mfaStep, setMfaStep] = useState(params.get('step') === '2fa');
  const from = (location.state as { from?: string } | null)?.from ?? '/admin/pages';

  if (session.data) {
    return <Navigate to={from} replace />;
  }

  return (
    <main className="night-sky flex min-h-full items-center justify-center p-6">
      <div className="glass w-full max-w-md rounded-3xl px-8 py-10 sm:px-10">
        {mfaStep ? (
          <TwoFactorStep from={from} onCancel={() => setMfaStep(false)} />
        ) : (
          <PasswordStep from={from} onMfa={() => setMfaStep(true)} error={params.get('error')} />
        )}
        <p className="mt-8 text-center text-sm text-white/80">
          <Link to="/admin" className="font-semibold hover:underline">
            ← {t.backHome}
          </Link>
        </p>
      </div>
    </main>
  );
}

function PasswordStep({
  from,
  onMfa,
  error,
}: {
  from: string;
  onMfa: () => void;
  error: string | null;
}) {
  const login = useLogin();
  const providers = useProviders();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [showForgot, setShowForgot] = useState(false);
  const googleError = error ? t.googleErrors[error] : undefined;

  function submit(event: FormEvent) {
    event.preventDefault();
    login.mutate(
      { email: email.trim(), password, remember },
      {
        onSuccess: (response) => {
          if (response.mfaRequired) onMfa();
          else void navigate(from, { replace: true });
        },
      },
    );
  }

  return (
    <form onSubmit={submit} aria-labelledby="login-title" className="space-y-6">
      <h1 id="login-title" className="text-center text-4xl font-extrabold text-white">
        {t.title}
      </h1>
      <Field icon={<UserIcon />}>
        <input
          type="email"
          aria-label={t.email}
          placeholder={t.email}
          autoComplete="username"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={input}
        />
      </Field>
      <Field icon={<LockIcon />}>
        <input
          type="password"
          aria-label={t.password}
          placeholder={t.password}
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={input}
        />
      </Field>
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-white">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={remember}
            onChange={(event) => setRemember(event.target.checked)}
            className="accent-accent h-4 w-4"
          />
          {t.remember}
        </label>
        <button
          type="button"
          onClick={() => setShowForgot(!showForgot)}
          aria-expanded={showForgot}
          className="font-semibold hover:underline"
        >
          {t.forgot}
        </button>
      </div>
      {showForgot && (
        <p className="rounded-2xl bg-white/10 p-4 text-sm text-white/85">{t.forgotHelp}</p>
      )}
      {(login.isError || googleError) && (
        <p role="alert" className="rounded-2xl bg-red-500/20 px-4 py-3 text-sm text-red-100">
          {login.isError ? errorMessage(login.error) : googleError}
        </p>
      )}
      <button type="submit" disabled={login.isPending} className={primary}>
        {login.isPending ? t.submitting : t.submit}
      </button>
      {providers.data?.google && (
        <>
          <div className="flex items-center gap-3 text-xs tracking-widest text-white/60 uppercase">
            <span className="h-px flex-1 bg-white/20" />
            {t.or}
            <span className="h-px flex-1 bg-white/20" />
          </div>
          <a
            href={googleSignInUrl(remember)}
            className="flex w-full items-center justify-center gap-3 rounded-full border border-white/30 py-3 font-semibold text-white transition-colors hover:border-white hover:bg-white/10"
          >
            <GoogleLogo />
            {t.google}
          </a>
        </>
      )}
    </form>
  );
}

function TwoFactorStep({ from, onCancel }: { from: string; onCancel: () => void }) {
  const verify = useVerifyTwoFactor();
  const navigate = useNavigate();
  const [code, setCode] = useState('');

  function submit(event: FormEvent) {
    event.preventDefault();
    verify.mutate(code.trim(), { onSuccess: () => void navigate(from, { replace: true }) });
  }

  return (
    <form onSubmit={submit} aria-labelledby="mfa-title" className="space-y-6 text-white">
      <h1 id="mfa-title" className="text-center text-3xl font-extrabold">
        {t.mfa.title}
      </h1>
      <p className="text-center text-sm text-white/80">{t.mfa.intro}</p>
      <Field icon={<ShieldIcon />}>
        <input
          aria-label={t.mfa.code}
          placeholder="123 456"
          autoComplete="one-time-code"
          autoFocus
          required
          maxLength={20}
          value={code}
          onChange={(event) => setCode(event.target.value)}
          className={`${input} text-center text-xl tracking-[0.3em]`}
        />
      </Field>
      <p className="text-center text-xs text-white/65">{t.mfa.recoveryHint}</p>
      {verify.isError && (
        <p role="alert" className="rounded-2xl bg-red-500/20 px-4 py-3 text-sm text-red-100">
          {mfaErrorMessage(verify.error)}
        </p>
      )}
      <button type="submit" disabled={verify.isPending} className={primary}>
        {verify.isPending ? t.mfa.submitting : t.mfa.submit}
      </button>
      <button
        type="button"
        onClick={onCancel}
        className="w-full text-center text-sm font-semibold text-white/80 hover:underline"
      >
        {t.mfa.cancel}
      </button>
    </form>
  );
}

function Field({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div className="relative">
      {children}
      <span className="pointer-events-none absolute top-1/2 right-5 -translate-y-1/2 text-white">
        {icon}
      </span>
    </div>
  );
}

const iconProps = { width: 20, height: 20, viewBox: '0 0 24 24', 'aria-hidden': true };

function UserIcon() {
  return (
    <svg {...iconProps} fill="currentColor">
      <circle cx="12" cy="7.5" r="4.5" />
      <path d="M3 21c0-4.4 4-7.5 9-7.5s9 3.1 9 7.5z" />
    </svg>
  );
}

function LockIcon() {
  return (
    <svg {...iconProps} fill="currentColor">
      <path d="M7 10V7a5 5 0 0110 0v3h1a1 1 0 011 1v10a1 1 0 01-1 1H6a1 1 0 01-1-1V11a1 1 0 011-1zm2 0h6V7a3 3 0 00-6 0z" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg {...iconProps} fill="currentColor">
      <path d="M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5z" />
    </svg>
  );
}

/** Google's "G" mark, shown next to "Continuer avec Google" as Google's sign-in branding asks. */
function GoogleLogo() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
      <path
        fill="#FFC107"
        d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"
      />
      <path
        fill="#FF3D00"
        d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"
      />
      <path
        fill="#4CAF50"
        d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"
      />
      <path
        fill="#1976D2"
        d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"
      />
    </svg>
  );
}
