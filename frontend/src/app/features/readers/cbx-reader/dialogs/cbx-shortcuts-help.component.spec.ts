import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {CbxShortcutsHelpComponent} from './cbx-shortcuts-help.component';

describe('CbxShortcutsHelpComponent', () => {
  let fixture: ComponentFixture<CbxShortcutsHelpComponent>;
  let component: CbxShortcutsHelpComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CbxShortcutsHelpComponent, getTranslocoModule()],
    }).compileComponents();

    fixture = TestBed.createComponent(CbxShortcutsHelpComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('builds translated shortcut groups for navigation, display, playback, and other actions', () => {
    const groups = component.shortcutGroups;

    expect(groups).toHaveLength(4);
    expect(groups.map((group) => group.shortcuts.length)).toEqual([7, 7, 1, 1]);
    expect(groups[0].shortcuts[0].keys).toEqual(['←', '→']);
    expect(groups[0].shortcuts[0].mobileGesture).toBeTruthy();
  });

  it('emits close events directly and when the overlay background is clicked', () => {
    const closeSpy = vi.fn();
    component.closed.subscribe(closeSpy);

    component.onClose();
    component.onOverlayClick({
      target: {
        classList: {
          contains: vi.fn((value: string) => value === 'dialog-overlay'),
        },
      },
    } as unknown as Event);

    expect(closeSpy).toHaveBeenCalledTimes(2);
  });

  it('ignores clicks that do not land on the dialog overlay', () => {
    const closeSpy = vi.fn();
    component.closed.subscribe(closeSpy);

    component.onOverlayClick({
      target: {
        classList: {
          contains: vi.fn(() => false),
        },
      },
    } as unknown as Event);

    expect(closeSpy).not.toHaveBeenCalled();
  });
});
