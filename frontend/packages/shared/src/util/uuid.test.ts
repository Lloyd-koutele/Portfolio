import { afterEach, describe, expect, it, vi } from 'vitest';
import { uuid } from './uuid';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

afterEach(() => vi.restoreAllMocks());

describe('uuid', () => {
  it('uses crypto.randomUUID when available', () => {
    expect(uuid()).toMatch(UUID_V4);
  });

  it('still works without randomUUID (plain HTTP on a local domain)', () => {
    vi.spyOn(crypto, 'randomUUID').mockImplementation(undefined as never);
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    const values = new Set(Array.from({ length: 50 }, () => uuid()));
    expect(values.size).toBe(50);
    for (const value of values) expect(value).toMatch(UUID_V4);
  });
});
