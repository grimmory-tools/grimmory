import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../model/metadata-batch-progress.model';
import {MetadataTaskService} from '../../features/book/service/metadata-task';
import {UserService} from '../../features/settings/user-management/user.service';
import {MetadataProgressService} from './metadata-progress.service';

describe('MetadataProgressService', () => {
  const adminUser = {
    permissions: {
      admin: true,
      canEditMetadata: false,
    },
  };

  let getActiveTasks: ReturnType<typeof vi.fn>;
  let currentUser: ReturnType<typeof signal<typeof adminUser | null>>;
  let service: MetadataProgressService;

  const taskA: MetadataBatchProgressNotification = {
    taskId: 'task-a',
    completed: 1,
    total: 4,
    message: 'running',
    status: MetadataBatchStatus.IN_PROGRESS,
    review: false,
  };

  const taskB: MetadataBatchProgressNotification = {
    taskId: 'task-b',
    completed: 4,
    total: 4,
    message: 'done',
    status: MetadataBatchStatus.COMPLETED,
    review: true,
  };

  beforeEach(() => {
    getActiveTasks = vi.fn(() => of([]));
    currentUser = signal<typeof adminUser | null>(adminUser);

    TestBed.configureTestingModule({
      providers: [
        MetadataProgressService,
        {
          provide: MetadataTaskService,
          useValue: {getActiveTasks},
        },
        {
          provide: UserService,
          useValue: {currentUser},
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('initializes active tasks once for users with metadata permissions', () => {
    getActiveTasks.mockReturnValue(of([taskA]));

    service = TestBed.inject(MetadataProgressService);
    TestBed.flushEffects();

    expect(getActiveTasks).toHaveBeenCalledOnce();
    expect(service.getActiveTasks()).toEqual({[taskA.taskId]: taskA});
  });

  it('does not initialize task loading when the current user lacks metadata permissions', () => {
    currentUser.set({
      permissions: {
        admin: false,
        canEditMetadata: false,
      },
    });

    service = TestBed.inject(MetadataProgressService);
    TestBed.flushEffects();

    expect(getActiveTasks).not.toHaveBeenCalled();
    expect(service.getActiveTasks()).toEqual({});
  });

  it('warns and keeps running when active-task initialization fails', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    getActiveTasks.mockReturnValue(throwError(() => new Error('boom')));

    service = TestBed.inject(MetadataProgressService);
    TestBed.flushEffects();

    expect(getActiveTasks).toHaveBeenCalledOnce();
    expect(warnSpy).toHaveBeenCalled();
    expect(service.getActiveTasks()).toEqual({});
  });

  it('publishes incoming progress updates and keeps the latest task state', () => {
    service = TestBed.inject(MetadataProgressService);

    const emissions: MetadataBatchProgressNotification[] = [];
    service.progressUpdates$.subscribe(value => emissions.push(value));

    service.handleIncomingProgress(taskA);
    service.handleIncomingProgress({...taskA, completed: 3});

    expect(emissions).toHaveLength(2);
    expect(service.getActiveTasks()).toEqual({
      [taskA.taskId]: {...taskA, completed: 3},
    });
  });

  it('clears tasks from the active task map', () => {
    service = TestBed.inject(MetadataProgressService);
    service.handleIncomingProgress(taskA);
    service.handleIncomingProgress(taskB);

    service.clearTask(taskA.taskId);

    expect(service.getActiveTasks()).toEqual({[taskB.taskId]: taskB});
  });

  it('unsubscribes cleanly on destroy', () => {
    service = TestBed.inject(MetadataProgressService);
    service.handleIncomingProgress(taskA);

    service.ngOnDestroy();

    expect(() => service.getActiveTasks()).not.toThrow();
  });
});
