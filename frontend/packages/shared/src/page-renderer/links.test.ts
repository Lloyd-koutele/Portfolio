import { describe, expect, it } from 'vitest';
import { isSafeHref, parseVideoInput, videoEmbedUrl } from './links';

describe('links', () => {
  it('accepts http, https and mailto only', () => {
    expect(isSafeHref('https://example.com')).toBe(true);
    expect(isSafeHref('mailto:a@b.fr')).toBe(true);
    expect(isSafeHref('javascript:alert(1)')).toBe(false);
    expect(isSafeHref('data:text/html,x')).toBe(false);
    expect(isSafeHref('/relative')).toBe(false);
  });

  it('parses YouTube and Vimeo addresses', () => {
    expect(parseVideoInput('https://www.youtube.com/watch?v=aqz-KE-bpKQ')).toEqual({
      provider: 'youtube',
      videoId: 'aqz-KE-bpKQ',
    });
    expect(parseVideoInput('https://youtu.be/aqz-KE-bpKQ')).toEqual({
      provider: 'youtube',
      videoId: 'aqz-KE-bpKQ',
    });
    expect(parseVideoInput('https://vimeo.com/76979871')).toEqual({
      provider: 'vimeo',
      videoId: '76979871',
    });
    expect(parseVideoInput('https://example.com/video')).toBeNull();
  });

  it('builds embed URLs on privacy-friendly hosts', () => {
    expect(videoEmbedUrl('vimeo', '76979871')).toBe(
      'https://player.vimeo.com/video/76979871?dnt=1',
    );
    expect(videoEmbedUrl('youtube', 'bad')).toBeNull();
  });
});
