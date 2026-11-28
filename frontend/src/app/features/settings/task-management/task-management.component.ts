import {Component, computed, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {Button} from '@openng/optimus-ui/button';
import {ProgressBar} from '@openng/optimus-ui/progressbar';
import {MessageService} from '@openng/optimus-ui/api';
import {Select} from '@openng/optimus-ui/select';
import {FormsModule} from '@angular/forms';
import {
  LibraryRescanOptions,
  MetadataReplaceMode,
  TASK_TYPE_CONFIG,
  TaskCreateRequest,
  TaskCronConfigRequest,
  TaskHistory,
  TaskInfo,
  TaskProgressPayload,
  TaskService,
  TaskStatus,
  TaskType
} from './task.service';
import {MetadataRefreshRequest} from '../../metadata/model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import {BookDialogHelperService} from '../../book/service/book-dialog-helper.service';
import {LibraryService} from '../../book/service/library.service';
import {MetadataProgressService} from '../../../shared/service/metadata-progress.service';
import {MetadataTaskService} from '../../book/service/metadata-task';
import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../../../shared/model/metadata-batch-progress.model';
import {finalize, forkJoin, interval} from 'rxjs';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {ToggleSwitch} from '@openng/optimus-ui/toggleswitch';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-task-management',
  standalone: true,
  imports: [
    Button,
    ProgressBar,
    Select,
    FormsModule,
    ExternalDocLinkComponent,
    ToggleSwitch,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './task-management.component.html',
  styleUrl: './task-management.component.scss'
})
export class TaskManagementComponent implements OnInit {
  // Services
  private readonly messageService = inject(MessageService);
  private readonly taskService = inject(TaskService);
  private readonly bookDialogHelperService = inject(BookDialogHelperService, {optional: true});
  private readonly libraryService = inject(LibraryService, {optional: true});
  private readonly metadataProgressService = inject(MetadataProgressService, {optional: true});
  private readonly metadataTaskService = inject(MetadataTaskService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  // State
  taskInfos: TaskInfo[] = [];
  private readonly taskHistoriesByType = signal(new Map<string, TaskHistory>());
  readonly loading = signal(false);
  private readonly executingTaskTypes = signal(new Set<string>());
  readonly libraries = computed(() => this.libraryService?.libraries() ?? []);
  readonly activeMetadataTasks = this.metadataProgressService
    ? toSignal(this.metadataProgressService.activeTasks$, {initialValue: {}})
    : signal<Record<string, MetadataBatchProgressNotification>>({});
  readonly selectedRefreshLibraryId = signal<number | null>(null);
  private readonly metadataProgressSamples = new Map<string, {startCompleted: number; startAt: number; latestCompleted: number; latestAt: number}>();
  private readonly metadataEtaClock = signal(Date.now());

  // Metadata Replace Options
  metadataReplaceOptions = [
    {
      label: 'Update Missing Metadata Only (Recommended)',
      value: MetadataReplaceMode.REPLACE_MISSING,
      translationKey: 'settingsTasks.metadataReplace.replaceMissing',
      descriptionKey: 'settingsTasks.metadataReplace.replaceMissingDesc'
    },
    {
      label: 'Replace All Metadata (Overwrite Existing)',
      value: MetadataReplaceMode.REPLACE_ALL,
      translationKey: 'settingsTasks.metadataReplace.replaceAll',
      descriptionKey: 'settingsTasks.metadataReplace.replaceAllDesc'
    }
  ];
  selectedMetadataReplaceMode: MetadataReplaceMode = MetadataReplaceMode.REPLACE_MISSING;

  // Cron Editing State
  cronUpdating = false;
  editingCronTaskType: string | null = null;
  editingCronExpression: string = '';
  cronValidationError: string | null = null;
  cronEditorMode: 'simple' | 'advanced' = 'simple';
  cronFrequency: 'minutes' | 'hourly' | 'daily' | 'weekly' | 'monthly' = 'daily';
  cronIntervalMinutes = 15;
  cronTime = '02:00';
  cronWeekday = 1;
  cronMonthDay = 1;

  // Constants
  private readonly STALE_TASK_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
  protected readonly TaskType = TaskType;

  // ============================================================================
  // Lifecycle Hooks
  // ============================================================================

  ngOnInit(): void {
    this.loadTasks();
    this.subscribeToTaskProgress();
    this.metadataProgressService?.activeTasks$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(tasks => this.recordMetadataProgressSamples(tasks));
    interval(15_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.metadataEtaClock.set(Date.now()));
  }

  // ============================================================================
  // Data Loading & Real-time Updates
  // ============================================================================

  loadTasks(): void {
    this.loading.set(true);

    forkJoin({
      available: this.taskService.getAvailableTasks(),
      latest: this.taskService.getLatestTasksForEachType()
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({available, latest}) => {
          this.taskInfos = this.sortTasksByDisplayOrder(available);
          this.taskHistoriesByType.set(new Map<string, TaskHistory>(latest.taskHistories.map(history => [history.type, history])));
        },
        error: (error) => {
          console.error('Error loading tasks:', error);
          this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.loadError'));
        }
      });
  }

  private subscribeToTaskProgress(): void {
    this.taskService.taskProgress$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(progress => {
        if (progress) {
          this.updateTaskWithProgress(progress);
        }
      });
  }

  private updateTaskWithProgress(progress: TaskProgressPayload): void {
    const now = new Date().toISOString();
    const isTerminalStatus = progress.taskStatus === TaskStatus.COMPLETED
      || progress.taskStatus === TaskStatus.FAILED
      || progress.taskStatus === TaskStatus.CANCELLED;

    this.taskHistoriesByType.update(histories => {
      const existingHistory = histories.get(progress.taskType);

      return new Map(histories).set(progress.taskType, {
        id: progress.taskId,
        type: progress.taskType,
        status: progress.taskStatus,
        progressPercentage: progress.progress,
        message: progress.message,
        createdAt: existingHistory?.createdAt ?? now,
        updatedAt: now,
        completedAt: isTerminalStatus
          ? now
          : existingHistory?.completedAt ?? null
      });
    });

    if (isTerminalStatus) {
      setTimeout(() => this.loadTasks(), 1000);
    }
  }

  private sortTasksByDisplayOrder(tasks: TaskInfo[]): TaskInfo[] {
    return tasks.sort((a, b) => {
      const orderA = TASK_TYPE_CONFIG[a.taskType as TaskType]?.displayOrder ?? 999;
      const orderB = TASK_TYPE_CONFIG[b.taskType as TaskType]?.displayOrder ?? 999;
      return orderA - orderB;
    });
  }

  // ============================================================================
  // Task Execution Operations
  // ============================================================================

  canExecuteTask(taskType: string): boolean {
    const history = this.getTaskHistory(taskType);
    return this.canRunTask(history) || this.isTaskStale(history);
  }

  executeTask(taskType: string): void {
    this.runTask(taskType);
  }

  runTask(type: string): void {
    const history = this.getTaskHistory(type);
    if (!this.canRunTask(history) && !this.isTaskStale(history)) {
      this.showMessage('warn', this.t.translate('settingsTasks.toast.alreadyRunning'), this.t.translate('settingsTasks.toast.alreadyRunningDetail'));
      return;
    }

    let options = null;

    if (type === TaskType.REFRESH_LIBRARY_METADATA) {
      options = {
        metadataReplaceMode: this.selectedMetadataReplaceMode
      };
    }

    this.runTaskWithOptions(type, options);
  }

  openProviderMetadataRefresh(): void {
    const libraryId = this.getSelectedRefreshLibraryId();
    if (libraryId == null || !this.bookDialogHelperService) {
      return;
    }
    void this.bookDialogHelperService.openMetadataRefreshDialogWithContext({
      metadataRefreshType: MetadataRefreshType.LIBRARY,
      libraryId,
    });
  }

  getSelectedRefreshLibraryId(): number | null {
    return this.selectedRefreshLibraryId()
      ?? this.libraries().find(library => library.id != null)?.id
      ?? null;
  }

  setSelectedRefreshLibraryId(libraryId: number | null): void {
    this.selectedRefreshLibraryId.set(libraryId);
  }

  getActiveMetadataRefresh(): MetadataBatchProgressNotification | null {
    const tasks = Object.values(this.activeMetadataTasks());
    return tasks.find(task => task.status === MetadataBatchStatus.IN_PROGRESS) ?? null;
  }

  getMetadataRefreshToDisplay(): MetadataBatchProgressNotification | null {
    const tasks = Object.values(this.activeMetadataTasks());
    return this.getActiveMetadataRefresh()
      ?? [...tasks].reverse().find(task => task.status === MetadataBatchStatus.COMPLETED
        || task.status === MetadataBatchStatus.ERROR
        || task.status === MetadataBatchStatus.CANCELLED)
      ?? null;
  }

  isMetadataRefreshInProgress(task: MetadataBatchProgressNotification): boolean {
    return task.status === MetadataBatchStatus.IN_PROGRESS;
  }

  cancelAllMetadataBatches(): void {
    this.metadataTaskService.cancelAllInProgressTasks().subscribe({
      next: (cancelled) => {
        cancelled.forEach(progress => this.metadataProgressService?.handleIncomingProgress(progress));
        this.showMessage('success', this.t.translate('settingsTasks.toast.taskCancelled'),
          this.t.translate('settingsTasks.progress.metadataBatchesCancelled', {count: cancelled.length}));
        this.loadTasks();
      },
      error: (error) => {
        console.error('Error cancelling metadata batches:', error);
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.cancelError'));
      }
    });
  }

  getMetadataRefreshStatusKey(task: MetadataBatchProgressNotification): string {
    return `metadataBatchStatus.${task.status}`;
  }

  getMetadataRefreshMessage(task: MetadataBatchProgressNotification): string {
    return task.status === MetadataBatchStatus.COMPLETED
      ? this.t.translate('settingsTasks.progress.metadataBatchSuccessMessage')
      : task.message;
  }

  getMetadataBatchProgressPercent(task: MetadataBatchProgressNotification): number {
    if (task.total <= 0) return 0;
    return Math.min(100, Math.round((task.completed / task.total) * 100));
  }

  getMetadataBatchEta(task: MetadataBatchProgressNotification): string | null {
    // Read the clock signal so Angular refreshes the estimate while progress is steady.
    const now = this.metadataEtaClock();
    const samples = this.metadataProgressSamples.get(task.taskId);
    if (!samples || now - samples.latestAt > 2 * 60_000 || samples.latestCompleted - samples.startCompleted < 3) {
      return null;
    }

    const elapsedSeconds = (samples.latestAt - samples.startAt) / 1000;
    if (elapsedSeconds < 30) {
      return null;
    }

    const booksPerSecond = (samples.latestCompleted - samples.startCompleted) / elapsedSeconds;
    const remainingSeconds = Math.ceil((task.total - task.completed) / booksPerSecond);
    if (!Number.isFinite(remainingSeconds) || remainingSeconds < 0) {
      return null;
    }

    return this.formatMetadataEta(remainingSeconds);
  }

  private recordMetadataProgressSamples(tasks: Record<string, MetadataBatchProgressNotification>): void {
    const now = Date.now();
    for (const task of Object.values(tasks)) {
      if (task.status !== MetadataBatchStatus.IN_PROGRESS) {
        continue;
      }
      const samples = this.metadataProgressSamples.get(task.taskId);
      if (!samples) {
        this.metadataProgressSamples.set(task.taskId, {
          startCompleted: task.completed,
          startAt: now,
          latestCompleted: task.completed,
          latestAt: now
        });
      } else if (samples.latestCompleted !== task.completed) {
        samples.latestCompleted = task.completed;
        samples.latestAt = now;
      }
    }
  }

  private formatMetadataEta(seconds: number): string {
    const days = Math.floor(seconds / 86_400);
    const hours = Math.floor((seconds % 86_400) / 3_600);
    const minutes = Math.ceil((seconds % 3_600) / 60);
    const parts: string[] = [];
    if (days > 0) parts.push(this.t.translate('settingsTasks.progress.etaDays', {count: days}));
    if (hours > 0) parts.push(this.t.translate('settingsTasks.progress.etaHours', {count: hours}));
    if (minutes > 0 || parts.length === 0) parts.push(this.t.translate('settingsTasks.progress.etaMinutes', {count: minutes}));
    return parts.slice(0, 2).join(' ');
  }

  private runTaskWithOptions(type: string, options: LibraryRescanOptions | MetadataRefreshRequest | null): void {
    const request: TaskCreateRequest = {
      taskType: type as TaskType,
      triggeredByCron: false,
      options: options
    };

    const isAsync = TASK_TYPE_CONFIG[type as TaskType]?.async || false;

    this.addExecutingTask(type);
    this.taskService.startTask(request)
      .pipe(finalize(() => this.removeExecutingTask(type)))
      .subscribe({
        next: (response) => {
          const name = this.getTaskDisplayName(type);
          if (isAsync) {
            this.showMessage('info', this.t.translate('settingsTasks.toast.taskQueued'), this.t.translate('settingsTasks.toast.taskQueuedDetail', {name}));
          } else {
            if (response.status === TaskStatus.COMPLETED) {
              this.showMessage('success', this.t.translate('settingsTasks.toast.taskCompleted'), this.t.translate('settingsTasks.toast.taskCompletedDetail', {name}));
            } else if (response.status === TaskStatus.FAILED) {
              this.showMessage('error', this.t.translate('settingsTasks.toast.taskFailed'), response.message || this.t.translate('settingsTasks.toast.taskFailedDetail', {name}));
            } else {
              this.showMessage('success', this.t.translate('settingsTasks.toast.taskStarted'), this.t.translate('settingsTasks.toast.taskStartedDetail', {name}));
            }
          }
          this.loadTasks();
        },
        error: (error) => {
          console.error('Error starting task:', error);
          this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.startError', {name: this.getTaskDisplayName(type)}));
        }
      });
  }

  cancelTask(taskType: string): void {
    const history = this.getTaskHistory(taskType);
    if (!history?.id) {
      this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.cancelNoId'));
      return;
    }

    this.addExecutingTask(taskType);
    this.taskService.cancelTask(history.id)
      .pipe(finalize(() => this.removeExecutingTask(taskType)))
      .subscribe({
        next: (response) => {
          if (response.cancelled) {
            this.showMessage('success', this.t.translate('settingsTasks.toast.taskCancelled'), response.message || this.t.translate('settingsTasks.toast.cancelledDetail'));
            this.loadTasks();
          } else {
            this.showMessage('error', this.t.translate('settingsTasks.toast.cancelFailed'), response.message || this.t.translate('settingsTasks.toast.cancelError'));
          }
        },
        error: (error) => {
          console.error('Error cancelling task:', error);
          this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.cancelError'));
          this.loadTasks();
        }
      });
  }

  canRunTask(history: TaskHistory | undefined): boolean {
    return !history?.status || history.status === TaskStatus.COMPLETED || history.status === TaskStatus.FAILED || history.status === TaskStatus.CANCELLED;
  }

  canCancelTask(history: TaskHistory | undefined): boolean {
    return history?.status === TaskStatus.IN_PROGRESS || history?.status === TaskStatus.PENDING;
  }

  isTaskExecuting(taskType: string): boolean {
    return this.executingTaskTypes().has(taskType);
  }

  isTaskRunning(taskType: string): boolean {
    const history = this.getTaskHistory(taskType);
    return history?.status === TaskStatus.IN_PROGRESS || history?.status === TaskStatus.PENDING;
  }

  isTaskStale(history: TaskHistory | undefined): boolean {
    if (!history || !this.isTaskRunningForHistory(history) || !history.updatedAt) {
      return false;
    }
    const lastUpdate = new Date(history.updatedAt).getTime();
    const now = Date.now();
    return (now - lastUpdate) > this.STALE_TASK_THRESHOLD_MS;
  }

  private isTaskRunningForHistory(history: TaskHistory): boolean {
    return history.status === TaskStatus.IN_PROGRESS || history.status === TaskStatus.PENDING;
  }

  // ============================================================================
  // Cron Configuration Management
  // ============================================================================

  isCronSupported(taskType: string): boolean {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return taskInfo?.cronSupported || false;
  }

  getCronConfig(taskType: string): { enabled?: boolean; cronExpression?: string } | null | undefined {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    if (!taskInfo?.cronConfig) return null;

    const cronConfig = taskInfo.cronConfig;
    return {
      enabled: cronConfig.enabled,
      cronExpression: cronConfig.cronExpression ?? undefined
    };
  }

  getCronDescription(taskType: string): string {
    const expression = this.getCronConfig(taskType)?.cronExpression;
    if (!expression) return this.t.translate('settingsTasks.cron.noSchedule');
    return this.describeCronExpression(expression);
  }

  describeCronExpression(expression: string): string {
    const parts = expression.trim().split(/\s+/);
    if (parts.length !== 6) return expression;

    const [, minute, hour, day, month, weekday] = parts;
    if (minute.startsWith('*/') && hour === '*' && day === '*' && month === '*' && weekday === '*') {
      return this.t.translate('settingsTasks.cron.everyMinutes', {minutes: minute.slice(2)});
    }
    if (hour === '*' && day === '*' && month === '*' && weekday === '*') {
      return this.t.translate('settingsTasks.cron.hourlyAt', {minute: minute.padStart(2, '0')});
    }
    if (day === '*' && month === '*' && weekday === '*') {
      return this.t.translate('settingsTasks.cron.dailyAt', {time: `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`});
    }
    if (day === '*' && month === '*' && weekday !== '*') {
      const weekdayKey = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'][Number(weekday) % 7];
      return this.t.translate('settingsTasks.cron.weeklyAt', {
        day: this.t.translate(`settingsTasks.cron.weekdays.${weekdayKey}`),
        time: `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`
      });
    }
    if (month === '*' && weekday === '*' && /^\d+$/.test(day)) {
      return this.t.translate('settingsTasks.cron.monthlyAt', {
        day,
        time: `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`
      });
    }
    return expression;
  }

  toggleCronEnabled(taskType: string): void {
    const cronConfig = this.getCronConfig(taskType);
    if (!cronConfig) return;

    const request: TaskCronConfigRequest = {
      enabled: !(cronConfig.enabled ?? false)
    };

    this.updateCronConfig(taskType, request);
  }

  isEditingCron(taskType: string): boolean {
    return this.editingCronTaskType === taskType;
  }

  startEditingCron(taskType: string): void {
    const cronConfig = this.getCronConfig(taskType);
    this.editingCronTaskType = taskType;
    this.editingCronExpression = cronConfig?.cronExpression || '';
    this.parseCronForEditor(this.editingCronExpression);
    this.cronValidationError = null;
    if (this.cronEditorMode === 'advanced') this.validateCronExpression(this.editingCronExpression);
    else this.updateSimpleCronExpression();
  }

  onCronEditorModeChange(): void {
    if (this.cronEditorMode === 'simple') {
      this.updateSimpleCronExpression();
    } else {
      this.cronValidationError = null;
    }
  }

  onSimpleCronChange(): void {
    this.updateSimpleCronExpression();
  }

  private parseCronForEditor(expression: string): void {
    const parts = expression.trim().split(/\s+/);
    this.cronEditorMode = 'simple';
    this.cronFrequency = 'daily';
    this.cronIntervalMinutes = 15;
    this.cronTime = '02:00';
    this.cronWeekday = 1;
    this.cronMonthDay = 1;
    if (parts.length !== 6) return;

    const [, minute, hour, day, month, weekday] = parts;
    if (/^\*\/([0-9]+)$/.test(minute) && hour === '*' && day === '*' && month === '*' && weekday === '*') {
      const interval = Number(minute.slice(2));
      if (![5, 10, 15, 30].includes(interval)) {
        this.cronEditorMode = 'advanced';
        return;
      }
      this.cronFrequency = 'minutes';
      this.cronIntervalMinutes = interval;
    } else if (hour === '*' && day === '*' && month === '*' && weekday === '*') {
      this.cronFrequency = 'hourly';
      this.cronTime = `00:${minute.padStart(2, '0')}`;
    } else if (day === '*' && month === '*' && weekday === '*') {
      this.cronFrequency = 'daily';
      this.cronTime = `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
    } else if (day === '*' && month === '*' && /^\d+$/.test(weekday)) {
      this.cronFrequency = 'weekly';
      this.cronWeekday = Number(weekday) % 7;
      this.cronTime = `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
    } else if (month === '*' && /^\d+$/.test(day) && (weekday === '*' || weekday === '?')) {
      this.cronFrequency = 'monthly';
      this.cronMonthDay = Number(day);
      this.cronTime = `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
    } else {
      this.cronEditorMode = 'advanced';
    }
  }

  private updateSimpleCronExpression(): void {
    const [hourText, minuteText] = this.cronTime.split(':');
    const hour = Number(hourText) || 0;
    const minute = Number(minuteText) || 0;
    if (this.cronFrequency === 'monthly' && (!Number.isInteger(this.cronMonthDay) || this.cronMonthDay < 1 || this.cronMonthDay > 31)) {
      this.editingCronExpression = '';
      this.cronValidationError = this.t.translate('settingsTasks.cron.invalidDayOfMonth');
      return;
    }
    switch (this.cronFrequency) {
      case 'minutes':
        this.editingCronExpression = `0 */${this.cronIntervalMinutes} * * * *`;
        break;
      case 'hourly':
        this.editingCronExpression = `0 ${minute} * * * *`;
        break;
      case 'daily':
        this.editingCronExpression = `0 ${minute} ${hour} * * *`;
        break;
      case 'weekly':
        this.editingCronExpression = `0 ${minute} ${hour} * * ${this.cronWeekday}`;
        break;
      case 'monthly':
        this.editingCronExpression = `0 ${minute} ${hour} ${this.cronMonthDay} * ?`;
        break;
    }
    this.validateCronExpression(this.editingCronExpression);
  }

  cancelEditingCron(): void {
    this.editingCronTaskType = null;
    this.editingCronExpression = '';
    this.cronValidationError = null;
  }

  onCronExpressionChange(): void {
    this.validateCronExpression(this.editingCronExpression);
  }

  saveCronExpression(taskType: string): void {
    if (this.cronValidationError) {
      return;
    }

    const expression = this.editingCronExpression.trim() || null;
    this.updateCronExpression(taskType, expression);
    this.cancelEditingCron();
  }

  updateCronExpression(taskType: string, expression: string | null): void {
    const request: TaskCronConfigRequest = {
      cronExpression: expression
    };

    this.updateCronConfig(taskType, request);
  }

  private updateCronConfig(taskType: string, request: TaskCronConfigRequest): void {
    this.cronUpdating = true;
    this.taskService.updateCronConfig(taskType, request)
      .pipe(finalize(() => this.cronUpdating = false))
      .subscribe({
        next: (updatedConfig) => {
          const taskInfoIndex = this.taskInfos.findIndex(t => t.taskType === taskType);
          if (taskInfoIndex !== -1) {
            this.taskInfos[taskInfoIndex].cronConfig = updatedConfig;
          }
          this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsTasks.cron.updateSuccess'));
        },
        error: (error) => {
          console.error('Error updating cron config:', error);
          this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.cron.updateError'));
        }
      });
  }

  // ============================================================================
  // Cron Validation
  // ============================================================================

  private validateCronExpression(expression: string): void {
    if (!expression || expression.trim() === '') {
      this.cronValidationError = null;
      return;
    }

    const trimmed = expression.trim();
    const parts = trimmed.split(/\s+/);

    if (parts.length !== 6) {
      this.cronValidationError = this.t.translate('settingsTasks.cron.validationPrefix');
      return;
    }

    const validations = [
      {field: parts[0], name: 'Seconds', range: [0, 59]},
      {field: parts[1], name: 'Minutes', range: [0, 59]},
      {field: parts[2], name: 'Hours', range: [0, 23]},
      {field: parts[3], name: 'Day of Month', range: [1, 31]},
      {field: parts[4], name: 'Month', range: [1, 12]},
      {field: parts[5], name: 'Day of Week', range: [0, 7]}
    ];

    for (const validation of validations) {
      if (!this.isValidCronField(validation.field, validation.range[0], validation.range[1])) {
        this.cronValidationError = this.t.translate('settingsTasks.cron.invalidField', {name: validation.name, value: validation.field});
        return;
      }
    }

    this.cronValidationError = null;
  }

  private isValidCronField(field: string, min: number, max: number): boolean {
    if (field === '*' || field === '?') {
      return true;
    }

    if (field.includes('-')) {
      const [start, end] = field.split('-').map(Number);
      return !isNaN(start) && !isNaN(end) && start >= min && end <= max && start <= end;
    }

    if (field.includes('/')) {
      const [range, step] = field.split('/');
      const stepNum = Number(step);
      if (isNaN(stepNum) || stepNum <= 0) return false;

      if (range === '*') return true;
      if (range.includes('-')) {
        const [start, end] = range.split('-').map(Number);
        return !isNaN(start) && !isNaN(end) && start >= min && end <= max;
      }
      return false;
    }

    if (field.includes(',')) {
      const values = field.split(',').map(Number);
      return values.every(val => !isNaN(val) && val >= min && val <= max);
    }

    const num = Number(field);
    return !isNaN(num) && num >= min && num <= max;
  }

  // ============================================================================
  // UI Helper Methods - Task Information
  // ============================================================================

  getTaskDisplayName(type: string): string {
    const taskInfo = this.taskInfos.find(t => t.taskType === type);
    if (Object.values(TaskType).some(taskType => taskType === type)) {
      const key = `settingsTasks.taskTypes.${type}.name`;
      const translated = this.t.translate(key);
      if (translated !== key) return translated;
    }
    return taskInfo?.name || type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }

  getTaskDescription(type: string): string {
    const taskInfo = this.taskInfos.find(t => t.taskType === type);
    if (Object.values(TaskType).some(taskType => taskType === type)) {
      const key = `settingsTasks.taskTypes.${type}.description`;
      const translated = this.t.translate(key);
      if (translated !== key) return translated;
    }
    return taskInfo?.description || 'System maintenance task.';
  }

  getTaskDisplayOrder(type: string): number {
    return TASK_TYPE_CONFIG[type as TaskType]?.displayOrder ?? 999;
  }

  getTaskLabel(taskType: string): string {
    return `${this.getTaskDisplayOrder(taskType)}. ${this.getTaskDisplayName(taskType)}`;
  }

  getTaskIcon(taskType: string): string {
    const icons: Record<string, string> = {
      [TaskType.CLEAR_PDF_CACHE]: 'pi-database',
      [TaskType.REFRESH_LIBRARY_METADATA]: 'pi-refresh',
      [TaskType.UPDATE_BOOK_RECOMMENDATIONS]: 'pi-sparkles',
      [TaskType.CLEANUP_DELETED_BOOKS]: 'pi-trash',
      [TaskType.SYNC_LIBRARY_FILES]: 'pi-sync',
      [TaskType.BOOKDROP_PERIODIC_SCANNING]: 'pi-inbox',
      [TaskType.CLEANUP_TEMP_METADATA]: 'pi-file',
      [TaskType.REPAIR_EPUB_CONTAINERS]: 'pi-wrench'
    };
    return icons[taskType] || 'pi-cog';
  }

  getTaskMetadata(taskType: string): string | null {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return taskInfo?.metadata || null;
  }

  hasMetadata(taskType: string): boolean {
    const taskInfo = this.taskInfos.find(t => t.taskType === taskType);
    return !!taskInfo?.metadata && taskInfo.metadata.trim().length > 0;
  }

  getMetadataIcon(taskType: string): string {
    const icons: Record<string, string> = {
      [TaskType.CLEAR_PDF_CACHE]: 'pi-database',
      [TaskType.CLEANUP_DELETED_BOOKS]: 'pi-trash',
      [TaskType.CLEANUP_TEMP_METADATA]: 'pi-file'
    };
    return icons[taskType] || 'pi-info-circle';
  }

  // ============================================================================
  // UI Helper Methods - Task History & Status
  // ============================================================================

  getTaskHistory(taskType: string): TaskHistory | undefined {
    return this.taskHistoriesByType().get(taskType);
  }

  getTaskProgressPercentage(taskType: string): number | null {
    return this.getTaskHistory(taskType)?.progressPercentage ?? null;
  }

  shouldShowTaskProgressBar(taskType: string): boolean {
    return this.getTaskProgressPercentage(taskType) !== null
      && !(taskType === TaskType.REFRESH_LIBRARY_METADATA && this.getMetadataRefreshToDisplay() !== null);
  }

  getTaskUpdatedAt(taskType: string): string | null {
    return this.getTaskHistory(taskType)?.updatedAt || null;
  }

  getTaskStatusMessage(taskType: string): string {
    const history = this.getTaskHistory(taskType);
    if (this.isTaskStale(history)) {
      return this.t.translate('settingsTasks.progress.stuckMessage');
    }
    return history?.message || this.t.translate('settingsTasks.progress.processing');
  }

  getLastRunMessage(taskType: string): string {
    const history = this.getTaskHistory(taskType);
    if (!history?.completedAt && !history?.updatedAt) {
      return this.t.translate('settingsTasks.status.neverRun');
    }

    const dateStr = history.completedAt || history.updatedAt;
    if (!dateStr) return this.t.translate('settingsTasks.status.neverRun');

    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return this.t.translate('settingsTasks.status.justNow');
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
  }

  getLastRunInfoClass(taskType: string): string {
    const history = this.getTaskHistory(taskType);
    if (!history?.status) return 'info';

    switch (history.status) {
      case TaskStatus.COMPLETED:
        return 'success';
      case TaskStatus.FAILED:
        return 'error';
      case TaskStatus.CANCELLED:
        return 'warning';
      default:
        return 'info';
    }
  }

  // ============================================================================
  // UI Helper Methods - Buttons & Icons
  // ============================================================================

  getTaskButtonIcon(taskType: string): string {
    if (this.isTaskExecuting(taskType)) {
      return 'pi pi-spinner pi-spin';
    }
    return 'pi ' + this.getTaskIcon(taskType);
  }

  getTaskButtonLabel(taskType: string): string {
    const history = this.getTaskHistory(taskType);
    if (this.isTaskStale(history)) {
      return this.t.translate('settingsTasks.buttons.rerun');
    }
    return this.t.translate('settingsTasks.buttons.run');
  }

  getCancelButtonIcon(taskType: string): string {
    if (this.isTaskExecuting(taskType)) {
      return 'pi pi-spinner pi-spin';
    }
    return 'pi pi-times';
  }

  // ============================================================================
  // UI Helper Methods - Metadata Replace
  // ============================================================================

  getMetadataReplaceDescription(mode: MetadataReplaceMode): string {
    switch (mode) {
      case MetadataReplaceMode.REPLACE_MISSING:
        return this.t.translate('settingsTasks.metadataReplace.replaceMissingDesc');
      case MetadataReplaceMode.REPLACE_ALL:
        return this.t.translate('settingsTasks.metadataReplace.replaceAllDesc');
      default:
        return '';
    }
  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  private addExecutingTask(taskType: string): void {
    this.executingTaskTypes.update(tasks => new Set(tasks).add(taskType));
  }

  private removeExecutingTask(taskType: string): void {
    this.executingTaskTypes.update(tasks => {
      const updatedTasks = new Set(tasks);
      updatedTasks.delete(taskType);
      return updatedTasks;
    });
  }

  private showMessage(severity: 'success' | 'info' | 'warn' | 'error', summary: string, detail: string): void {
    this.messageService.add({
      severity,
      summary,
      detail
    });
  }
}
