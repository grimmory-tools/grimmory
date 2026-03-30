import {TestBed} from '@angular/core/testing';
import {describe, expect, it, vi} from 'vitest';
import {firstValueFrom, of, throwError} from 'rxjs';

import {TaskHelperService} from './task-helper.service';
import {TaskService, TaskType} from './task.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

describe('TaskHelperService', () => {
  it('schedules metadata refresh tasks', async () => {
    const startTask = vi.fn(() => of({type: TaskType.REFRESH_METADATA_MANUAL, status: 'ACCEPTED'}));
    const messageAdd = vi.fn();
    const translate = vi.fn((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        TaskHelperService,
        {provide: TaskService, useValue: {startTask}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });

    const service = TestBed.inject(TaskHelperService);

    await expect(firstValueFrom(service.refreshMetadataTask({libraryIds: [1]} as never))).resolves.toEqual({success: true});
    expect(startTask).toHaveBeenCalledWith({
      taskType: TaskType.REFRESH_METADATA_MANUAL,
      triggeredByCron: false,
      options: {libraryIds: [1]}
    });
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'settingsTasks.toast.metadataScheduled',
      detail: 'settingsTasks.toast.metadataScheduledDetail'
    });
  });

  it('maps task conflicts to the already running error toast', async () => {
    const startTask = vi.fn(() => throwError(() => ({status: 409})));
    const messageAdd = vi.fn();
    const translate = vi.fn((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        TaskHelperService,
        {provide: TaskService, useValue: {startTask}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });

    const service = TestBed.inject(TaskHelperService);

    await expect(firstValueFrom(service.refreshMetadataTask({libraryIds: [1]} as never))).resolves.toEqual({success: false});
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'settingsTasks.toast.alreadyRunning',
      life: 5000,
      detail: 'settingsTasks.toast.metadataAlreadyRunningDetail'
    });
  });

  it('maps non-conflict failures to the generic error toast', async () => {
    const startTask = vi.fn(() => throwError(() => ({status: 500})));
    const messageAdd = vi.fn();
    const translate = vi.fn((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        TaskHelperService,
        {provide: TaskService, useValue: {startTask}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });

    const service = TestBed.inject(TaskHelperService);

    await expect(firstValueFrom(service.refreshMetadataTask({libraryIds: [1]} as never))).resolves.toEqual({success: false});
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'settingsTasks.toast.metadataFailed',
      life: 5000,
      detail: 'settingsTasks.toast.metadataFailedDetail'
    });
  });
});
