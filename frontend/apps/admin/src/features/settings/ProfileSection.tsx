import { useState, type FormEvent } from 'react';
import { ApiError } from '@shared/api/http';
import { Button } from '@shared/ui/Button';
import { Dialog } from '@shared/ui/Dialog';
import { messages } from '../../i18n';
import { MediaGrid } from '../media-library/MediaGrid';
import { useMediaLibrary } from '../media-library/mediaApi';
import {
  useProfile,
  useSaveProfile,
  type Experience,
  type Profile,
  type ProfileBody,
} from '../home/profileApi';

const t = messages.profile;
const field = 'focus:border-accent mt-1 w-full border border-neutral-300 bg-white px-3 py-2';
const emptyExperience: Experience = { role: '', organization: '', period: '', description: '' };

/** Réglages → texts and photo of the administration's home page. */
export function ProfileSection() {
  const profile = useProfile();
  if (!profile.data) {
    return null;
  }
  return <ProfileForm initial={profile.data} />;
}

function ProfileForm({ initial }: { initial: Profile }) {
  const save = useSaveProfile();
  const library = useMediaLibrary('image');
  const [form, setForm] = useState<ProfileBody>(() => {
    const { photoUrl: _photo, updatedAt: _updated, ...rest } = initial;
    return rest;
  });
  const [skillsText, setSkillsText] = useState(initial.skills.join('\n'));
  const [photoUrl, setPhotoUrl] = useState(initial.photoUrl);
  const [picking, setPicking] = useState(false);
  const [done, setDone] = useState(false);

  const update = <K extends keyof ProfileBody>(key: K, value: ProfileBody[K]) => {
    setDone(false);
    setForm((current) => ({ ...current, [key]: value }));
  };
  const updateExperience = (index: number, patch: Partial<Experience>) =>
    update(
      'experiences',
      form.experiences.map((item, i) => (i === index ? { ...item, ...patch } : item)),
    );

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const skills = skillsText
      .split('\n')
      .map((skill) => skill.trim())
      .filter(Boolean);
    save.mutate({ ...form, skills }, { onSuccess: () => setDone(true) });
  };

  const errors = save.error instanceof ApiError ? (save.error.problem.errors ?? {}) : {};

  return (
    <form
      onSubmit={submit}
      className="space-y-4 bg-white p-6 shadow-sm"
      aria-labelledby="profile-title"
    >
      <div>
        <h2 id="profile-title" className="text-xl font-bold">
          {t.title}
        </h2>
        <p className="text-sm text-neutral-600">{t.intro}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className="text-sm font-medium">{t.fullName}</span>
          <input
            className={field}
            value={form.fullName}
            maxLength={120}
            required
            onChange={(e) => update('fullName', e.target.value)}
          />
          {errors.fullName && <span className="text-sm text-red-700">{errors.fullName}</span>}
        </label>
        <label className="block">
          <span className="text-sm font-medium">{t.headline}</span>
          <input
            className={field}
            value={form.headline}
            maxLength={160}
            required
            onChange={(e) => update('headline', e.target.value)}
          />
        </label>
      </div>
      <label className="block">
        <span className="text-sm font-medium">{t.introText}</span>
        <textarea
          className={`${field} min-h-24`}
          value={form.intro}
          maxLength={1000}
          onChange={(e) => update('intro', e.target.value)}
        />
      </label>
      <label className="block">
        <span className="text-sm font-medium">{t.availability}</span>
        <input
          className={field}
          value={form.availability}
          maxLength={300}
          onChange={(e) => update('availability', e.target.value)}
        />
      </label>
      <label className="block">
        <span className="text-sm font-medium">{t.skills}</span>
        <textarea
          className={`${field} min-h-32 font-mono text-sm`}
          value={skillsText}
          onChange={(e) => {
            setDone(false);
            setSkillsText(e.target.value);
          }}
        />
      </label>
      <div>
        <span className="text-sm font-medium">{t.photo}</span>
        <div className="mt-2 flex items-center gap-3">
          {photoUrl && (
            <img src={photoUrl} alt="" className="h-20 w-20 rounded-full object-cover" />
          )}
          <Button variant="secondary" onClick={() => setPicking(true)}>
            {t.choosePhoto}
          </Button>
          {form.photoMediaId && (
            <Button
              variant="ghost"
              onClick={() => {
                update('photoMediaId', null);
                setPhotoUrl(null);
              }}
            >
              {t.removePhoto}
            </Button>
          )}
        </div>
      </div>
      <fieldset className="space-y-3">
        <legend className="text-sm font-medium">{t.experiences}</legend>
        {form.experiences.map((experience, index) => (
          <div key={index} className="grid gap-2 border border-neutral-200 p-3 sm:grid-cols-3">
            <input
              aria-label={t.role}
              placeholder={t.role}
              className={field}
              value={experience.role}
              maxLength={120}
              required
              onChange={(e) => updateExperience(index, { role: e.target.value })}
            />
            <input
              aria-label={t.organization}
              placeholder={t.organization}
              className={field}
              value={experience.organization}
              maxLength={120}
              onChange={(e) => updateExperience(index, { organization: e.target.value })}
            />
            <input
              aria-label={t.period}
              placeholder={t.period}
              className={field}
              value={experience.period}
              maxLength={60}
              onChange={(e) => updateExperience(index, { period: e.target.value })}
            />
            <textarea
              aria-label={t.description}
              placeholder={t.description}
              className={`${field} sm:col-span-3`}
              value={experience.description}
              maxLength={400}
              onChange={(e) => updateExperience(index, { description: e.target.value })}
            />
            <div className="sm:col-span-3">
              <Button
                variant="ghost"
                onClick={() =>
                  update(
                    'experiences',
                    form.experiences.filter((_, i) => i !== index),
                  )
                }
              >
                {t.removeExperience}
              </Button>
            </div>
          </div>
        ))}
        {form.experiences.length < 12 && (
          <Button
            variant="secondary"
            onClick={() => update('experiences', [...form.experiences, { ...emptyExperience }])}
          >
            {t.addExperience}
          </Button>
        )}
      </fieldset>
      <div className="flex items-center gap-4">
        <Button type="submit" disabled={save.isPending}>
          {t.save}
        </Button>
        {done && (
          <span role="status" className="text-sm text-green-700">
            {t.saved}
          </span>
        )}
        {save.isError && (
          <span role="alert" className="text-sm text-red-700">
            {t.saveError}
          </span>
        )}
      </div>
      <Dialog open={picking} title={t.choosePhoto} onClose={() => setPicking(false)} wide>
        <div className="max-h-[60vh] overflow-y-auto">
          <MediaGrid
            items={library.data?.pages.flatMap((page) => page.content) ?? []}
            onPick={(item) => {
              update('photoMediaId', item.id);
              setPhotoUrl(item.url);
              setPicking(false);
            }}
            compact
          />
        </div>
      </Dialog>
    </form>
  );
}
