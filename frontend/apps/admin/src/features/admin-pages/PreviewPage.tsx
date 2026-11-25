import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { messages } from '../../i18n';
import { BookViewer } from '@shared/book/BookViewer';
import { useSettings } from '../settings/settingsApi';
import { pagesApi, useBookStructure } from './pagesApi';

/** "Aperçu": the public rendering of one page, drafts included, for the administrator only. */
export function PreviewPage() {
  const { id } = useParams<{ id: string }>();
  const page = useQuery({
    queryKey: ['admin', 'preview', id],
    queryFn: () => pagesApi.get(id!),
    enabled: !!id,
  });
  const settings = useSettings();
  const structure = useBookStructure();

  if (page.isPending || settings.isPending)
    return (
      <p role="status" className="p-8 text-white">
        {messages.common.loading}
      </p>
    );
  if (!page.data || !settings.data)
    return (
      <p role="alert" className="p-8 text-white">
        {messages.common.error}
      </p>
    );

  const book = {
    settings: {
      title: settings.data.title,
      pageWidth: settings.data.pageWidth,
      pageHeight: settings.data.pageHeight,
      backgroundColor: settings.data.backgroundColor,
      backgroundUrl: settings.data.backgroundUrl,
      privacyText: settings.data.privacyText,
    },
    pages: [
      {
        id: page.data.id,
        slug: page.data.slug,
        title: page.data.title,
        content: page.data.content,
      },
    ],
    media: page.data.media,
    toc: structure.data?.toc ?? [],
  };

  return (
    <div className="flex h-full flex-col">
      <p className="bg-accent px-4 py-2 text-center text-sm font-semibold text-white">
        {messages.preview.banner}
      </p>
      <div className="min-h-0 flex-1">
        <BookViewer book={book} initialIndex={0} syncUrl={false} track={false} />
      </div>
    </div>
  );
}
