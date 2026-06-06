import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BehaviorSubject, of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../../model/metadata-batch-progress.model';
import {MetadataTaskService} from '../../../features/book/service/metadata-task';
import {TaskService} from '../../../features/settings/task-management/task.service';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {MetadataProgressWidgetComponent} from './metadata-progress-widget-component';
import {MetadataProgressService} from '../../service/metadata-progress.service';
import {DialogLauncherService} from '../../services/dialog-launcher.service';

describe('MetadataProgressWidgetComponent', () => {
  let fixture: ComponentFixture<MetadataProgressWidgetComponent>;
  let component: MetadataProgressWidgetComponent;
  let activeTasksSubject: BehaviorSubject<Record<string, MetadataBatchProgressNotification>>;
  let metadataProgressService: {
    activeTasks$: BehaviorSubject<Record<string, MetadataBatchProgressNotification>>;
    clearTask: ReturnType<typeof vi.fn>;
  };
  let metadataTaskService: {deleteTask: ReturnType<typeof vi.fn>};
  let taskService: {cancelTask: ReturnType<typeof vi.fn>};
  let dialogLauncherService: {openMetadataReviewDialog: ReturnType<typeof vi.fn>};
  let messageService: {add: ReturnType<typeof vi.fn>};
  let translocoService: TranslocoService;

  const task: MetadataBatchProgressNotification = {
    taskId: 'task-1',
    completed: 2,
    total: 5,
    message: 'Running',
    status: MetadataBatchStatus.IN_PROGRESS,
    review: false,
  };

  beforeEach(async () => {
    activeTasksSubject = new BehaviorSubject<Record<string, MetadataBatchProgressNotification>>({});
    metadataProgressService = {
      activeTasks$: activeTasksSubject,
      clearTask: vi.fn(),
    };
    metadataTaskService = {
      deleteTask: vi.fn(() => of(void 0)),
    };
    taskService = {
      cancelTask: vi.fn(() => of({cancelled: true})),
    };
    dialogLauncherService = {
      openMetadataReviewDialog: vi.fn(),
    };
    messageService = {
      add: vi.fn(),
    };

    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [MetadataProgressWidgetComponent, getTranslocoModule()],
      providers: [
        {provide: MetadataProgressService, useValue: metadataProgressService},
        {provide: MetadataTaskService, useValue: metadataTaskService},
        {provide: TaskService, useValue: taskService},
        {provide: DialogLauncherService, useValue: dialogLauncherService},
        {provide: MessageService, useValue: messageService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataProgressWidgetComponent);
    component = fixture.componentInstance;
    translocoService = TestBed.inject(TranslocoService);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('tracks active tasks from the progress service and marks stalled tasks as errors', () => {
    component.ngOnInit();

    activeTasksSubject.next({[task.taskId]: task});
    expect(component.activeTasks).toEqual({[task.taskId]: task});

    vi.advanceTimersByTime(60000);

    expect(component.activeTasks[task.taskId]).toMatchObject({
      status: MetadataBatchStatus.ERROR,
      message: translocoService.translate('shared.metadataProgress.taskStalled'),
    });
  });

  it('computes progress percentages and tag severity values', () => {
    expect(component.getProgressPercent({...task, total: 0})).toBe(0);
    expect(component.getProgressPercent({...task, status: MetadataBatchStatus.COMPLETED})).toBe(100);
    expect(component.getProgressPercent(task)).toBe(40);
    expect(component.getTagSeverity(MetadataBatchStatus.IN_PROGRESS)).toBe('info');
    expect(component.getTagSeverity(MetadataBatchStatus.COMPLETED)).toBe('success');
    expect(component.getTagSeverity(MetadataBatchStatus.ERROR)).toBe('danger');
    expect(component.getTagSeverity(MetadataBatchStatus.CANCELLED)).toBe('warn');
  });

  it('clears a task through the metadata task API and local progress store', () => {
    component.ngOnInit();
    activeTasksSubject.next({[task.taskId]: task});

    component.clearTask(task.taskId);

    expect(metadataTaskService.deleteTask).toHaveBeenCalledWith(task.taskId);
    expect(metadataProgressService.clearTask).toHaveBeenCalledWith(task.taskId);
  });

  it('opens the review dialog for a task', () => {
    component.reviewTask(task.taskId);

    expect(dialogLauncherService.openMetadataReviewDialog).toHaveBeenCalledWith(task.taskId);
  });

  it('marks tasks cancelled and shows a success toast when cancellation is scheduled', () => {
    component.activeTasks = {[task.taskId]: task};

    component.cancelTask(task.taskId);

    expect(taskService.cancelTask).toHaveBeenCalledWith(task.taskId);
    expect(component.activeTasks[task.taskId]).toMatchObject({
      status: MetadataBatchStatus.CANCELLED,
      message: translocoService.translate('shared.metadataProgress.taskCancelled'),
    });
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: translocoService.translate('shared.metadataProgress.cancellationScheduledSummary'),
      detail: translocoService.translate('shared.metadataProgress.cancellationScheduledDetail'),
    });
  });

  it('shows an error toast when cancellation fails', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    taskService.cancelTask.mockReturnValueOnce(throwError(() => new Error('boom')));

    component.cancelTask(task.taskId);

    expect(console.error).toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: translocoService.translate('shared.metadataProgress.cancelFailedSummary'),
      detail: translocoService.translate('shared.metadataProgress.cancelFailedDetail'),
    });
  });

  it('returns translated status labels and clears timers on destroy', () => {
    component.ngOnInit();
    activeTasksSubject.next({[task.taskId]: task});

    expect(component.getStatusLabel(MetadataBatchStatus.ERROR)).toBe(
      translocoService.translate('shared.metadataProgress.statusError')
    );

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((component as any).timeoutHandles.size).toBe(1);

    component.ngOnDestroy();

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((component as any).timeoutHandles.size).toBe(0);
    expect(component.activeTasks[task.taskId]).toEqual(task);
  });
});
