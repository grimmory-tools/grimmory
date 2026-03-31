import {BehaviorSubject} from 'rxjs';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {BookdropFileService} from '../../../features/bookdrop/service/bookdrop-file.service';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {MetadataBatchStatus} from '../../model/metadata-batch-progress.model';
import {MetadataProgressService} from '../../service/metadata-progress.service';
import {UnifiedNotificationBoxComponent} from './unified-notification-popover-component';

describe('UnifiedNotificationBoxComponent', () => {
  let fixture: ComponentFixture<UnifiedNotificationBoxComponent>;
  let component: UnifiedNotificationBoxComponent;
  let activeTasks$: BehaviorSubject<Record<string, unknown>>;
  let hasPendingFiles$: BehaviorSubject<boolean>;

  beforeEach(async () => {
    activeTasks$ = new BehaviorSubject<Record<string, unknown>>({});
    hasPendingFiles$ = new BehaviorSubject(false);

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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UnifiedNotificationBoxComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('reports whether there are metadata tasks to show', () => {
    let hasMetadataTasks: boolean | undefined;
    component.hasMetadataTasks$.subscribe(value => {
      hasMetadataTasks = value;
    });

    expect(hasMetadataTasks).toBe(false);

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

    expect(hasMetadataTasks).toBe(true);
  });

  it('forwards the bookdrop pending-file signal', () => {
    let hasPendingFiles: boolean | undefined;
    component.hasPendingBookdropFiles$.subscribe(value => {
      hasPendingFiles = value;
    });

    expect(hasPendingFiles).toBe(false);
    hasPendingFiles$.next(true);
    expect(hasPendingFiles).toBe(true);
  });
});
