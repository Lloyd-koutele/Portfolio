import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '@shared/api/http';

export type Device = 'DESKTOP' | 'MOBILE' | 'TABLET' | 'BOT';

export interface Summary {
  from: string;
  to: string;
  totalVisits: number;
  uniqueVisitors: number;
  visitsToday: number;
  previous: { visits: number; uniqueVisitors: number };
  visitsChange: number | null;
  uniqueVisitorsChange: number | null;
  devices: Record<string, number>;
  downloads: number;
}

export interface DayPoint {
  date: string;
  visits: number;
  uniqueVisitors: number;
}

export interface PageCount {
  pageId: string | null;
  slug: string;
  title: string;
  deleted: boolean;
  visits: number;
}

export interface VisitRow {
  id: number;
  occurredAt: string;
  ip: string;
  pageId: string | null;
  pageSlug: string;
  pageTitle: string;
  deviceType: Device;
  bot: boolean;
  referer: string | null;
}

export interface VisitPage {
  content: VisitRow[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}

export type DownloadSource = 'BOOK' | 'HOME' | 'DIRECT';

export interface DownloadRow {
  id: number;
  occurredAt: string;
  ip: string;
  fileName: string;
  source: DownloadSource;
  deviceType: Device;
  bot: boolean;
  referer: string | null;
}

export interface DownloadPage {
  content: DownloadRow[];
  page: VisitPage['page'];
}

export interface Period {
  from: string;
  to: string;
}

export interface VisitFilters {
  ip: string;
  pageId: string;
  device: string;
  includeBots: boolean;
}

export function periodQuery(period: Period): string {
  return new URLSearchParams({ from: period.from, to: period.to }).toString();
}

export function visitsQuery(period: Period, filters: VisitFilters, page?: number): string {
  const params = new URLSearchParams({ from: period.from, to: period.to });
  if (filters.ip.trim()) params.set('ip', filters.ip.trim());
  if (filters.pageId) params.set('pageId', filters.pageId);
  if (filters.device) params.set('device', filters.device);
  if (filters.includeBots) params.set('includeBots', 'true');
  if (page !== undefined) {
    params.set('page', String(page));
    params.set('size', '50');
  }
  return params.toString();
}

export function useStats(period: Period) {
  const query = periodQuery(period);
  const summary = useQuery({
    queryKey: ['stats', 'summary', query],
    queryFn: () => api.get<Summary>(`/api/admin/stats/summary?${query}`),
  });
  const timeseries = useQuery({
    queryKey: ['stats', 'timeseries', query],
    queryFn: () => api.get<DayPoint[]>(`/api/admin/stats/timeseries?${query}`),
  });
  const pages = useQuery({
    queryKey: ['stats', 'pages', query],
    queryFn: () => api.get<PageCount[]>(`/api/admin/stats/pages?${query}`),
  });
  return { summary, timeseries, pages };
}

export function useVisits(period: Period, filters: VisitFilters, page: number) {
  const query = visitsQuery(period, filters, page);
  return useQuery({
    queryKey: ['stats', 'visits', query],
    queryFn: () => api.get<VisitPage>(`/api/admin/stats/visits?${query}`),
    placeholderData: keepPreviousData,
  });
}

export function useDownloads(period: Period, includeBots: boolean, page: number) {
  const params = new URLSearchParams({
    from: period.from,
    to: period.to,
    page: String(page),
    size: '20',
  });
  if (includeBots) params.set('includeBots', 'true');
  const query = params.toString();
  return useQuery({
    queryKey: ['stats', 'downloads', query],
    queryFn: () => api.get<DownloadPage>(`/api/admin/stats/downloads?${query}`),
    placeholderData: keepPreviousData,
  });
}

export function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function lastDays(days: number, today = new Date()): Period {
  const to = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));
  const from = new Date(to);
  from.setUTCDate(from.getUTCDate() - (days - 1));
  return { from: isoDay(from), to: isoDay(to) };
}
