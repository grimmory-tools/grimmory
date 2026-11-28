import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {LocalSettingsService} from './local-settings.service';
import {LocalStorageService} from "./local-storage.service";


describe('LocalSettingsService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [LocalSettingsService, LocalStorageService],
    });
  });

    afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('defaults toast notifications to enabled', () => {
    const service = TestBed.inject(LocalSettingsService);

    expect(service.toastNotificationsEnabled()).toBe(true);
  });

  it('loads a stored toast notification preference', () => {
    localStorage.setItem('local_settings', JSON.stringify({
      cacheStorageEnabled: true,
      toastNotificationsEnabled: false,
    }));

    const service = TestBed.inject(LocalSettingsService);

    expect(service.toastNotificationsEnabled()).toBe(false);
  });

  it('defaults the preference to enabled for older settings', () => {
    localStorage.setItem('local_settings', JSON.stringify({
      cacheStorageEnabled: true,
    }));

    const service = TestBed.inject(LocalSettingsService);

    expect(service.toastNotificationsEnabled()).toBe(true);
  });

  it('saves changes to local storage', () => {
    const service = TestBed.inject(LocalSettingsService);

    service.setToastNotificationsEnabled(false);

    expect(service.toastNotificationsEnabled()).toBe(false);
    expect(JSON.parse(localStorage.getItem('local_settings')!).toastNotificationsEnabled).toBe(false);
  });
});