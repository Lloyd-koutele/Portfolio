import QRCode from 'qrcode';
import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '@shared/api/http';
import { Button } from '@shared/ui/Button';
import { messages } from '../../i18n';
import {
  useTwoFactorActions,
  useTwoFactorStatus,
  type TwoFactorSetup,
} from '../security/securityApi';

const t = messages.security;
const field =
  'focus:border-accent mt-1 w-40 border border-neutral-300 bg-white px-3 py-2 tracking-widest';

function failure(error: unknown): string {
  return error instanceof ApiError && error.status === 400 ? t.invalidCode : t.error;
}

/** Réglages → two-factor authentication: set-up with a QR code, recovery codes, deactivation. */
export function SecuritySection() {
  const status = useTwoFactorStatus();
  const actions = useTwoFactorActions();
  const [setup, setSetup] = useState<TwoFactorSetup | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);

  return (
    <section className="space-y-4 bg-white p-6 shadow-sm" aria-labelledby="security-title">
      <h2 id="security-title" className="text-xl font-bold">
        {t.title}
      </h2>
      {codes ? (
        <RecoveryCodes codes={codes} onDone={() => setCodes(null)} />
      ) : setup ? (
        <SetupStep
          setup={setup}
          onCancel={() => setSetup(null)}
          onEnabled={(recovery) => {
            setSetup(null);
            setCodes(recovery);
          }}
        />
      ) : status.data?.enabled ? (
        <EnabledPanel left={status.data.recoveryCodesLeft} onCodes={setCodes} />
      ) : (
        status.data && (
          <>
            <p className="text-sm text-neutral-700">{t.off}</p>
            <Button
              disabled={actions.setup.isPending}
              onClick={() => actions.setup.mutate(undefined, { onSuccess: setSetup })}
            >
              {t.start}
            </Button>
            {actions.setup.isError && (
              <p role="alert" className="text-sm text-red-700">
                {t.error}
              </p>
            )}
          </>
        )
      )}
    </section>
  );
}

function SetupStep({
  setup,
  onCancel,
  onEnabled,
}: {
  setup: TwoFactorSetup;
  onCancel: () => void;
  onEnabled: (codes: string[]) => void;
}) {
  const { enable } = useTwoFactorActions();
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState('');

  useEffect(() => {
    let active = true;
    // SVG output: no canvas needed, shown as a data: image (allowed by the admin CSP).
    QRCode.toString(setup.otpauthUri, { type: 'svg', margin: 1 })
      .then((svg) => {
        if (active) setQr(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`);
      })
      .catch(() => {
        if (active) setQr(null);
      });
    return () => {
      active = false;
    };
  }, [setup.otpauthUri]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    enable.mutate(code.trim(), { onSuccess: (data) => onEnabled(data.recoveryCodes) });
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-sm text-neutral-700">{t.scan}</p>
      {qr && <img src={qr} alt={t.qrAlt} width={200} height={200} className="border" />}
      <p className="text-sm text-neutral-700">
        {t.manual} <code className="bg-neutral-100 px-2 py-1 break-all">{setup.secret}</code>
      </p>
      <label className="block">
        <span className="text-sm font-medium">{t.code}</span>
        <br />
        <input
          className={field}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          required
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
      </label>
      {enable.isError && (
        <p role="alert" className="text-sm text-red-700">
          {failure(enable.error)}
        </p>
      )}
      <div className="flex gap-3">
        <Button type="submit" disabled={enable.isPending}>
          {t.confirm}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          {t.cancel}
        </Button>
      </div>
    </form>
  );
}

function EnabledPanel({ left, onCodes }: { left: number; onCodes: (codes: string[]) => void }) {
  const { disable, regenerate } = useTwoFactorActions();
  const [code, setCode] = useState('');
  const error = disable.error ?? regenerate.error;

  return (
    <div className="space-y-4">
      <p className="text-sm text-neutral-700">{t.on(left)}</p>
      <label className="block">
        <span className="text-sm font-medium">{t.codeToConfirm}</span>
        <br />
        <input
          className={field}
          autoComplete="one-time-code"
          maxLength={20}
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {failure(error)}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button
          variant="secondary"
          disabled={!code.trim() || regenerate.isPending}
          onClick={() =>
            regenerate.mutate(code.trim(), { onSuccess: (data) => onCodes(data.recoveryCodes) })
          }
        >
          {t.regenerate}
        </Button>
        <Button
          variant="danger"
          disabled={!code.trim() || disable.isPending}
          onClick={() => disable.mutate(code.trim(), { onSuccess: () => setCode('') })}
        >
          {t.disable}
        </Button>
      </div>
    </div>
  );
}

function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  return (
    <div className="space-y-3">
      <h3 className="font-semibold">{t.recoveryTitle}</h3>
      <p className="text-sm text-neutral-700">{t.recoveryHelp}</p>
      <ul className="grid grid-cols-2 gap-2 font-mono sm:grid-cols-5">
        {codes.map((code) => (
          <li key={code} className="bg-neutral-100 px-2 py-1 text-center">
            {code}
          </li>
        ))}
      </ul>
      <Button onClick={onDone}>{t.recoveryDone}</Button>
    </div>
  );
}
