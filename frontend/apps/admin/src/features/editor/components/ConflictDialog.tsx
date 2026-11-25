import { messages } from '../../../i18n';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { useEditorStore } from '../store/editorStore';

const t = messages.editor.conflict;

export function ConflictDialog({
  onReload,
  onOverwrite,
}: {
  onReload: () => void;
  onOverwrite: () => void;
}) {
  const open = useEditorStore((state) => state.saveState === 'conflict');
  return (
    <Dialog
      open={open}
      title={t.title}
      onClose={onReload}
      actions={
        <>
          <Button variant="secondary" onClick={onReload}>
            {t.reload}
          </Button>
          <Button variant="danger" onClick={onOverwrite}>
            {t.overwrite}
          </Button>
        </>
      }
    >
      <p>{t.body}</p>
    </Dialog>
  );
}
