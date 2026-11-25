import { useEffect, useRef, useState } from 'react';
import { messages } from '../../i18n';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { Upload } from '@shared/ui/icons';
import { MediaGrid } from './MediaGrid';
import {
  ACCEPTED_TYPES,
  mediaFiles,
  mediaErrorMessage,
  useDeleteMedia,
  useMediaLibrary,
  useUploadMedia,
  type MediaItem,
} from './mediaApi';

const t = messages.media;

export function MediaLibraryPage() {
  const library = useMediaLibrary();
  const upload = useUploadMedia();
  const remove = useDeleteMedia();
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<MediaItem | null>(null);

  const send = async (files: File[]) => {
    setError(null);
    for (const file of files) {
      try {
        await upload.mutateAsync(file);
      } catch (failure) {
        setError(`${file.name} : ${mediaErrorMessage(failure)}`);
      }
    }
  };

  useEffect(() => {
    const onPaste = (event: ClipboardEvent) => {
      const files = mediaFiles(event.clipboardData?.items);
      if (files.length > 0) {
        event.preventDefault();
        void send(files);
      }
    };
    window.addEventListener('paste', onPaste);
    return () => window.removeEventListener('paste', onPaste);
  });

  const items = library.data?.pages.flatMap((page) => page.content) ?? [];

  return (
    <div
      className="mx-auto max-w-6xl p-6"
      onDragOver={(event) => {
        if (event.dataTransfer.types.includes('Files')) {
          event.preventDefault();
          setDragging(true);
        }
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        void send(mediaFiles(event.dataTransfer.files));
      }}
    >
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">
            <span className="text-accent">■</span> {t.title}
          </h1>
          <p className="mt-1 text-sm text-neutral-600">{t.intro}</p>
        </div>
        <input
          ref={input}
          type="file"
          accept={ACCEPTED_TYPES.join(',')}
          multiple
          hidden
          onChange={(event) => {
            void send(mediaFiles(event.target.files));
            event.target.value = '';
          }}
        />
        <Button onClick={() => input.current?.click()} disabled={upload.isPending}>
          <Upload size={18} /> {upload.isPending ? t.uploading : t.upload}
        </Button>
      </div>
      {error && (
        <p role="alert" className="mb-4 bg-red-50 p-3 text-sm text-red-800">
          {error}
        </p>
      )}
      <div
        className={`min-h-48 border-2 border-dashed p-4 transition-colors ${dragging ? 'border-accent bg-orange-50' : 'border-transparent'}`}
      >
        {dragging && <p className="text-accent mb-3 text-center font-semibold">{t.dropHere}</p>}
        {library.isPending ? (
          <p role="status">{messages.common.loading}</p>
        ) : (
          <MediaGrid items={items} onDelete={setToDelete} />
        )}
        {library.hasNextPage && (
          <div className="mt-4 text-center">
            <Button variant="secondary" onClick={() => void library.fetchNextPage()}>
              {t.loadMore}
            </Button>
          </div>
        )}
      </div>

      <Dialog
        open={toDelete !== null}
        title={t.delete}
        onClose={() => setToDelete(null)}
        actions={
          <>
            <Button variant="secondary" onClick={() => setToDelete(null)}>
              {messages.common.cancel}
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                if (toDelete) {
                  remove.mutate(toDelete.id, {
                    onError: (failure) => setError(mediaErrorMessage(failure)),
                  });
                }
                setToDelete(null);
              }}
            >
              {messages.common.delete}
            </Button>
          </>
        }
      >
        <p>{t.confirmDelete}</p>
        {toDelete && <img src={toDelete.url} alt="" className="mt-3 max-h-48" />}
      </Dialog>
    </div>
  );
}
