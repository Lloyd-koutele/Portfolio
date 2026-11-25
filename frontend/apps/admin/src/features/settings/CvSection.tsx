import { useRef, useState, type ChangeEvent } from 'react';
import { Button } from '@shared/ui/Button';
import { useCvInfo } from '@shared/cv/cvApi';
import { messages } from '../../i18n';
import { CV_MAX_BYTES, useDeleteCv, useUploadCv } from './cvApi';

const t = messages.cv;
const day = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long' });
const size = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });

function formatSize(bytes: number): string {
  return bytes < 1024 * 1024
    ? `${size.format(bytes / 1024)} Ko`
    : `${size.format(bytes / (1024 * 1024))} Mo`;
}

/** Réglages → the CV visitors can download. */
export function CvSection() {
  const info = useCvInfo();
  const upload = useUploadCv();
  const remove = useDeleteCv();
  const input = useRef<HTMLInputElement>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const cv = info.data?.available ? info.data : null;

  const choose = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (file.size > CV_MAX_BYTES) {
      setMessage({ ok: false, text: t.tooBig });
      return;
    }
    if (file.type && file.type !== 'application/pdf') {
      setMessage({ ok: false, text: t.notPdf });
      return;
    }
    setMessage(null);
    upload.mutate(file, {
      onSuccess: () => setMessage({ ok: true, text: t.uploaded }),
      onError: () => setMessage({ ok: false, text: t.error }),
    });
  };

  const drop = () => {
    if (!window.confirm(t.confirmRemove)) return;
    remove.mutate(undefined, { onSuccess: () => setMessage({ ok: true, text: t.removed }) });
  };

  return (
    <section className="space-y-4 bg-white p-6 shadow-sm" aria-labelledby="cv-title">
      <div>
        <h2 id="cv-title" className="text-xl font-bold">
          {t.title}
        </h2>
        <p className="text-sm text-neutral-600">{t.intro}</p>
      </div>
      {cv ? (
        <p className="text-sm">
          <span className="font-medium">{t.current} :</span> {cv.fileName}
          {cv.sizeBytes !== null && ` · ${formatSize(cv.sizeBytes)}`}
          {cv.updatedAt && ` · ${t.updated(day.format(new Date(cv.updatedAt)))}`}
        </p>
      ) : (
        <p className="text-sm text-neutral-600">{t.none}</p>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <input
          ref={input}
          type="file"
          accept="application/pdf,.pdf"
          className="hidden"
          aria-label={cv ? t.replace : t.choose}
          onChange={choose}
        />
        <Button onClick={() => input.current?.click()} disabled={upload.isPending}>
          {upload.isPending ? t.uploading : cv ? t.replace : t.choose}
        </Button>
        {cv && (
          <>
            <a
              href="/api/admin/cv/file"
              target="_blank"
              rel="noopener"
              className="px-4 py-2 text-sm font-semibold hover:underline"
            >
              {t.view}
            </a>
            <Button variant="ghost" onClick={drop} disabled={remove.isPending}>
              {t.remove}
            </Button>
          </>
        )}
      </div>
      <p className="text-xs text-neutral-500">{t.hint}</p>
      {message && (
        <p
          role={message.ok ? 'status' : 'alert'}
          className={`text-sm ${message.ok ? 'text-green-700' : 'text-red-700'}`}
        >
          {message.text}
        </p>
      )}
    </section>
  );
}
