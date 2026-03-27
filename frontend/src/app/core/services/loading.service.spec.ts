import {afterEach, describe, expect, it} from 'vitest';

import {LoadingService} from './loading.service';

describe('LoadingService', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    document.body.style.cursor = '';
  });

  it('shows the default loading overlay and restores the cursor when hidden', () => {
    const service = new LoadingService();

    const loader = service.show();

    expect(document.body.contains(loader)).toBe(true);
    expect(loader.querySelector('.loader-content')?.textContent).toContain('Loading...');
    expect(document.body.style.cursor).toBe('wait');

    service.hide(loader);

    expect(document.body.contains(loader)).toBe(false);
    expect(document.body.style.cursor).toBe('default');
  });

  it('renders a custom loading message', () => {
    const service = new LoadingService();

    const loader = service.show('Syncing metadata');

    expect(loader.querySelector('.loader-content')?.textContent).toContain('Syncing metadata');

    service.hide(loader);
  });

  it('removes all active overlays and ignores detached loaders', () => {
    const service = new LoadingService();

    const first = service.show('First');
    const second = service.show('Second');
    const detached = document.createElement('div');

    expect(() => service.hide(detached)).not.toThrow();

    service.hideAll();

    expect(document.body.contains(first)).toBe(false);
    expect(document.body.contains(second)).toBe(false);
    expect(document.body.style.cursor).toBe('default');
  });

  it('keeps the wait cursor active until the final loader is hidden', () => {
    const service = new LoadingService();

    const first = service.show('First');
    const second = service.show('Second');

    service.hide(first);

    expect(document.body.contains(first)).toBe(false);
    expect(document.body.contains(second)).toBe(true);
    expect(document.body.style.cursor).toBe('wait');

    service.hide(second);

    expect(document.body.contains(second)).toBe(false);
    expect(document.body.style.cursor).toBe('default');
  });
});
