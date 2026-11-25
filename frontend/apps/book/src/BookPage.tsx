import { useParams } from 'react-router-dom';
import { messages } from '@shared/i18n';
import { Backdrop, BookViewer } from '@shared/book/BookViewer';
import { useBook } from '@shared/book/bookApi';

/** Public portal: "/" opens the book on the cover, "/p/:slug" on the requested page. */
export function BookPage() {
  const { slug } = useParams();
  const book = useBook();

  if (book.isPending) {
    return (
      <Shell>
        <p role="status" className="text-white/80">
          {messages.common.loading}
        </p>
      </Shell>
    );
  }
  if (book.isError) {
    return (
      <Shell>
        <Card text={messages.book.loadError} />
      </Shell>
    );
  }
  if (book.data.pages.length === 0) {
    return (
      <Shell color={book.data.settings.backgroundColor} url={book.data.settings.backgroundUrl}>
        <Card title={book.data.settings.title} text={messages.book.comingSoon} />
      </Shell>
    );
  }
  const requested = slug ? book.data.pages.findIndex((page) => page.slug === slug) : 0;
  return <BookViewer book={book.data} initialIndex={Math.max(0, requested)} />;
}

function Shell({
  children,
  color = '#1f1a17',
  url = null,
}: {
  children: React.ReactNode;
  color?: string;
  url?: string | null;
}) {
  return (
    <main className="relative flex h-full items-center justify-center p-6">
      <Backdrop color={color} url={url} />
      <div className="relative z-10">{children}</div>
    </main>
  );
}

function Card({ title, text }: { title?: string; text: string }) {
  return (
    <div className="bg-paper text-ink w-full max-w-sm p-10 shadow-2xl">
      <div className="bg-accent mb-6 h-2 w-16" />
      <h1 className="text-accent text-4xl leading-tight font-bold">
        {title ?? messages.app.title}
      </h1>
      <p className="mt-4 text-lg">{text}</p>
    </div>
  );
}
