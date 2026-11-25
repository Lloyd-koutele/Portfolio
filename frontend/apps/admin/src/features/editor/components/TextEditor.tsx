import { useEffect, useRef } from 'react';
import { messages } from '../../../i18n';
import { fontStack } from '@shared/page-renderer/fonts';
import { elementBoxStyle } from '@shared/page-renderer/elementBox';
import { useEditorStore } from '../store/editorStore';

/** In-place text editing: a textarea laid exactly over the text element, with the same typography. */
export function TextEditor() {
  const editingId = useEditorStore((state) => state.editingTextId);
  const element = useEditorStore((state) =>
    state.content.elements.find((e) => e.id === state.editingTextId),
  );
  const applyChange = useEditorStore((state) => state.applyChange);
  const setEditing = useEditorStore((state) => state.setEditingText);
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!editingId) return;
    const node = ref.current;
    node?.focus();
    node?.select();
  }, [editingId]);

  if (!element || element.type !== 'text') return null;
  const p = element.props;

  return (
    <textarea
      ref={ref}
      aria-label={messages.editor.textEditor}
      value={p.text}
      maxLength={5000}
      onChange={(event) => {
        const value = event.target.value;
        applyChange((draft) => {
          const target = draft.content.elements.find((e) => e.id === element.id);
          if (target?.type === 'text') target.props.text = value;
        }, `text:${element.id}`);
      }}
      onBlur={() => setEditing(null)}
      onKeyDown={(event) => {
        event.stopPropagation();
        if (event.key === 'Escape') {
          event.preventDefault();
          setEditing(null);
        }
      }}
      onPointerDown={(event) => event.stopPropagation()}
      onMouseDown={(event) => event.stopPropagation()}
      style={{
        ...elementBoxStyle(element),
        zIndex: 150_000,
        margin: 0,
        padding: 0,
        border: 'none',
        outline: '2px solid #ee7d1f',
        resize: 'none',
        overflow: 'hidden',
        background: 'rgba(255,255,255,0.6)',
        fontFamily: fontStack(p.fontFamily),
        fontSize: p.fontSize,
        fontWeight: p.fontWeight,
        fontStyle: p.italic ? 'italic' : 'normal',
        textDecoration: p.underline ? 'underline' : 'none',
        textAlign: p.align,
        color: p.color,
        lineHeight: p.lineHeight,
        whiteSpace: 'pre-wrap',
        overflowWrap: 'break-word',
      }}
    />
  );
}
