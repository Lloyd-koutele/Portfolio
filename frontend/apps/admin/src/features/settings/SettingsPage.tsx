import { useState, type FormEvent } from 'react';
import { ApiError } from '@shared/api/http';
import { messages } from '../../i18n';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { MediaGrid } from '../media-library/MediaGrid';
import { useMediaLibrary } from '../media-library/mediaApi';
import { CvSection } from './CvSection';
import { ProfileSection } from './ProfileSection';
import { SecuritySection } from './SecuritySection';
import {
  useSaveSettings,
  useSettings,
  type IpMode,
  type SettingsBody,
  type SiteSettings,
} from './settingsApi';

const t = messages.settings;
const field = 'focus:border-accent mt-1 w-full border border-neutral-300 bg-white px-3 py-2';

export function SettingsPage() {
  const settings = useSettings();
  if (!settings.data) {
    return (
      <p className="p-6" role="status">
        {messages.common.loading}
      </p>
    );
  }
  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6 pb-0">
      <SettingsForm initial={settings.data} />
      <ProfileSection />
      <CvSection />
      <SecuritySection />
    </div>
  );
}

function SettingsForm({ initial }: { initial: SiteSettings }) {
  const save = useSaveSettings();
  const library = useMediaLibrary('image');
  const [form, setForm] = useState<SettingsBody>(() => {
    const { backgroundUrl: _url, updatedAt: _updatedAt, ...rest } = initial;
    return rest;
  });
  const [backgroundUrl, setBackgroundUrl] = useState<string | null>(initial.backgroundUrl);
  const [picking, setPicking] = useState(false);
  const [done, setDone] = useState(false);

  const update = <K extends keyof SettingsBody>(key: K, value: SettingsBody[K]) => {
    setDone(false);
    setForm({ ...form, [key]: value });
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate(form, { onSuccess: () => setDone(true) });
  };

  const fieldErrors = save.error instanceof ApiError ? (save.error.problem.errors ?? {}) : {};

  return (
    <form onSubmit={submit} className="space-y-6">
      <h1 className="text-3xl font-bold">
        <span className="text-accent">■</span> {t.title}
      </h1>

      <section className="space-y-4 bg-white p-6 shadow-sm">
        <label className="block">
          <span className="text-sm font-medium">{t.siteTitle}</span>
          <input
            className={field}
            value={form.title}
            maxLength={120}
            required
            onChange={(e) => update('title', e.target.value)}
          />
        </label>
        <fieldset>
          <legend className="text-sm font-medium">{t.pageFormat}</legend>
          <div className="mt-1 flex gap-3">
            <label className="flex-1">
              <span className="text-xs text-neutral-600">{t.pageWidth}</span>
              <input
                type="number"
                min={100}
                max={4000}
                className={field}
                value={form.pageWidth}
                onChange={(e) => update('pageWidth', Number(e.target.value))}
              />
            </label>
            <label className="flex-1">
              <span className="text-xs text-neutral-600">{t.pageHeight}</span>
              <input
                type="number"
                min={100}
                max={4000}
                className={field}
                value={form.pageHeight}
                onChange={(e) => update('pageHeight', Number(e.target.value))}
              />
            </label>
          </div>
        </fieldset>
        <label className="flex items-center gap-3">
          <input
            type="color"
            value={form.backgroundColor}
            onChange={(e) => update('backgroundColor', e.target.value)}
            className="h-10 w-14"
          />
          <span className="text-sm font-medium">{t.backgroundColor}</span>
          {fieldErrors.backgroundColor && (
            <span className="text-sm text-red-700">{fieldErrors.backgroundColor}</span>
          )}
        </label>
        <div>
          <span className="text-sm font-medium">{t.backgroundImage}</span>
          <div className="mt-2 flex items-center gap-3">
            {backgroundUrl && <img src={backgroundUrl} alt="" className="h-20 w-32 object-cover" />}
            <Button variant="secondary" onClick={() => setPicking(true)}>
              {t.chooseBackground}
            </Button>
            {form.backgroundMediaId && (
              <Button
                variant="ghost"
                onClick={() => {
                  update('backgroundMediaId', null);
                  setBackgroundUrl(null);
                }}
              >
                {t.removeBackground}
              </Button>
            )}
          </div>
        </div>
      </section>

      <section className="space-y-3 bg-white p-6 shadow-sm">
        <fieldset>
          <legend className="text-sm font-medium">{t.ipMode}</legend>
          {(['MASKED', 'FULL', 'HASHED'] as IpMode[]).map((mode) => (
            <label key={mode} className="mt-2 flex items-start gap-2 text-sm">
              <input
                type="radio"
                name="ipMode"
                checked={form.ipMode === mode}
                onChange={() => update('ipMode', mode)}
                className="mt-1"
              />
              <span>{t.ipModes[mode]}</span>
            </label>
          ))}
        </fieldset>
        <label className="block">
          <span className="text-sm font-medium">{t.privacyText}</span>
          <textarea
            className={`${field} min-h-40`}
            value={form.privacyText}
            maxLength={20000}
            onChange={(e) => update('privacyText', e.target.value)}
            placeholder={messages.book.defaultPrivacy}
          />
          <span className="text-xs text-neutral-500">{t.privacyHint}</span>
        </label>
      </section>

      <div className="flex items-center gap-4">
        <Button type="submit" disabled={save.isPending}>
          {messages.common.save}
        </Button>
        {done && (
          <span role="status" className="text-sm text-green-700">
            {t.saved}
          </span>
        )}
        {save.isError && (
          <span role="alert" className="text-sm text-red-700">
            {t.saveError}
          </span>
        )}
      </div>

      <Dialog open={picking} title={t.chooseBackground} onClose={() => setPicking(false)} wide>
        <div className="max-h-[60vh] overflow-y-auto">
          <MediaGrid
            items={library.data?.pages.flatMap((page) => page.content) ?? []}
            onPick={(item) => {
              update('backgroundMediaId', item.id);
              setBackgroundUrl(item.url);
              setPicking(false);
            }}
            compact
          />
        </div>
      </Dialog>
    </form>
  );
}
