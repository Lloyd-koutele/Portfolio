import { uuid } from '../util/uuid';
import { useEffect } from 'react';
import { api } from '../api/http';

const SESSION_KEY = 'portfolio.sessionId';
/** A page must stay on screen this long to count, so fast page turning is not recorded. */
export const VISIT_DELAY_MS = 1500;

function sessionId(): string {
  try {
    const existing = sessionStorage.getItem(SESSION_KEY);
    if (existing) return existing;
    const created = uuid();
    sessionStorage.setItem(SESSION_KEY, created);
    return created;
  } catch {
    return uuid();
  }
}

/**
 * Reports the pages currently displayed once they stayed visible for 1.5 s. The server reads the IP itself and
 * decides what to store; a failure is silent and never disturbs the visitor.
 */
export function useVisitTracking(slugs: string[], enabled = true) {
  const key = slugs.join('|');
  useEffect(() => {
    if (!enabled || !key) return;
    const timer = window.setTimeout(() => {
      const id = sessionId();
      const referer = document.referrer || undefined;
      for (const slug of key.split('|')) {
        api
          .post('/api/public/visits', { pageSlug: slug, sessionId: id, referer })
          .catch(() => undefined);
      }
    }, VISIT_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [key, enabled]);
}
