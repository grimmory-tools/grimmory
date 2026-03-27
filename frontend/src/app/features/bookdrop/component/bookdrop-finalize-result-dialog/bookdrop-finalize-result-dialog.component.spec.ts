import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BookdropFinalizeResultDialogComponent} from './bookdrop-finalize-result-dialog.component';

describe('BookdropFinalizeResultDialogComponent', () => {
  let fixture: ComponentFixture<BookdropFinalizeResultDialogComponent>;
  let component: BookdropFinalizeResultDialogComponent;
  let dialogRef: {close: ReturnType<typeof vi.fn>};
  const result = {
    totalFiles: 4,
    successfulFiles: 3,
    failedFiles: 1,
    finishedAt: '2026-03-26T00:00:00Z',
  };

  beforeEach(async () => {
    dialogRef = {
      close: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [BookdropFinalizeResultDialogComponent, getTranslocoModule()],
      providers: [
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: DynamicDialogConfig, useValue: {data: {result}}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookdropFinalizeResultDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('hydrates the finalize result from the dialog config', () => {
    expect(component.result).toEqual(result);
  });

  it('closes the dialog when the component is destroyed', () => {
    component.ngOnDestroy();

    expect(dialogRef.close).toHaveBeenCalledOnce();
  });
});
