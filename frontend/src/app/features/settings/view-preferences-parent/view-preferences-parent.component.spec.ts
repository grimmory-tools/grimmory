import {TestBed} from '@angular/core/testing';
import {describe, expect, it, vi} from 'vitest';

import {ViewPreferencesParentComponent} from './view-preferences-parent.component';
import {LocalStorageService} from '../../../shared/service/local-storage.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

describe('ViewPreferencesParentComponent', () => {
  it('loads and saves the sidebar width', () => {
    const localStorageGet = vi.fn(() => 312);
    const localStorageSet = vi.fn();
    const messageAdd = vi.fn();
    const translate = vi.fn(key => key);

    TestBed.configureTestingModule({
      imports: [ViewPreferencesParentComponent],
      providers: [
        {provide: LocalStorageService, useValue: {get: localStorageGet, set: localStorageSet}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });

    const fixture = TestBed.createComponent(ViewPreferencesParentComponent);
    const component = fixture.componentInstance;

    component.ngOnInit();
    expect(component.sidebarWidth).toBe(312);

    component.saveSidebarWidth();

    expect(localStorageSet).toHaveBeenCalledWith('sidebarWidth', 312);
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'settingsView.layout.saved',
      detail: 'settingsView.layout.savedDetail'
    });
  });

  it('updates the sidebar CSS variable when the width changes', () => {
    const localStorageGet = vi.fn(() => 225);

    TestBed.configureTestingModule({
      imports: [ViewPreferencesParentComponent],
      providers: [
        {provide: LocalStorageService, useValue: {get: localStorageGet, set: vi.fn()}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn()}},
      ]
    });

    const fixture = TestBed.createComponent(ViewPreferencesParentComponent);
    const component = fixture.componentInstance;

    component.sidebarWidth = 287;
    component.onSidebarWidthChange();

    expect(document.documentElement.style.getPropertyValue('--sidebar-width')).toBe('287px');
  });

  it('falls back to the default width when no value is stored', () => {
    TestBed.configureTestingModule({
      imports: [ViewPreferencesParentComponent],
      providers: [
        {provide: LocalStorageService, useValue: {get: vi.fn(() => null), set: vi.fn()}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn()}},
      ]
    });

    const fixture = TestBed.createComponent(ViewPreferencesParentComponent);
    const component = fixture.componentInstance;

    component.ngOnInit();

    expect(component.sidebarWidth).toBe(225);
  });
});
