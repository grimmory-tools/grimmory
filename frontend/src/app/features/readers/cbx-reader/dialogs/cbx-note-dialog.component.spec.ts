import {SimpleChange} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {CbxNoteDialogComponent} from './cbx-note-dialog.component';

describe('CbxNoteDialogComponent', () => {
  let fixture: ComponentFixture<CbxNoteDialogComponent>;
  let component: CbxNoteDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CbxNoteDialogComponent, getTranslocoModule()],
    }).compileComponents();

    fixture = TestBed.createComponent(CbxNoteDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('hydrates its local state from input changes and reports edit mode when a note id is present', () => {
    component.data = {
      pageNumber: 9,
      noteId: 3,
      noteContent: ' remembered ',
      color: '#2196F3',
    };

    component.ngOnChanges({
      data: new SimpleChange(null, component.data, false),
    });

    expect(component.isEditing).toBe(true);
    expect(component.noteContent).toBe(' remembered ');
    expect(component.selectedColor).toBe('#2196F3');
  });

  it('emits saved notes with trimmed content and the selected color', () => {
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);
    component.noteContent = '  Panel reveal  ';
    component.selectColor('#E91E63');

    component.onSave();

    expect(savedSpy).toHaveBeenCalledWith({
      noteContent: 'Panel reveal',
      color: '#E91E63',
    });
  });

  it('does not emit saves for blank notes and emits cancel events explicitly or through the overlay', () => {
    const savedSpy = vi.fn();
    const cancelSpy = vi.fn();
    component.saved.subscribe(savedSpy);
    component.cancelled.subscribe(cancelSpy);

    component.noteContent = '   ';
    component.onSave();
    component.onCancel();
    component.onOverlayClick({
      target: {
        classList: {
          contains: vi.fn((value: string) => value === 'dialog-overlay'),
        },
      },
    } as unknown as Event);

    expect(savedSpy).not.toHaveBeenCalled();
    expect(cancelSpy).toHaveBeenCalledTimes(2);
  });
});
