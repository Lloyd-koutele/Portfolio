import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { findTheme } from '@shared/themes/bookThemes';
import { Button } from '@shared/ui/Button';
import { Undo } from '@shared/ui/icons';
import { messages } from '../../i18n';
import { refreshAfterTheme, revertBookTheme, themeHistoryKey, useThemeHistory } from './themeApi';

const t = messages.themes;

const dateFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' });

/**
 * The last theme applied, and "Revenir en arrière": the book goes back to how it was before that theme. When
 * pages were edited since, their titles are shown and a second click is asked for.
 */
export function ThemeHistoryBar({ onReverted }: { onReverted?: () => void }) {
  const queryClient = useQueryClient();
  const history = useThemeHistory();
  const [confirming, setConfirming] = useState(false);
  const [done, setDone] = useState(false);
  const revert = useMutation({
    mutationFn: revertBookTheme,
    onSuccess: (next) => {
      queryClient.setQueryData(themeHistoryKey, next);
      refreshAfterTheme(queryClient);
      setConfirming(false);
      setDone(true);
      onReverted?.();
    },
    onError: () => refreshAfterTheme(queryClient),
  });

  const data = history.data;
  if (!data?.available || data.id === null) {
    return done ? (
      <p role="status" className="bg-green-50 px-4 py-3 text-sm text-green-900">
        {t.reverted}
      </p>
    ) : null;
  }
  const id = data.id;
  const name = findTheme(data.themeId)?.name ?? t.unknownTheme;
  const modified = data.modifiedSince;

  const onClick = () => {
    setDone(false);
    if (modified.length > 0 && !confirming) setConfirming(true);
    else revert.mutate(id);
  };

  return (
    <div className="flex flex-wrap items-center gap-3 border border-neutral-300 bg-white px-4 py-3 text-sm">
      <p className="flex-1">
        {t.last(name, data.appliedAt ? dateFormat.format(new Date(data.appliedAt)) : '')}
        {confirming && (
          <span className="mt-1 block text-amber-800">{t.modifiedSince(modified.join(', '))}</span>
        )}
        {done && (
          <span role="status" className="mt-1 block text-green-800">
            {t.reverted}
          </span>
        )}
        {revert.isError && (
          <span role="alert" className="mt-1 block text-red-700">
            {t.revertError}
          </span>
        )}
      </p>
      <Button variant="secondary" onClick={onClick} disabled={revert.isPending}>
        <Undo size={16} />
        {revert.isPending ? t.reverting : confirming ? t.confirmRevert : t.revert}
      </Button>
    </div>
  );
}
