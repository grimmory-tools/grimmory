import {describe, expect, it, vi} from 'vitest';

import {FaviconService} from './favicon-service';

describe('FaviconService', () => {
  it('updates an existing favicon link with a generated SVG URL', () => {
    const favicon = {type: '', href: ''} as HTMLLinkElement;
    const querySelectorSpy = vi.spyOn(document, 'querySelector').mockReturnValue(favicon);
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:favicon');

    const service = new FaviconService();
    service.updateFavicon('#abcdef', '#123456');

    expect(querySelectorSpy).toHaveBeenCalledWith("link[rel*='icon']");
    expect(createObjectUrlSpy).toHaveBeenCalledOnce();
    expect(favicon.type).toBe('image/svg+xml');
    expect(favicon.href).toBe('blob:favicon');
  });

  it('creates and appends a favicon link when one does not already exist', () => {
    const appended: HTMLLinkElement[] = [];
    const favicon = {rel: '', type: '', href: ''} as HTMLLinkElement;

    vi.spyOn(document, 'querySelector').mockReturnValue(null);
    vi.spyOn(document, 'createElement').mockReturnValue(favicon);
    vi.spyOn(document.head, 'appendChild').mockImplementation(node => {
      appended.push(node as HTMLLinkElement);
      return node;
    });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:new-favicon');

    const service = new FaviconService();
    service.updateFavicon('#fedcba', '#abcdef');

    expect(appended).toEqual([favicon]);
    expect(favicon.rel).toBe('icon');
    expect(favicon.type).toBe('image/svg+xml');
    expect(favicon.href).toBe('blob:new-favicon');
  });

  it('revokes the previous favicon object URL before replacing it', () => {
    const favicon = {type: '', href: ''} as HTMLLinkElement;

    vi.spyOn(document, 'querySelector').mockReturnValue(favicon);
    vi.spyOn(URL, 'createObjectURL')
      .mockReturnValueOnce('blob:first-favicon')
      .mockReturnValueOnce('blob:second-favicon');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    const service = new FaviconService();
    service.updateFavicon('#fedcba', '#abcdef');
    service.updateFavicon('#89abcd', '#123456');

    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:first-favicon');
    expect(favicon.href).toBe('blob:second-favicon');
  });
});
