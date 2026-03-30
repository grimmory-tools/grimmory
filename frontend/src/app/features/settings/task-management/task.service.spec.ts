import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {firstValueFrom} from 'rxjs';
import {skip} from 'rxjs/operators';

import {API_CONFIG} from '../../../core/config/api-config';
import {MetadataReplaceMode, TaskService, TaskStatus, TaskType} from './task.service';

describe('TaskService', () => {
  let service: TaskService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TaskService,
      ]
    });

    service = TestBed.inject(TaskService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads available tasks', async () => {
    const resultPromise = firstValueFrom(service.getAvailableTasks());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/tasks`);
    expect(request.request.method).toBe('GET');
    request.flush([{taskType: TaskType.CLEAR_PDF_CACHE, name: 'Clear cache', description: 'desc', parallel: false, async: false, cronSupported: false, cronConfig: null}]);

    await expect(resultPromise).resolves.toEqual([{taskType: TaskType.CLEAR_PDF_CACHE, name: 'Clear cache', description: 'desc', parallel: false, async: false, cronSupported: false, cronConfig: null}]);
  });

  it('starts tasks with the provided options', async () => {
    const requestPromise = firstValueFrom(service.startTask({
      taskType: TaskType.REFRESH_METADATA_MANUAL,
      options: {metadataReplaceMode: MetadataReplaceMode.REPLACE_ALL}
    }));

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/tasks/start`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      taskType: TaskType.REFRESH_METADATA_MANUAL,
      options: {metadataReplaceMode: MetadataReplaceMode.REPLACE_ALL}
    });
    request.flush({type: TaskType.REFRESH_METADATA_MANUAL, status: TaskStatus.ACCEPTED});

    await expect(requestPromise).resolves.toEqual({type: TaskType.REFRESH_METADATA_MANUAL, status: TaskStatus.ACCEPTED});
  });

  it('loads the latest task for each type', async () => {
    const requestPromise = firstValueFrom(service.getLatestTasksForEachType());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/tasks/last`);
    expect(request.request.method).toBe('GET');
    request.flush({taskHistories: []});

    await expect(requestPromise).resolves.toEqual({taskHistories: []});
  });

  it('cancels tasks', async () => {
    const requestPromise = firstValueFrom(service.cancelTask('task-1'));

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/tasks/task-1/cancel`);
    expect(request.request.method).toBe('DELETE');
    request.flush({taskId: 'task-1', cancelled: true, message: 'done'});

    await expect(requestPromise).resolves.toEqual({taskId: 'task-1', cancelled: true, message: 'done'});
  });

  it('updates cron config', async () => {
    const requestPromise = firstValueFrom(service.updateCronConfig('task-type', {cronExpression: '0 0 * * *', enabled: true}));

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/tasks/task-type/cron`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({cronExpression: '0 0 * * *', enabled: true});
    request.flush({id: 1, taskType: 'task-type', cronExpression: '0 0 * * *', enabled: true, options: null, createdAt: null, updatedAt: null});

    await expect(requestPromise).resolves.toEqual({id: 1, taskType: 'task-type', cronExpression: '0 0 * * *', enabled: true, options: null, createdAt: null, updatedAt: null});
  });

  it('publishes task progress updates', async () => {
    const progressPromise = firstValueFrom(service.taskProgress$.pipe(skip(1)));

    service.handleTaskProgress({taskId: 'task-1', taskType: 'refresh', message: 'working', progress: 25, taskStatus: TaskStatus.IN_PROGRESS});

    await expect(progressPromise).resolves.toEqual({taskId: 'task-1', taskType: 'refresh', message: 'working', progress: 25, taskStatus: TaskStatus.IN_PROGRESS});
  });
});
