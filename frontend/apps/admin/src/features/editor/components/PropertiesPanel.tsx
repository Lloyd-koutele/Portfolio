import { useState } from 'react';
import { messages } from '../../../i18n';
import { isSafeHref, parseVideoInput } from '@shared/page-renderer/links';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { Copy, Lock, Trash, Unlock } from '@shared/ui/icons';
import { FONT_FAMILIES, type PageElement, type PropsByType } from '@shared/types/page';
import { MediaGrid } from '../../media-library/MediaGrid';
import { useMediaLibrary } from '../../media-library/mediaApi';
import {
  alignSelection,
  arrangeSelection,
  deleteSelection,
  distributeSelection,
  duplicateSelection,
  toggleLock,
} from '../actions';
import { clampCoordinate, clampSize, round } from '../geometry';
import { useEditorStore } from '../store/editorStore';
import { ColorField, NumberField, Section, SelectField, TextField, Toggle } from './fields';

const t = messages.editor.properties;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function useElementPatch(id: string) {
  const applyChange = useEditorStore((state) => state.applyChange);
  return {
    patch: (recipe: (element: PageElement) => void, mergeKey?: string) =>
      applyChange((draft) => {
        const element = draft.content.elements.find((e) => e.id === id);
        if (element) recipe(element);
      }, mergeKey),
    props: <T extends PageElement['type']>(
      type: T,
      values: Partial<PropsByType[T]>,
      mergeKey?: string,
    ) =>
      applyChange((draft) => {
        const element = draft.content.elements.find((e) => e.id === id);
        if (element && element.type === type) Object.assign(element.props, values);
      }, mergeKey),
  };
}

function GeometrySection({ element }: { element: PageElement }) {
  const { patch } = useElementPatch(element.id);
  const locked = element.locked;
  return (
    <Section title={`${t.position} · ${t.size}`}>
      <div className="grid grid-cols-2 gap-2">
        <NumberField
          label={t.x}
          value={element.x}
          disabled={locked}
          onCommit={(v) => patch((e) => void (e.x = clampCoordinate(v)))}
        />
        <NumberField
          label={t.y}
          value={element.y}
          disabled={locked}
          onCommit={(v) => patch((e) => void (e.y = clampCoordinate(v)))}
        />
        <NumberField
          label={t.width}
          value={element.width}
          min={1}
          disabled={locked}
          onCommit={(v) => patch((e) => void (e.width = clampSize(v)))}
        />
        <NumberField
          label={t.height}
          value={element.height}
          min={1}
          disabled={locked}
          onCommit={(v) => patch((e) => void (e.height = clampSize(v)))}
        />
        <NumberField
          label={t.rotation}
          value={element.rotation}
          min={-360}
          max={360}
          disabled={locked}
          onCommit={(v) => patch((e) => void (e.rotation = v))}
        />
        <NumberField
          label={`${t.opacity} (%)`}
          value={Math.round(element.opacity * 100)}
          min={0}
          max={100}
          onCommit={(v) => patch((e) => void (e.opacity = round(v / 100)))}
        />
      </div>
    </Section>
  );
}

function LinkField({
  value,
  onChange,
}: {
  value: string | undefined;
  onChange: (v: string | undefined) => void;
}) {
  const invalid = !!value && !isSafeHref(value);
  return (
    <TextField
      label={t.link}
      value={value ?? ''}
      maxLength={2000}
      placeholder="https://"
      invalid={invalid}
      hint={invalid ? <span className="text-red-700">{t.invalidLink}</span> : undefined}
      onChange={(v) => onChange(v.trim() ? v.trim() : undefined)}
    />
  );
}

function TypeSection({ element }: { element: PageElement }) {
  const { props } = useElementPatch(element.id);
  const key = (name: string) => `${element.id}:${name}`;
  const [videoInput, setVideoInput] = useState('');

  switch (element.type) {
    case 'text': {
      const p = element.props;
      return (
        <Section title={t.text}>
          <TextField
            label={t.text}
            multiline
            maxLength={5000}
            value={p.text}
            onChange={(v) => props('text', { text: v }, key('text'))}
          />
          <SelectField
            label={t.font}
            value={p.fontFamily}
            options={FONT_FAMILIES.map((f) => ({ value: f, label: f }))}
            onChange={(v) => props('text', { fontFamily: v })}
          />
          <div className="grid grid-cols-2 gap-2">
            <NumberField
              label={t.fontSize}
              value={p.fontSize}
              min={4}
              max={400}
              onCommit={(v) => props('text', { fontSize: v })}
            />
            <SelectField
              label={t.fontWeight}
              value={p.fontWeight}
              options={[100, 200, 300, 400, 500, 600, 700, 800, 900].map((w) => ({
                value: w,
                label: String(w),
              }))}
              onChange={(v) => props('text', { fontWeight: v })}
            />
          </div>
          <div className="flex gap-4">
            <Toggle
              label={t.italic}
              checked={p.italic}
              onChange={(v) => props('text', { italic: v })}
            />
            <Toggle
              label={t.underline}
              checked={p.underline}
              onChange={(v) => props('text', { underline: v })}
            />
          </div>
          <SelectField
            label={t.textAlign}
            value={p.align}
            options={[
              { value: 'left', label: t.alignLeft },
              { value: 'center', label: t.alignCenter },
              { value: 'right', label: t.alignRight },
              { value: 'justify', label: t.alignJustify },
            ]}
            onChange={(v) => props('text', { align: v })}
          />
          <ColorField
            label={t.color}
            value={p.color}
            onChange={(v) => props('text', { color: v }, key('color'))}
          />
          <NumberField
            label={t.lineHeight}
            value={p.lineHeight}
            min={0.5}
            max={4}
            step={0.05}
            onCommit={(v) => props('text', { lineHeight: v })}
          />
          <LinkField value={p.href} onChange={(v) => props('text', { href: v }, key('href'))} />
        </Section>
      );
    }
    case 'image': {
      const p = element.props;
      const missingAlt = !p.alt.trim();
      return (
        <Section title={messages.editor.tabs.media}>
          <TextField
            label={t.alt}
            value={p.alt}
            maxLength={500}
            invalid={missingAlt}
            hint={missingAlt ? <span className="text-red-700">{t.altRequired}</span> : undefined}
            onChange={(v) => props('image', { alt: v }, key('alt'))}
          />
          <SelectField
            label={t.fit}
            value={p.fit}
            options={[
              { value: 'cover', label: t.fitCover },
              { value: 'contain', label: t.fitContain },
            ]}
            onChange={(v) => props('image', { fit: v })}
          />
          <NumberField
            label={t.radius}
            value={p.borderRadius}
            min={0}
            max={1000}
            onCommit={(v) => props('image', { borderRadius: v })}
          />
          <LinkField value={p.href} onChange={(v) => props('image', { href: v }, key('href'))} />
        </Section>
      );
    }
    case 'shape': {
      const p = element.props;
      return (
        <Section title={t.shape}>
          <SelectField
            label={t.shape}
            value={p.shape}
            options={(['rect', 'ellipse', 'triangle', 'line'] as const).map((s) => ({
              value: s,
              label: t.shapes[s],
            }))}
            onChange={(v) => props('shape', { shape: v })}
          />
          <ColorField
            label={t.fill}
            value={p.fill}
            allowTransparent
            onChange={(v) => props('shape', { fill: v }, key('fill'))}
          />
          <ColorField
            label={t.stroke}
            value={p.stroke}
            allowTransparent
            onChange={(v) => props('shape', { stroke: v }, key('stroke'))}
          />
          <div className="grid grid-cols-2 gap-2">
            <NumberField
              label={t.strokeWidth}
              value={p.strokeWidth}
              min={0}
              max={100}
              onCommit={(v) => props('shape', { strokeWidth: v })}
            />
            <NumberField
              label={t.radius}
              value={p.borderRadius}
              min={0}
              max={1000}
              onCommit={(v) => props('shape', { borderRadius: v })}
            />
          </div>
        </Section>
      );
    }
    case 'drawing': {
      const p = element.props;
      return (
        <Section title={messages.editor.tools.pen}>
          <ColorField
            label={t.color}
            value={p.fill}
            onChange={(v) => props('drawing', { fill: v }, key('fill'))}
          />
        </Section>
      );
    }
    case 'video': {
      const p = element.props;
      if (p.provider === 'file') {
        return (
          <Section title={messages.editor.tools.video}>
            <p className="text-sm text-neutral-600">{t.videoFile}</p>
          </Section>
        );
      }
      const parsed = videoInput ? parseVideoInput(videoInput) : null;
      return (
        <Section title={messages.editor.tools.video}>
          <TextField
            label={t.video}
            value={
              videoInput ||
              (p.provider === 'youtube'
                ? `https://youtu.be/${p.videoId}`
                : `https://vimeo.com/${p.videoId}`)
            }
            invalid={!!videoInput && !parsed}
            hint={
              videoInput && !parsed ? (
                <span className="text-red-700">{t.invalidVideo}</span>
              ) : undefined
            }
            onChange={(v) => {
              setVideoInput(v);
              const result = parseVideoInput(v);
              if (result) props('video', result, key('video'));
            }}
          />
        </Section>
      );
    }
    case 'toc': {
      const p = element.props;
      return (
        <Section title={t.toc}>
          <p className="text-xs text-neutral-500">{t.tocHint}</p>
          <SelectField
            label={t.font}
            value={p.fontFamily}
            options={FONT_FAMILIES.map((f) => ({ value: f, label: f }))}
            onChange={(v) => props('toc', { fontFamily: v })}
          />
          <div className="grid grid-cols-2 gap-2">
            <NumberField
              label={t.fontSize}
              value={p.fontSize}
              min={8}
              max={120}
              onCommit={(v) => props('toc', { fontSize: v })}
            />
            <SelectField
              label={t.fontWeight}
              value={p.fontWeight}
              options={[100, 200, 300, 400, 500, 600, 700, 800, 900].map((w) => ({
                value: w,
                label: String(w),
              }))}
              onChange={(v) => props('toc', { fontWeight: v })}
            />
          </div>
          <ColorField
            label={t.color}
            value={p.color}
            onChange={(v) => props('toc', { color: v }, key('color'))}
          />
          <ColorField
            label={t.accentColor}
            value={p.accentColor}
            onChange={(v) => props('toc', { accentColor: v }, key('accent'))}
          />
          <NumberField
            label={t.lineHeight}
            value={p.lineHeight}
            min={1}
            max={4}
            step={0.1}
            onCommit={(v) => props('toc', { lineHeight: v })}
          />
          <Toggle
            label={t.leaders}
            checked={p.leaders}
            onChange={(v) => props('toc', { leaders: v })}
          />
        </Section>
      );
    }
    case 'button': {
      const p = element.props;
      return (
        <Section title={messages.editor.tools.button}>
          <TextField
            label={t.label}
            value={p.label}
            maxLength={200}
            onChange={(v) => props('button', { label: v }, key('label'))}
          />
          <TextField
            label={t.link}
            value={p.href}
            maxLength={2000}
            invalid={!isSafeHref(p.href)}
            hint={
              !isSafeHref(p.href) ? (
                <span className="text-red-700">{t.invalidLink}</span>
              ) : undefined
            }
            onChange={(v) => props('button', { href: v.trim() }, key('href'))}
          />
          <ColorField
            label={t.fill}
            value={p.fill}
            allowTransparent
            onChange={(v) => props('button', { fill: v }, key('fill'))}
          />
          <ColorField
            label={t.color}
            value={p.color}
            onChange={(v) => props('button', { color: v }, key('color'))}
          />
          <NumberField
            label={t.radius}
            value={p.borderRadius}
            min={0}
            max={1000}
            onCommit={(v) => props('button', { borderRadius: v })}
          />
        </Section>
      );
    }
  }
}

function ArrangeSection({ multiple, locked }: { multiple: boolean; locked: boolean }) {
  const small = 'border border-neutral-300 bg-white px-2 py-1 text-xs hover:border-neutral-800';
  return (
    <>
      <Section title={t.arrange}>
        <div className="grid grid-cols-2 gap-1">
          <button type="button" className={small} onClick={() => arrangeSelection('front')}>
            {t.front}
          </button>
          <button type="button" className={small} onClick={() => arrangeSelection('back')}>
            {t.back}
          </button>
          <button type="button" className={small} onClick={() => arrangeSelection('forward')}>
            {t.forward}
          </button>
          <button type="button" className={small} onClick={() => arrangeSelection('backward')}>
            {t.backward}
          </button>
        </div>
      </Section>
      <Section title={t.align}>
        <div className="grid grid-cols-3 gap-1">
          {(['left', 'center', 'right', 'top', 'middle', 'bottom'] as const).map((how) => (
            <button key={how} type="button" className={small} onClick={() => alignSelection(how)}>
              {t[`align${how[0]!.toUpperCase()}${how.slice(1)}` as 'alignLeft']}
            </button>
          ))}
        </div>
        {multiple && (
          <div className="grid grid-cols-1 gap-1">
            <button
              type="button"
              className={small}
              onClick={() => distributeSelection('horizontal')}
            >
              {t.distributeH}
            </button>
            <button type="button" className={small} onClick={() => distributeSelection('vertical')}>
              {t.distributeV}
            </button>
          </div>
        )}
      </Section>
      <div className="flex flex-wrap gap-2 p-4">
        <Button variant="secondary" onClick={toggleLock}>
          {locked ? <Unlock size={16} /> : <Lock size={16} />} {locked ? t.unlock : t.lock}
        </Button>
        <Button
          variant="secondary"
          onClick={duplicateSelection}
          title={t.duplicate}
          aria-label={t.duplicate}
        >
          <Copy size={16} />
        </Button>
        <Button
          variant="danger"
          onClick={deleteSelection}
          disabled={locked}
          title={t.delete}
          aria-label={t.delete}
        >
          <Trash size={16} />
        </Button>
      </div>
    </>
  );
}

function PageSection() {
  const title = useEditorStore((state) => state.title);
  const slug = useEditorStore((state) => state.slug);
  const background = useEditorStore((state) => state.content.background);
  const media = useEditorStore((state) => state.media);
  const applyChange = useEditorStore((state) => state.applyChange);
  const [picking, setPicking] = useState(false);
  const library = useMediaLibrary('image');
  const color = background.type === 'color' ? background.value : (background.value ?? '#ffffff');

  return (
    <>
      <Section title={t.page}>
        <TextField
          label={t.pageTitle}
          value={title}
          maxLength={200}
          onChange={(v) => applyChange((d) => void (d.title = v), 'title')}
        />
        <TextField
          label={t.slug}
          value={slug}
          maxLength={120}
          invalid={!SLUG.test(slug)}
          hint={
            <span className="text-neutral-500">
              {t.slugHint}
              {slug}
            </span>
          }
          onChange={(v) =>
            applyChange((d) => void (d.slug = v.toLowerCase().replace(/\s+/g, '-')), 'slug')
          }
        />
      </Section>
      <Section title={t.background}>
        <ColorField
          label={t.color}
          value={color}
          onChange={(v) =>
            applyChange((d) => {
              d.content.background =
                d.content.background.type === 'image'
                  ? { ...d.content.background, value: v }
                  : { type: 'color', value: v };
            }, 'background')
          }
        />
        {background.type === 'image' && media[background.mediaId] && (
          <img src={media[background.mediaId]!.url} alt="" className="h-24 w-full object-cover" />
        )}
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setPicking(true)}>
            {t.backgroundImage}
          </Button>
          {background.type === 'image' && (
            <Button
              variant="ghost"
              onClick={() =>
                applyChange((d) => void (d.content.background = { type: 'color', value: color }))
              }
            >
              {t.removeImage}
            </Button>
          )}
        </div>
      </Section>
      <Dialog open={picking} title={t.backgroundImage} onClose={() => setPicking(false)} wide>
        <div className="max-h-[60vh] overflow-y-auto">
          <MediaGrid
            compact
            items={library.data?.pages.flatMap((p) => p.content) ?? []}
            onPick={(item) => {
              useEditorStore.getState().addMedia([item]);
              applyChange(
                (d) =>
                  void (d.content.background = {
                    type: 'image',
                    mediaId: item.id,
                    fit: 'cover',
                    value: color,
                  }),
              );
              setPicking(false);
            }}
          />
        </div>
      </Dialog>
    </>
  );
}

export function PropertiesPanel() {
  const selection = useEditorStore((state) => state.selection);
  const elements = useEditorStore((state) => state.content.elements);
  const selected = elements.filter((element) => selection.includes(element.id));

  return (
    <aside
      className="w-72 shrink-0 overflow-y-auto border-l border-neutral-200 bg-neutral-50"
      aria-label={t.title}
    >
      <h2 className="border-b border-neutral-200 px-4 py-3 text-sm font-bold">
        {t.title}
        {selected.length > 1 && (
          <span className="ml-2 font-normal text-neutral-500">{t.selection(selected.length)}</span>
        )}
      </h2>
      {selected.length === 0 && <PageSection />}
      {selected.length === 1 && (
        <>
          <GeometrySection element={selected[0]!} />
          <TypeSection element={selected[0]!} />
        </>
      )}
      {selected.length > 0 && (
        <ArrangeSection multiple={selected.length > 2} locked={selected.every((e) => e.locked)} />
      )}
    </aside>
  );
}
