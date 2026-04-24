import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {describe, expect, it, vi} from 'vitest';

import {ViewPreferencesParentComponent} from './view-preferences-parent.component';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {LayoutService} from '../../../shared/layout/layout.service';

describe('ViewPreferencesParentComponent', () => {
  function createLayoutService(initialWidth: number) {
    const sidebarWidth = signal(initialWidth);
    return {
      sidebarWidth,
      setSidebarWidth: vi.fn((value: number) => sidebarWidth.set(value)),
    };
  }

  it('reads the sidebar width from LayoutService and saves it back with persistence', () => {
    const layoutService = createLayoutService(312);
    const messageAdd = vi.fn();
    const translate = vi.fn(key => key);

    TestBed.configureTestingModule({
      imports: [ViewPreferencesParentComponent],
      providers: [
        {provide: LayoutService, useValue: layoutService},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });

    const fixture = TestBed.createComponent(ViewPreferencesParentComponent);
    const component = fixture.componentInstance;

    expect(component.sidebarWidth).toBe(312);

    component.saveSidebarWidth();

    expect(layoutService.setSidebarWidth).toHaveBeenCalledWith(312, true);
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'settingsView.layout.saved',
      detail: 'settingsView.layout.savedDetail'
    });
  });

  it('updates the layout service width immediately while the slider moves', () => {
    const layoutService = createLayoutService(225);

    TestBed.configureTestingModule({
      imports: [ViewPreferencesParentComponent],
      providers: [
        {provide: LayoutService, useValue: layoutService},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn()}},
      ]
    });

    const fixture = TestBed.createComponent(ViewPreferencesParentComponent);
    const component = fixture.componentInstance;

    component.sidebarWidth = 287;
    component.onSidebarWidthChange();

    expect(layoutService.setSidebarWidth).toHaveBeenCalledWith(287, false);
    expect(component.sidebarWidth).toBe(287);
  });

  it('falls back to the layout service default width when nothing custom is loaded', () => {
    const layoutService = createLayoutService(225);

    TestBed.configureTestingModule({
      imports: [ViewPreferencesParentComponent],
      providers: [
        {provide: LayoutService, useValue: layoutService},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn()}},
      ]
    });

    const fixture = TestBed.createComponent(ViewPreferencesParentComponent);
    const component = fixture.componentInstance;

    expect(component.sidebarWidth).toBe(225);
  });
});
