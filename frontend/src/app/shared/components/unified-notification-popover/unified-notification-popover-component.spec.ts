import {Signal, signal, WritableSignal} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {BookdropFileService} from '../../../features/bookdrop/service/bookdrop-file.service';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {MetadataBatchStatus} from '../../model/metadata-batch-progress.model';
import {MetadataProgressService} from '../../service/metadata-progress.service';
import {LibraryImportProgressService} from '../../service/library-import-progress.service';
import {UnifiedNotificationBoxComponent} from './unified-notification-popover-component';

describe('UnifiedNotificationBoxComponent', () => {
  let fixture: ComponentFixture<UnifiedNotificationBoxComponent>;
  let component: UnifiedNotificationBoxComponent;
  let activeTasks$: BehaviorSubject<Record<string, unknown>>;
  let hasPendingFiles$: BehaviorSubject<boolean>;
  let hasActiveImport: WritableSignal<boolean>;

  beforeEach(async () => {
    activeTasks$ = new BehaviorSubject<Record<string, unknown>>({});
    hasPendingFiles$ = new BehaviorSubject(false);
    hasActiveImport = signal(false);

    await TestBed.configureTestingModule({
      imports: [UnifiedNotificationBoxComponent, getTranslocoModule()],
      providers: [
        {
          provide: MetadataProgressService,
          useValue: {activeTasks$},
        },
        {
          provide: BookdropFileService,
          useValue: {hasPendingFiles$},
        },
        {
          provide: LibraryImportProgressService,
          useValue: {hasActiveImport},
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UnifiedNotificationBoxComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('reports whether there are metadata tasks to show', () => {
    const popover = component as unknown as {hasMetadataTasks: Signal<boolean>};

    expect(popover.hasMetadataTasks()).toBe(false);

    activeTasks$.next({
      'task-1': {
        taskId: 'task-1',
        completed: 1,
        total: 2,
        message: 'running',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false,
      },
    });

    expect(popover.hasMetadataTasks()).toBe(true);
  });

  it('forwards the bookdrop pending-file signal', () => {
    const popover = component as unknown as {hasPendingBookdropFiles: Signal<boolean>};

    expect(popover.hasPendingBookdropFiles()).toBe(false);
    hasPendingFiles$.next(true);
    expect(popover.hasPendingBookdropFiles()).toBe(true);
  });

  it('forwards the active library import signal', () => {
    const popover = component as unknown as {hasActiveLibraryImport: Signal<boolean>};

    expect(popover.hasActiveLibraryImport()).toBe(false);
    hasActiveImport.set(true);
    expect(popover.hasActiveLibraryImport()).toBe(true);
  });
});
