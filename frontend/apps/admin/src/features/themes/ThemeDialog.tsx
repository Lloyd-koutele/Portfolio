import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { ApiError } from '@shared/api/http';
import { PageRenderer } from '@shared/page-renderer/PageRenderer';
import { TocContext } from '@shared/page-renderer/tocContext';
import { applyTheme, BOOK_THEMES, type BookTheme } from '@shared/themes/bookThemes';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { messages } from '../../i18n';
import { pagesApi, useBookStructure } from '../admin-pages/pagesApi';
import { useSettings } from '../settings/settingsApi';
import { applyBookTheme, refreshAfterTheme, themeHistoryKey, type ThemeHistory } from './themeApi';
import { ThemeHistoryBar } from './ThemeHistoryBar';
import { useThumbnailStudio } from './useThumbnailStudio';

const t = messages.themes;
const PREVIEW_WIDTH = 150;

type Step =
  | { kind: 'choose' }
  | { kind: 'confirm'; theme: BookTheme }
  | { kind: 'working'; theme: BookTheme; done: number; total: number }
  | { kind: 'applied'; theme: BookTheme; history: ThemeHistory }
  | { kind: 'failed'; message: string };

/**
 * The theme gallery: each theme is previewed on the book's cover; one click re-colours every page and the
 * backdrop, and "Revenir en arrière" puts the book back as it was.
 */
export function ThemeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const structure = useBookStructure();
  const settings = useSettings();
  const [step, setStep] = useState<Step>({ kind: 'choose' });
  const width = settings.data?.pageWidth ?? 800;
  const height = settings.data?.pageHeight ?? 1000;
  const toc = useMemo(() => ({ entries: structure.data?.toc ?? [] }), [structure.data]);
  const studio = useThumbnailStudio(width, height, toc);

  const coverId = structure.data?.start[0]?.id;
  const cover = useQuery({
    queryKey: ['admin', 'theme-preview', coverId],
    queryFn: () => pagesApi.get(coverId!),
    enabled: open && !!coverId,
  });
  const pageCount =
    (structure.data?.start.length ?? 0) +
    (structure.data?.end.length ?? 0) +
    (structure.data?.projects.reduce((sum, project) => sum + project.sheets.length, 0) ?? 0);

  const close = () => {
    if (step.kind === 'working') return;
    setStep({ kind: 'choose' });
    onClose();
  };

  const apply = async (theme: BookTheme) => {
    setStep({ kind: 'working', theme, done: 0, total: pageCount });
    try {
      const history = await applyBookTheme(theme, studio.render, (done, total) =>
        setStep({ kind: 'working', theme, done, total }),
      );
      queryClient.setQueryData(themeHistoryKey, history);
      refreshAfterTheme(queryClient);
      setStep({ kind: 'applied', theme, history });
    } catch (error) {
      refreshAfterTheme(queryClient);
      const changed =
        error instanceof ApiError &&
        error.status === 409 &&
        (error.problem as { code?: string }).code === 'BOOK_CHANGED';
      setStep({ kind: 'failed', message: changed ? t.bookChanged : t.error });
    }
  };

  return (
    <Dialog open={open} title={t.title} onClose={close} wide>
      <p className="mb-4 text-sm text-neutral-600">{t.intro}</p>

      {step.kind === 'working' && (
        <p role="status" className="bg-neutral-100 px-4 py-3 text-sm">
          {step.done < step.total ? t.preparing(step.done, step.total) : t.saving}
        </p>
      )}

      {step.kind === 'applied' && (
        <p role="status" className="mb-4 bg-green-50 px-4 py-3 text-sm text-green-900">
          {t.applied(step.theme.name, step.history.pages)}
        </p>
      )}

      {step.kind === 'failed' && (
        <p role="alert" className="mb-4 bg-red-50 px-4 py-3 text-sm text-red-800">
          {step.message}
        </p>
      )}

      {step.kind === 'confirm' && (
        <div className="mb-4 border border-neutral-300 bg-neutral-50 p-4 text-sm">
          <p>{t.confirm(pageCount)}</p>
          <div className="mt-3 flex gap-2">
            <Button onClick={() => void apply(step.theme)}>{t.confirmApply}</Button>
            <Button variant="secondary" onClick={() => setStep({ kind: 'choose' })}>
              {t.cancel}
            </Button>
          </div>
        </div>
      )}

      {step.kind !== 'working' && (
        <TocContext.Provider value={toc}>
          <ul className="grid max-h-[55vh] grid-cols-2 gap-3 overflow-y-auto sm:grid-cols-4">
            {BOOK_THEMES.map((theme) => {
              const selected = step.kind === 'confirm' && step.theme.id === theme.id;
              return (
                <li key={theme.id}>
                  <button
                    type="button"
                    onClick={() => setStep({ kind: 'confirm', theme })}
                    aria-label={t.apply(theme.name)}
                    aria-pressed={selected}
                    className={`w-full border-2 p-1 text-left transition-colors ${selected ? 'border-accent' : 'hover:border-ink border-transparent'}`}
                  >
                    <div
                      className="flex justify-center p-2"
                      style={{ backgroundColor: theme.backdrop }}
                      aria-label={t.preview(theme.name)}
                      role="img"
                    >
                      {cover.data ? (
                        <PageRenderer
                          content={applyTheme(cover.data.content, theme)}
                          media={cover.data.media}
                          width={width}
                          height={height}
                          scale={PREVIEW_WIDTH / width}
                          mode="thumbnail"
                        />
                      ) : (
                        <span
                          className="block"
                          style={{
                            width: PREVIEW_WIDTH,
                            height: (PREVIEW_WIDTH * height) / width,
                            backgroundColor: theme.page,
                          }}
                        />
                      )}
                    </div>
                    <span className="mt-1 flex gap-1" aria-hidden="true">
                      {[theme.page, theme.heading, theme.accent, theme.surface].map((color) => (
                        <span
                          key={color}
                          className="h-3 w-3 border border-neutral-300"
                          style={{ backgroundColor: color }}
                        />
                      ))}
                    </span>
                    <span className="mt-1 block font-semibold">{theme.name}</span>
                    <span className="block text-xs text-neutral-600">{theme.description}</span>
                    <span className="block text-xs text-neutral-500">
                      {t.fonts(theme.headingFont, theme.bodyFont)}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </TocContext.Provider>
      )}

      {step.kind !== 'working' && (
        <div className="mt-4">
          <ThemeHistoryBar onReverted={() => setStep({ kind: 'choose' })} />
        </div>
      )}

      {studio.studio}
    </Dialog>
  );
}
