import { beforeEach, describe, expect, it } from 'vitest';
import { emptyContent } from '@shared/types/page';
import { HISTORY_LIMIT, useEditorStore } from './editorStore';
import { createElement } from './operations';

const store = () => useEditorStore.getState();

beforeEach(() => {
  store().load({
    id: 'p1',
    title: 'Page',
    slug: 'page',
    kind: 'PROJECT_SHEET',
    projectId: 'project-1',
    position: 0,
    content: emptyContent(),
    thumbnailMediaId: null,
    version: 3,
    createdAt: '',
    updatedAt: '',
    media: {},
  });
});

function addText() {
  store().applyChange((draft) => {
    draft.content.elements.push(createElement('text', draft.content, { x: 400, y: 500 }));
  });
}

describe('editorStore', () => {
  it('records each change in the history and undoes / redoes it', () => {
    addText();
    addText();
    expect(store().content.elements).toHaveLength(2);
    expect(store().saveState).toBe('dirty');

    store().undo();
    expect(store().content.elements).toHaveLength(1);
    store().undo();
    expect(store().content.elements).toHaveLength(0);
    store().redo();
    expect(store().content.elements).toHaveLength(1);
  });

  it('keeps at most 100 steps', () => {
    for (let i = 0; i < HISTORY_LIMIT + 20; i += 1) {
      store().applyChange((draft) => void (draft.title = `T${i}`));
    }
    expect(store().past).toHaveLength(HISTORY_LIMIT);
  });

  it('merges quick edits that share a key into one step', () => {
    store().applyChange((draft) => void (draft.title = 'A'), 'title');
    store().applyChange((draft) => void (draft.title = 'AB'), 'title');
    store().applyChange((draft) => void (draft.title = 'ABC'), 'title');
    expect(store().past).toHaveLength(1);
    store().undo();
    expect(store().title).toBe('Page');
  });

  it('ignores edits that change nothing', () => {
    store().applyChange((draft) => void (draft.title = 'Page'));
    expect(store().past).toHaveLength(0);
    expect(store().revision).toBe(0);
  });

  it('turns a whole drag into a single history step', () => {
    addText();
    const id = store().content.elements[0]!.id;
    store().beginGesture();
    for (let x = 0; x < 10; x += 1) {
      store().updateGesture((draft) => void (draft.content.elements[0]!.x = x * 10));
    }
    store().endGesture();
    expect(store().content.elements[0]!.x).toBe(90);
    expect(store().past).toHaveLength(2);
    store().undo();
    expect(store().content.elements.find((e) => e.id === id)!.x).not.toBe(90);
  });

  it('drops deleted elements from the selection', () => {
    addText();
    const id = store().content.elements[0]!.id;
    store().select([id]);
    store().applyChange((draft) => void (draft.content.elements = []));
    expect(store().selection).toEqual([]);
  });

  it('tracks save progress against the revision', () => {
    addText();
    const revision = store().revision;
    store().markSaving();
    addText();
    store().markSaved(revision, {
      version: 4,
      thumbnailMediaId: null,
    });
    expect(store().version).toBe(4);
    expect(store().saveState).toBe('dirty');
    store().markSaved(store().revision, {
      version: 5,
      thumbnailMediaId: 't',
    });
    expect(store().saveState).toBe('saved');
  });
});
