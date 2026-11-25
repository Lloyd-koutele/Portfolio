import { Link } from 'react-router-dom';
import { messages } from '../i18n/index';

export function NotFoundPage() {
  return (
    <main className="text-paper flex h-full flex-col items-center justify-center gap-4">
      <h1 className="text-3xl font-bold">{messages.notFound.title}</h1>
      <Link className="text-accent underline" to="/">
        {messages.notFound.backHome}
      </Link>
    </main>
  );
}
