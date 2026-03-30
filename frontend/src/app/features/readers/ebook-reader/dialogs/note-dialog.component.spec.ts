import {SimpleChange} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {ReaderNoteDialogComponent} from './note-dialog.component';

describe('ReaderNoteDialogComponent', () => {
  let fixture: ComponentFixture<ReaderNoteDialogComponent>;
  let component: ReaderNoteDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReaderNoteDialogComponent, getTranslocoModule()],
    }).compileComponents();

    fixture = TestBed.createComponent(ReaderNoteDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('hydrates note state from incoming data and exposes edit mode', () => {
    component.data = {
      cfi: 'epubcfi(/6/2)',
      noteId: 7,
      noteContent: 'Draft note',
      color: '#4CAF50',
    };

    component.ngOnChanges({
      data: new SimpleChange(null, component.data, false),
    });

    expect(component.isEditing).toBe(true);
    expect(component.noteContent).toBe('Draft note');
    expect(component.selectedColor).toBe('#4CAF50');
  });

  it('emits trimmed save payloads and updates the selected color', () => {
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);
    component.noteContent = '  keep this quote ';

    component.selectColor('#9C27B0');
    component.onSave();

    expect(savedSpy).toHaveBeenCalledWith({
      noteContent: 'keep this quote',
      color: '#9C27B0',
    });
  });

  it('ignores blank saves and emits cancellation when asked', () => {
    const savedSpy = vi.fn();
    const cancelSpy = vi.fn();
    component.saved.subscribe(savedSpy);
    component.cancelled.subscribe(cancelSpy);

    component.noteContent = '  ';
    component.onSave();
    component.onCancel();

    expect(savedSpy).not.toHaveBeenCalled();
    expect(cancelSpy).toHaveBeenCalledOnce();
  });
});
