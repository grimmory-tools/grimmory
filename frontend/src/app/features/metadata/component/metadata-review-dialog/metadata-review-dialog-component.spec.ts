import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

import {BookService} from '../../../book/service/book.service';
import {FetchedMetadataProposalStatus, FetchedProposal, MetadataTaskService} from '../../../book/service/metadata-task';
import {BookMetadata} from '../../../book/model/book.model';
import {MetadataProgressService} from '../../../../shared/service/metadata-progress.service';
import {MetadataPickerComponent} from '../book-metadata-center/metadata-picker/metadata-picker.component';
import {MetadataReviewDialogComponent} from './metadata-review-dialog-component';

describe('MetadataReviewDialogComponent', () => {
  const close = vi.fn();
  const getTaskWithProposals = vi.fn();
  const updateProposalStatus = vi.fn(() => of(void 0));
  const deleteTask = vi.fn(() => of(void 0));
  const clearTask = vi.fn();
  const books = signal([{id: 11, title: 'Book 11'}]);
  const findBookById = vi.fn((bookId: number) => books().find(book => book.id === bookId));

  beforeEach(() => {
    close.mockClear();
    getTaskWithProposals.mockReset();
    updateProposalStatus.mockClear();
    deleteTask.mockClear();
    clearTask.mockClear();
    findBookById.mockClear();
  });

  function createProposal(overrides: Partial<FetchedProposal>): FetchedProposal {
    return {
      proposalId: 1,
      taskId: 'task-1',
      bookId: 11,
      fetchedAt: '2026-03-26T00:00:00Z',
      reviewedAt: null,
      reviewerUserId: null,
      status: FetchedMetadataProposalStatus.FETCHED,
      metadataJson: {} as BookMetadata,
      ...overrides,
    };
  }

  function createPickerStub() {
    return {
      onSave: vi.fn(),
      lockAll: vi.fn(),
      unlockAll: vi.fn(),
    } as unknown as MetadataPickerComponent;
  }

  function createComponent(taskId: string | undefined) {
    TestBed.configureTestingModule({
      providers: [
        {provide: DynamicDialogConfig, useValue: {data: taskId ? {taskId} : {}}},
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: MetadataTaskService, useValue: {getTaskWithProposals, updateProposalStatus, deleteTask}},
        {provide: BookService, useValue: {books, findBookById}},
        {provide: MetadataProgressService, useValue: {clearTask}},
      ]
    });

    return TestBed.runInInjectionContext(() => new MetadataReviewDialogComponent());
  }

  it('closes immediately when the dialog does not receive a task id', () => {
    const component = createComponent(undefined);

    component.ngOnInit();

    expect(close).toHaveBeenCalledOnce();
  });

  it('loads proposals for the requested task and exposes the current book', () => {
    getTaskWithProposals.mockReturnValue(of({
      proposals: [
        createProposal({proposalId: 5}),
      ],
    }));

    const component = createComponent('task-1');
    component.ngOnInit();
    TestBed.flushEffects();

    expect(getTaskWithProposals).toHaveBeenCalledWith('task-1');
    expect(component.currentProposal).toEqual(createProposal({proposalId: 5}));
    expect(component.currentBook()).toEqual({id: 11, title: 'Book 11'});
    expect(component.loading).toBe(false);
  });

  it('closes when loading the task proposals fails', () => {
    getTaskWithProposals.mockReturnValue(throwError(() => new Error('failed')));

    const component = createComponent('task-1');
    component.ngOnInit();

    expect(close).toHaveBeenCalledOnce();
  });

  it('saves the current proposal and clears progress when saving the last proposal', () => {
    getTaskWithProposals.mockReturnValue(of({proposals: []}));

    const component = createComponent('task-1');
    component.proposals.set([createProposal({proposalId: 5})]);
    component.pickerComponent = createPickerStub();

    component.onSave();

    expect(component.pickerComponent.onSave).toHaveBeenCalledOnce();
    expect(updateProposalStatus).toHaveBeenCalledWith('task-1', 5, 'ACCEPTED');
    expect(deleteTask).toHaveBeenCalledWith('task-1');
    expect(clearTask).toHaveBeenCalledWith('task-1');
  });

  it('does nothing when save is triggered without an active proposal', () => {
    getTaskWithProposals.mockReturnValue(of({proposals: []}));

    const component = createComponent('task-1');
    component.pickerComponent = createPickerStub();

    component.onSave();

    expect(component.pickerComponent.onSave).not.toHaveBeenCalled();
    expect(updateProposalStatus).not.toHaveBeenCalled();
    expect(deleteTask).not.toHaveBeenCalled();
    expect(clearTask).not.toHaveBeenCalled();
  });

  it('advances between proposals and closes when moving past the last one', () => {
    getTaskWithProposals.mockReturnValue(of({proposals: []}));

    const component = createComponent('task-1');
    component.proposals.set([
      createProposal({proposalId: 1, bookId: 11}),
      createProposal({proposalId: 2, bookId: 12}),
    ]);

    component.onNext();
    expect(component.currentProposal).toEqual(createProposal({proposalId: 2, bookId: 12}));

    component.onNext();
    expect(close).toHaveBeenCalledOnce();
  });

  it('delegates lock and unlock actions to the picker component', () => {
    const component = createComponent('task-1');
    component.pickerComponent = createPickerStub();

    component.lockAllMetadata();
    component.unlockAllMetadata();

    expect(component.pickerComponent.lockAll).toHaveBeenCalledOnce();
    expect(component.pickerComponent.unlockAll).toHaveBeenCalledOnce();
  });
});
