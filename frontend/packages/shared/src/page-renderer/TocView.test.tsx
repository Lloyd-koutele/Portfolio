import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { messages } from '../i18n';
import type { PageContent, TocEntry } from '../types/page';
import { PageRenderer } from './PageRenderer';
import { TocContext } from './tocContext';

const content: PageContent = {
  schemaVersion: 1,
  background: { type: 'color', value: '#ffffff' },
  elements: [
    {
      id: '00000000-0000-4000-8000-000000000001',
      type: 'toc',
      x: 50,
      y: 200,
      width: 700,
      height: 600,
      rotation: 0,
      zIndex: 1,
      opacity: 1,
      locked: false,
      props: {
        fontFamily: 'Inter',
        fontSize: 22,
        fontWeight: 600,
        color: '#111111',
        accentColor: '#ee7d1f',
        lineHeight: 2,
        leaders: true,
      },
    },
  ],
};

const entries: TocEntry[] = [
  { projectId: 'a', title: 'Plateforme d’archivage', page: 4, slug: 'archivage' },
  { projectId: 'b', title: 'Infrastructure', page: 7, slug: 'infra' },
];

function renderToc(
  mode: 'public' | 'editor',
  state: { entries: TocEntry[]; onSelect?: (e: TocEntry) => void },
) {
  return render(
    <TocContext.Provider value={state}>
      <PageRenderer content={content} media={{}} width={800} height={1000} scale={1} mode={mode} />
    </TocContext.Provider>,
  );
}

describe('automatic table of contents', () => {
  it('lists each project with its first page number', () => {
    renderToc('public', { entries });
    expect(screen.getByText('Plateforme d’archivage')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('opens the project when a line is clicked in the book', () => {
    const onSelect = vi.fn();
    renderToc('public', { entries, onSelect });
    fireEvent.click(
      screen.getByRole('button', { name: messages.renderer.tocGoTo('Infrastructure', 7) }),
    );
    expect(onSelect).toHaveBeenCalledWith(entries[1]);
  });

  it('is inert in the editor and explains itself while there is no project', () => {
    const { unmount } = renderToc('editor', { entries });
    expect(screen.queryByRole('button')).toBeNull();
    unmount();
    renderToc('editor', { entries: [] });
    expect(screen.getByText(messages.renderer.tocPlaceholder)).toBeInTheDocument();
  });
});
