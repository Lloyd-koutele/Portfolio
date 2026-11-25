import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { messages } from '../../i18n';
import { Download } from '@shared/ui/icons';
import {
  lastDays,
  useDownloads,
  useStats,
  useVisits,
  visitsQuery,
  type Period,
  type VisitFilters,
} from './statsApi';

const t = messages.stats;
const ACCENT = '#ee7d1f';
const INK = '#111111';
const dateTime = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
const shortDay = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'UTC',
});
const number = new Intl.NumberFormat('fr-FR');

type PresetKey = '7' | '30' | '90' | 'custom';

function Change({ value }: { value: number | null }) {
  if (value === null) return <p className="mt-1 text-xs text-neutral-500">{t.noComparison}</p>;
  const positive = value >= 0;
  return (
    <p className={`mt-1 text-xs ${positive ? 'text-green-700' : 'text-red-700'}`}>
      {positive ? '▲' : '▼'} {number.format(Math.abs(value))} %{' '}
      <span className="text-neutral-500">{t.change}</span>
    </p>
  );
}

function Card({
  label,
  value,
  change,
}: {
  label: string;
  value: number | undefined;
  change?: number | null;
}) {
  return (
    <div className="bg-white p-5 shadow-sm">
      <p className="text-sm text-neutral-600">{label}</p>
      <p className="mt-1 text-3xl font-bold tabular-nums" data-testid={`card-${label}`}>
        {value === undefined ? '…' : number.format(value)}
      </p>
      {change !== undefined && <Change value={change} />}
    </div>
  );
}

export function StatsPage() {
  const [preset, setPreset] = useState<PresetKey>('30');
  const [period, setPeriod] = useState<Period>(() => lastDays(30));
  const [filters, setFilters] = useState<VisitFilters>({
    ip: '',
    pageId: '',
    device: '',
    includeBots: false,
  });
  const [page, setPage] = useState(0);
  const { summary, timeseries, pages } = useStats(period);
  const visits = useVisits(period, filters, page);

  const choose = (key: PresetKey) => {
    setPreset(key);
    if (key !== 'custom') setPeriod(lastDays(Number(key)));
    setPage(0);
  };
  const filter = (patch: Partial<VisitFilters>) => {
    setFilters((current) => ({ ...current, ...patch }));
    setPage(0);
  };

  const devices = summary.data
    ? (['DESKTOP', 'MOBILE', 'TABLET'] as const).map((key) => ({
        name: t.deviceNames[key],
        value: summary.data.devices[key] ?? 0,
        share: summary.data.totalVisits
          ? Math.round(((summary.data.devices[key] ?? 0) * 100) / summary.data.totalVisits)
          : 0,
      }))
    : [];

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-bold">
          <span className="text-accent">■</span> {t.title}
        </h1>
        <div className="flex flex-wrap items-end gap-2" role="group" aria-label={t.period}>
          {(['7', '30', '90', 'custom'] as const).map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => choose(key)}
              aria-pressed={preset === key}
              className={`px-3 py-2 text-sm font-semibold ${preset === key ? 'bg-ink text-white' : 'bg-white hover:bg-neutral-200'}`}
            >
              {t.periods[key]}
            </button>
          ))}
          {preset === 'custom' && (
            <>
              <label className="text-sm">
                {t.from}{' '}
                <input
                  type="date"
                  value={period.from}
                  max={period.to}
                  onChange={(e) => e.target.value && setPeriod({ ...period, from: e.target.value })}
                  className="border border-neutral-300 bg-white px-2 py-1"
                />
              </label>
              <label className="text-sm">
                {t.to}{' '}
                <input
                  type="date"
                  value={period.to}
                  min={period.from}
                  onChange={(e) => e.target.value && setPeriod({ ...period, to: e.target.value })}
                  className="border border-neutral-300 bg-white px-2 py-1"
                />
              </label>
            </>
          )}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card
          label={t.totalVisits}
          value={summary.data?.totalVisits}
          change={summary.data?.visitsChange ?? null}
        />
        <Card
          label={t.uniqueVisitors}
          value={summary.data?.uniqueVisitors}
          change={summary.data?.uniqueVisitorsChange ?? null}
        />
        <Card label={t.visitsToday} value={summary.data?.visitsToday} />
        <Card label={t.cvDownloads} value={summary.data?.downloads} />
      </div>

      <section className="bg-white p-5 shadow-sm">
        <h2 className="mb-4 font-bold">{t.timeline}</h2>
        <div className="h-72">
          <ResponsiveContainer>
            <LineChart data={timeseries.data ?? []} margin={{ left: -10, right: 10 }}>
              <CartesianGrid stroke="#eeeeee" />
              <XAxis
                dataKey="date"
                tickFormatter={(value: string) => shortDay.format(new Date(value))}
                fontSize={12}
              />
              <YAxis allowDecimals={false} fontSize={12} />
              <Tooltip labelFormatter={(value) => shortDay.format(new Date(String(value)))} />
              <Legend />
              <Line
                type="monotone"
                dataKey="visits"
                name={t.visits}
                stroke={ACCENT}
                strokeWidth={2}
                dot={false}
              />
              <Line
                type="monotone"
                dataKey="uniqueVisitors"
                name={t.uniques}
                stroke={INK}
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <div className="grid gap-4 lg:grid-cols-3">
        <section className="bg-white p-5 shadow-sm lg:col-span-2">
          <h2 className="mb-4 font-bold">{t.topPages}</h2>
          <div className="h-72">
            <ResponsiveContainer>
              <BarChart
                data={(pages.data ?? []).slice(0, 10)}
                layout="vertical"
                margin={{ left: 20, right: 20 }}
              >
                <CartesianGrid stroke="#eeeeee" horizontal={false} />
                <XAxis type="number" allowDecimals={false} fontSize={12} />
                <YAxis type="category" dataKey="title" width={150} fontSize={12} />
                <Tooltip />
                <Bar dataKey="visits" name={t.visits} fill={ACCENT} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
        <section className="bg-white p-5 shadow-sm">
          <h2 className="mb-4 font-bold">{t.devices}</h2>
          <ul className="space-y-4">
            {devices.map((device) => (
              <li key={device.name}>
                <div className="flex justify-between text-sm">
                  <span>{device.name}</span>
                  <span className="tabular-nums">
                    {number.format(device.value)} · {device.share} %
                  </span>
                </div>
                <div className="mt-1 h-2 bg-neutral-200">
                  <div className="bg-accent h-2" style={{ width: `${device.share}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="bg-white p-5 shadow-sm">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <h2 className="font-bold">{t.visitsTable}</h2>
          <a
            href={`/api/admin/stats/visits.csv?${visitsQuery(period, filters)}`}
            download
            className="bg-ink hover:bg-accent inline-flex items-center gap-2 px-3 py-2 text-sm font-semibold text-white"
          >
            <Download size={16} /> {t.exportCsv}
          </a>
        </div>
        <div className="mb-4 flex flex-wrap items-end gap-3 text-sm">
          <label>
            <span className="block text-xs text-neutral-600">{t.filters.ip}</span>
            <input
              value={filters.ip}
              onChange={(e) => filter({ ip: e.target.value })}
              className="border border-neutral-300 px-2 py-1"
            />
          </label>
          <label>
            <span className="block text-xs text-neutral-600">{t.filters.page}</span>
            <select
              value={filters.pageId}
              onChange={(e) => filter({ pageId: e.target.value })}
              className="border border-neutral-300 px-2 py-1"
            >
              <option value="">{t.filters.allPages}</option>
              {(pages.data ?? [])
                .filter((p) => p.pageId)
                .map((p) => (
                  <option key={p.pageId} value={p.pageId!}>
                    {p.title}
                  </option>
                ))}
            </select>
          </label>
          <label>
            <span className="block text-xs text-neutral-600">{t.filters.device}</span>
            <select
              value={filters.device}
              onChange={(e) => filter({ device: e.target.value })}
              className="border border-neutral-300 px-2 py-1"
            >
              <option value="">{t.filters.allDevices}</option>
              {(['DESKTOP', 'MOBILE', 'TABLET', 'BOT'] as const).map((key) => (
                <option key={key} value={key}>
                  {t.deviceNames[key]}
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={filters.includeBots}
              onChange={(e) => filter({ includeBots: e.target.checked })}
            />
            {t.filters.bots}
          </label>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-neutral-300 text-xs text-neutral-600 uppercase">
              <tr>
                <th className="py-2 pr-4">{t.columns.date}</th>
                <th className="py-2 pr-4">{t.columns.ip}</th>
                <th className="py-2 pr-4">{t.columns.page}</th>
                <th className="py-2 pr-4">{t.columns.device}</th>
                <th className="py-2">{t.columns.referer}</th>
              </tr>
            </thead>
            <tbody>
              {visits.data?.content.map((row) => (
                <tr key={row.id} className="border-b border-neutral-100">
                  <td className="py-2 pr-4 whitespace-nowrap tabular-nums">
                    {dateTime.format(new Date(row.occurredAt))}
                  </td>
                  <td className="py-2 pr-4 font-mono text-xs">{row.ip}</td>
                  <td className="py-2 pr-4">
                    {row.pageTitle}
                    {!row.pageId && <span className="text-neutral-500"> {t.deletedPage}</span>}
                  </td>
                  <td className="py-2 pr-4">{t.deviceNames[row.deviceType]}</td>
                  <td className="max-w-64 truncate py-2" title={row.referer ?? undefined}>
                    {row.referer ?? <span className="text-neutral-400">{t.direct}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {visits.data && visits.data.content.length === 0 && (
            <p className="py-6 text-center text-neutral-500">{t.empty}</p>
          )}
        </div>
        {visits.data && (
          <div className="mt-4 flex items-center justify-end gap-3 text-sm">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className="border border-neutral-300 px-3 py-1 disabled:opacity-40"
            >
              {t.previousPage}
            </button>
            <span>{t.pageOf(page + 1, visits.data.page.totalPages)}</span>
            <button
              type="button"
              disabled={page + 1 >= visits.data.page.totalPages}
              onClick={() => setPage(page + 1)}
              className="border border-neutral-300 px-3 py-1 disabled:opacity-40"
            >
              {t.nextPage}
            </button>
          </div>
        )}
      </section>

      <DownloadsSection period={period} />
    </div>
  );
}

/** The CV download log: one row per download, robots left out unless asked for. */
function DownloadsSection({ period }: { period: Period }) {
  const [includeBots, setIncludeBots] = useState(false);
  const [page, setPage] = useState(0);
  const downloads = useDownloads(period, includeBots, page);

  return (
    <section className="bg-white p-5 shadow-sm" aria-labelledby="downloads-title">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <h2 id="downloads-title" className="font-bold">
          {t.downloadsTable}
        </h2>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={includeBots}
            onChange={(e) => {
              setIncludeBots(e.target.checked);
              setPage(0);
            }}
          />
          {t.filters.bots}
        </label>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-neutral-300 text-xs text-neutral-600 uppercase">
            <tr>
              <th className="py-2 pr-4">{t.columns.date}</th>
              <th className="py-2 pr-4">{t.columns.ip}</th>
              <th className="py-2 pr-4">{t.downloadColumns.file}</th>
              <th className="py-2 pr-4">{t.downloadColumns.source}</th>
              <th className="py-2 pr-4">{t.columns.device}</th>
              <th className="py-2">{t.columns.referer}</th>
            </tr>
          </thead>
          <tbody>
            {downloads.data?.content.map((row) => (
              <tr key={row.id} className="border-b border-neutral-100">
                <td className="py-2 pr-4 whitespace-nowrap tabular-nums">
                  {dateTime.format(new Date(row.occurredAt))}
                </td>
                <td className="py-2 pr-4 font-mono text-xs">{row.ip}</td>
                <td className="max-w-48 truncate py-2 pr-4" title={row.fileName}>
                  {row.fileName}
                </td>
                <td className="py-2 pr-4">{t.sources[row.source]}</td>
                <td className="py-2 pr-4">{t.deviceNames[row.deviceType]}</td>
                <td className="max-w-64 truncate py-2" title={row.referer ?? undefined}>
                  {row.referer ?? <span className="text-neutral-400">{t.direct}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {downloads.data && downloads.data.content.length === 0 && (
          <p className="py-6 text-center text-neutral-500">{t.downloadsEmpty}</p>
        )}
      </div>
      {downloads.data && downloads.data.page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-end gap-3 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
            className="border border-neutral-300 px-3 py-1 disabled:opacity-40"
          >
            {t.previousPage}
          </button>
          <span>{t.pageOf(page + 1, downloads.data.page.totalPages)}</span>
          <button
            type="button"
            disabled={page + 1 >= downloads.data.page.totalPages}
            onClick={() => setPage(page + 1)}
            className="border border-neutral-300 px-3 py-1 disabled:opacity-40"
          >
            {t.nextPage}
          </button>
        </div>
      )}
    </section>
  );
}
