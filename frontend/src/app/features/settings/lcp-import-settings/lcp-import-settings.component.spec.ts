import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';
import {LcpSettingsService} from '../../../shared/service/lcp-settings.service';
import {LcpImportSettingsComponent} from './lcp-import-settings.component';

function setup(configured = false) {
  const settings = {
    getStatus: vi.fn(() => of({hasDefaultPassphrase: configured})),
    save: vi.fn(() => of(undefined)),
    clear: vi.fn(() => of(undefined))
  };
  TestBed.configureTestingModule({providers: [
    {provide: LcpSettingsService, useValue: settings},
    {provide: TranslocoService, useValue: {translate: (key: string) => key}}
  ]});
  const component = TestBed.runInInjectionContext(() => new LcpImportSettingsComponent());
  return {component, settings};
}

describe('LcpImportSettingsComponent', () => {
  it('reports an existing default without loading its value', () => {
    const {component} = setup(true);
    expect(component.configured()).toBe(true);
    expect(component.passphrase).toBe('');
  });

  it('saves the entered code and clears the password field', () => {
    const {component, settings} = setup();
    component.passphrase = 'fixture-code';
    component.save();
    expect(settings.save).toHaveBeenCalledWith('fixture-code');
    expect(component.configured()).toBe(true);
    expect(component.passphrase).toBe('');
  });

  it('does not report a saved default when the request fails', () => {
    const {component, settings} = setup();
    settings.save.mockReturnValue(throwError(() => new Error('failed')));
    component.passphrase = 'fixture-code';
    component.save();
    expect(component.configured()).toBe(false);
    expect(component.busy()).toBe(false);
    expect(component.error()).toBe('lcpImport.settingsError');
  });

  it('removes the default and clears entered text', () => {
    const {component, settings} = setup(true);
    component.passphrase = 'discard-this';
    component.clear();
    expect(settings.clear).toHaveBeenCalledOnce();
    expect(component.configured()).toBe(false);
    expect(component.passphrase).toBe('');
  });
});
