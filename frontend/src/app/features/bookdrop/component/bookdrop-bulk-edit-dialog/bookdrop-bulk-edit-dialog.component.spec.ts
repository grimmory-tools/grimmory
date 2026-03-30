import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BookdropBulkEditDialogComponent, BulkEditResult} from './bookdrop-bulk-edit-dialog.component';

describe('BookdropBulkEditDialogComponent', () => {
  let fixture: ComponentFixture<BookdropBulkEditDialogComponent>;
  let component: BookdropBulkEditDialogComponent;
  let dialogRef: {close: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    dialogRef = {
      close: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [BookdropBulkEditDialogComponent, getTranslocoModule()],
      providers: [
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: DynamicDialogConfig, useValue: {data: {fileCount: 4}}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookdropBulkEditDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function blurAutoComplete(fieldName: string, value: string) {
    const input = document.createElement('input');
    input.value = value;

    component.onAutoCompleteBlur(fieldName, {target: input} as unknown as Event);

    return input;
  }

  it('hydrates the file count and enables fields when form controls receive values', () => {
    component.getControl('seriesName').setValue('Saga');
    component.getControl('seriesTotal').setValue(7);

    expect(component.fileCount).toBe(4);
    expect(component.enabledFields.has('seriesName')).toBe(true);
    expect(component.enabledFields.has('seriesTotal')).toBe(true);
    expect(component.hasEnabledFields).toBe(true);
  });

  it('adds trimmed autocomplete blur values once, clears the input, and enables the chip field', () => {
    const firstInput = blurAutoComplete('authors', '  Octavia Butler  ');
    const duplicateInput = blurAutoComplete('authors', 'Octavia Butler');
    const blankInput = blurAutoComplete('authors', '   ');

    expect(component.getControl('authors').value).toEqual(['Octavia Butler']);
    expect(component.enabledFields.has('authors')).toBe(true);
    expect(firstInput.value).toBe('');
    expect(duplicateInput.value).toBe('');
    expect(blankInput.value).toBe('   ');
  });

  it('toggles fields on and off explicitly', () => {
    component.toggleField('publisher');
    expect(component.isFieldEnabled('publisher')).toBe(true);

    component.toggleField('publisher');
    expect(component.isFieldEnabled('publisher')).toBe(false);
  });

  it('applies only enabled non-null fields and preserves merge mode in the dialog result', () => {
    component.getControl('seriesName').setValue('Saga');
    component.getControl('authors').setValue(['Octavia Butler', 'N. K. Jemisin']);
    component.toggleField('publisher');
    component.mergeArrays = false;

    component.apply();

    const result = dialogRef.close.mock.calls[0]?.[0] as BulkEditResult;

    expect(result.fields).toEqual({
      seriesName: 'Saga',
      authors: ['Octavia Butler', 'N. K. Jemisin'],
      publisher: '',
    });
    expect(result.enabledFields).toEqual(new Set(['seriesName', 'authors', 'publisher']));
    expect(result.mergeArrays).toBe(false);
  });

  it('closes the dialog with null when cancelled', () => {
    component.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith(null);
  });
});
