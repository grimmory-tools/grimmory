import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom} from 'rxjs';

import {ReaderLoaderService} from './loader.service';

describe('ReaderLoaderService', () => {
  let service: ReaderLoaderService;

  beforeEach(() => {
    service = new ReaderLoaderService();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('returns immediately when the custom element already exists', async () => {
    const getSpy = vi.spyOn(customElements, 'get').mockReturnValue(class FoliateView extends HTMLElement {});
    const appendSpy = vi.spyOn(document.head, 'appendChild');

    await expect(firstValueFrom(service.loadFoliateScript())).resolves.toBeUndefined();

    expect(getSpy).toHaveBeenCalledWith('foliate-view');
    expect(appendSpy).not.toHaveBeenCalled();
  });

  it('loads the foliate script once and waits for the delayed completion', async () => {
    vi.useFakeTimers();
    vi.spyOn(customElements, 'get').mockReturnValue(undefined);
    const appendSpy = vi.spyOn(document.head, 'appendChild');

    const loadPromise = firstValueFrom(service.loadFoliateScript());
    const script = appendSpy.mock.calls[0]?.[0] as HTMLScriptElement;

    expect(script).toBeDefined();
    expect(script.type).toBe('module');
    expect(script.src).toContain('assets/foliate/view.js');

    script.onload?.(new Event('load') as never);
    await vi.advanceTimersByTimeAsync(100);

    await expect(loadPromise).resolves.toBeUndefined();

    appendSpy.mockClear();
    await expect(firstValueFrom(service.loadFoliateScript())).resolves.toBeUndefined();
    expect(appendSpy).not.toHaveBeenCalled();
  });

  it('surfaces script loading failures', async () => {
    vi.useFakeTimers();
    vi.spyOn(customElements, 'get').mockReturnValue(undefined);
    const appendSpy = vi.spyOn(document.head, 'appendChild');

    const loadPromise = firstValueFrom(service.loadFoliateScript());
    const script = appendSpy.mock.calls[0]?.[0] as HTMLScriptElement;

    script.onerror?.(new Event('error') as never);

    await expect(loadPromise).rejects.toThrow('Failed to load foliate.js');
  });

  it('waits for the custom element definition', async () => {
    const whenDefinedSpy = vi.spyOn(customElements, 'whenDefined').mockResolvedValue(class FoliateView extends HTMLElement {});

    await expect(firstValueFrom(service.waitForCustomElement())).resolves.toBeUndefined();

    expect(whenDefinedSpy).toHaveBeenCalledWith('foliate-view');
  });
});
