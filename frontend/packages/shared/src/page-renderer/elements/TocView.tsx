import { useContext } from 'react';
import { messages } from '../../i18n';
import { fontStack } from '../fonts';
import { stopFlipPropagation } from '../stopFlip';
import { TocContext } from '../tocContext';
import type { ElementViewProps } from '../types';

/**
 * Automatic table of contents: one line per project with the number of its first page, recomputed by the server
 * whenever sheets or projects change. Lines open the project in the book.
 */
export function TocView({ element, mode }: ElementViewProps<'toc'>) {
  const { entries, onSelect } = useContext(TocContext);
  const p = element.props;
  const lines = entries.length > 0 || mode === 'public' ? entries : null;
  const rowStyle = {
    display: 'flex',
    alignItems: 'baseline',
    gap: 12,
    width: '100%',
    padding: 0,
    border: 0,
    background: 'transparent',
    color: p.color,
    font: 'inherit',
    textAlign: 'left' as const,
    cursor: mode === 'public' && onSelect ? 'pointer' : 'default',
  };
  return (
    <nav
      aria-label={messages.renderer.tocLabel}
      style={{
        width: '100%',
        height: '100%',
        overflow: 'hidden',
        fontFamily: fontStack(p.fontFamily),
        fontSize: p.fontSize,
        fontWeight: p.fontWeight,
        lineHeight: p.lineHeight,
        color: p.color,
      }}
    >
      {lines === null ? (
        <p style={{ margin: 0, opacity: 0.55, fontStyle: 'italic' }}>
          {messages.renderer.tocPlaceholder}
        </p>
      ) : (
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {lines.map((entry) => {
            const content = (
              <>
                <span
                  style={{
                    flexShrink: 1,
                    minWidth: 0,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {entry.title}
                </span>
                <span
                  aria-hidden="true"
                  style={{
                    flex: 1,
                    minWidth: 16,
                    borderBottom: p.leaders
                      ? `${Math.max(1, p.fontSize / 10)}px dotted ${p.accentColor}`
                      : undefined,
                    opacity: 0.6,
                    transform: `translateY(-${p.fontSize / 4}px)`,
                  }}
                />
                <span
                  style={{
                    color: p.accentColor,
                    fontWeight: 700,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {entry.page}
                </span>
              </>
            );
            return (
              <li key={entry.projectId}>
                {mode === 'public' && onSelect ? (
                  <button
                    ref={stopFlipPropagation}
                    type="button"
                    style={rowStyle}
                    onClick={() => onSelect(entry)}
                    aria-label={messages.renderer.tocGoTo(entry.title, entry.page)}
                  >
                    {content}
                  </button>
                ) : (
                  <div style={rowStyle}>{content}</div>
                )}
              </li>
            );
          })}
        </ol>
      )}
    </nav>
  );
}
