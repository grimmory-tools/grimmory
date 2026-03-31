import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {LocalStorageService} from './local-storage.service';

describe('LocalStorageService', () => {
  let service: LocalStorageService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [LocalStorageService],
    });

    service = TestBed.inject(LocalStorageService);
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('reads parsed values from local storage', () => {
    localStorage.setItem('settings', JSON.stringify({theme: 'aura'}));

    expect(service.get<{theme: string}>('settings')).toEqual({theme: 'aura'});
  });

  it('returns null when the key is missing or invalid', () => {
    localStorage.setItem('broken', '{not-json');

    expect(service.get('missing')).toBeNull();
    expect(service.get('broken')).toBeNull();
  });

  it('serializes values when writing to local storage', () => {
    service.set('settings', {theme: 'aura'});

    expect(localStorage.getItem('settings')).toBe(JSON.stringify({theme: 'aura'}));
  });

  it('swallows storage write errors', () => {
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota exceeded');
    });

    expect(() => service.set('settings', {theme: 'aura'})).not.toThrow();
    expect(setItemSpy).toHaveBeenCalled();
    setItemSpy.mockRestore();
  });

  it('removes values from local storage', () => {
    localStorage.setItem('settings', JSON.stringify({theme: 'aura'}));

    service.remove('settings');

    expect(localStorage.getItem('settings')).toBeNull();
  });
});
