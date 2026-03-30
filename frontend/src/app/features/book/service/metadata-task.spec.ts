import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../../../shared/model/metadata-batch-progress.model';
import {
  FetchedMetadataProposalStatus,
  MetadataFetchTask,
  MetadataTaskService,
} from './metadata-task';

describe('MetadataTaskService', () => {
  let service: MetadataTaskService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MetadataTaskService,
      ],
    });

    service = TestBed.inject(MetadataTaskService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('maps the wrapped task response when fetching a task with proposals', () => {
    const task: MetadataFetchTask = {
      id: 'task-1',
      status: 'IN_PROGRESS',
      completed: 2,
      totalBooks: 5,
      startedAt: '2026-03-26T00:00:00Z',
      completedAt: null,
      initiatedBy: 'alice',
      errorMessage: null,
      proposals: [
        {
          proposalId: 42,
          taskId: 'task-1',
          bookId: 9,
          fetchedAt: '2026-03-26T00:00:01Z',
          reviewedAt: null,
          reviewerUserId: null,
          status: FetchedMetadataProposalStatus.FETCHED,
          metadataJson: {title: 'Dune'} as MetadataFetchTask['proposals'][number]['metadataJson'],
        },
      ],
    };

    let result: MetadataFetchTask | undefined;
    service.getTaskWithProposals('task-1').subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/metadata/tasks/task-1'));
    expect(request.request.method).toBe('GET');
    request.flush({task});

    expect(result).toEqual(task);
  });

  it('deletes tasks by id', () => {
    let completed = false;
    service.deleteTask('task-2').subscribe(() => {
      completed = true;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/metadata/tasks/task-2'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(completed).toBe(true);
  });

  it('posts status updates with the requested query param', () => {
    let completed = false;
    service.updateProposalStatus('task-3', 17, 'ACCEPTED').subscribe(() => {
      completed = true;
    });

    const request = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/metadata/tasks/task-3/proposals/17/status')
      && req.params.get('status') === 'ACCEPTED'
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush(null);

    expect(completed).toBe(true);
  });

  it('fetches active tasks directly from the active endpoint', () => {
    const tasks: MetadataBatchProgressNotification[] = [
      {
        taskId: 'task-4',
        completed: 3,
        total: 8,
        message: 'still running',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false,
      },
    ];

    let result: MetadataBatchProgressNotification[] | undefined;
    service.getActiveTasks().subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/metadata/tasks/active'));
    expect(request.request.method).toBe('GET');
    request.flush(tasks);

    expect(result).toEqual(tasks);
  });
});
