import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';

import {AppSettingsService} from './app-settings.service';
import {SettingsHelperService} from './settings-helper.service';

describe('SettingsHelperService', () => {
  const appSettingsService = {
    saveSettings: vi.fn(),
  };
  const messageService = {
    add: vi.fn(),
  };
  const translocoService = {
    translate: vi.fn((key: string) => `translated:${key}`),
  };

  let service: SettingsHelperService;

  beforeEach(() => {
    vi.restoreAllMocks();
    appSettingsService.saveSettings.mockReset();
    messageService.add.mockClear();
    translocoService.translate.mockClear();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        SettingsHelperService,
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    service = TestBed.inject(SettingsHelperService);
  });

  it('saves a setting and shows a success message', () => {
    appSettingsService.saveSettings.mockReturnValue(of(undefined));

    service.saveSetting('theme', 'dark');

    expect(appSettingsService.saveSettings).toHaveBeenCalledWith([{key: 'theme', newValue: 'dark'}]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'translated:shared.settingsHelper.settingsSavedSummary',
      detail: 'translated:shared.settingsHelper.settingsSavedDetail',
    });
  });

  it('shows an error message when saving fails', () => {
    const error = new Error('save failed');
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    appSettingsService.saveSettings.mockReturnValue(throwError(() => error));

    service.saveSetting('theme', 'dark');

    expect(errorSpy).toHaveBeenCalledWith('Failed to save setting:', error);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:common.error',
      detail: 'translated:shared.settingsHelper.saveErrorDetail',
    });
  });

  it('forwards ad-hoc messages through the message service', () => {
    service.showMessage('success', 'Saved', 'Everything worked');

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'Saved',
      detail: 'Everything worked',
    });
  });
});
