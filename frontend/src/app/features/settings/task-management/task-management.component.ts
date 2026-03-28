import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Button} from 'primeng/button';
import {ProgressBar} from 'primeng/progressbar';
import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
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
import {finalize, forkJoin, Subscription} from 'rxjs';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-task-management',
  standalone: true,
  imports: [
    CommonModule,
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
export class TaskManagementComponent implements OnInit, OnDestroy {
  // Services
  private messageService = inject(MessageService);
  private taskService = inject(TaskService);
  private t = inject(TranslocoService);

  // State
  taskInfos: TaskInfo[] = [];
  taskHistories = new Map<string, TaskHistory>();
  loading = false;
  executingTasks = new Set<string>();
  private subscription?: Subscription;

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

  // Constants
  private readonly STALE_TASK_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
  protected readonly TaskType = TaskType;

  // ============================================================================
  // Lifecycle Hooks
  // ============================================================================

  ngOnInit(): void {
    this.loadTasks();
    this.subscribeToTaskProgress();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  // ============================================================================
  // Data Loading & Real-time Updates
  // ============================================================================

  loadTasks(): void {
    this.loading = true;

    forkJoin({
      available: this.taskService.getAvailableTasks(),
      latest: this.taskService.getLatestTasksForEachType()
    })
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: ({available, latest}) => {
          this.taskInfos = this.sortTasksByDisplayOrder(available);
          this.taskHistories.clear();
          latest.taskHistories.forEach(history => {
            this.taskHistories.set(history.type, history);
          });
        },
        error: (error) => {
          console.error('Error loading tasks:', error);
          this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.loadError'));
        }
      });
  }

  private subscribeToTaskProgress(): void {
    this.subscription = this.taskService.taskProgress$.subscribe(progress => {
      if (progress) {
        this.updateTaskWithProgress(progress);
      }
    });
  }

  private updateTaskWithProgress(progress: TaskProgressPayload): void {
    const existingHistory = this.taskHistories.get(progress.taskType);

    const updatedHistory: TaskHistory = {
      id: progress.taskId,
      type: progress.taskType,
      status: progress.taskStatus,
      progressPercentage: progress.progress,
      message: progress.message,
      createdAt: existingHistory?.createdAt || new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      completedAt: (progress.taskStatus === TaskStatus.COMPLETED || progress.taskStatus === TaskStatus.FAILED)
        ? new Date().toISOString()
        : existingHistory?.completedAt || null
    };

    this.taskHistories.set(progress.taskType, updatedHistory);

    if (progress.taskStatus === TaskStatus.COMPLETED || progress.taskStatus === TaskStatus.FAILED) {
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
    const history = this.taskHistories.get(taskType);
    return this.canRunTask(history) || this.isTaskStale(history);
  }

  executeTask(taskType: string): void {
    this.runTask(taskType);
  }

  runTask(type: string): void {
    const history = this.taskHistories.get(type);
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

  private runTaskWithOptions(type: string, options: LibraryRescanOptions | MetadataRefreshRequest | null): void {
    const request: TaskCreateRequest = {
      taskType: type as TaskType,
      triggeredByCron: false,
      options: options
    };

    const isAsync = TASK_TYPE_CONFIG[type as TaskType]?.async || false;

    this.executingTasks.add(type);
    this.taskService.startTask(request)
      .pipe(finalize(() => this.executingTasks.delete(type)))
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
    const history = this.taskHistories.get(taskType);
    if (!history?.id) {
      this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsTasks.toast.cancelNoId'));
      return;
    }

    this.executingTasks.add(taskType);
    this.taskService.cancelTask(history.id)
      .pipe(finalize(() => this.executingTasks.delete(taskType)))
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
    return this.executingTasks.has(taskType);
  }

  isTaskRunning(taskType: string): boolean {
    const history = this.taskHistories.get(taskType);
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
    this.cronValidationError = null;
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
    return taskInfo?.name || type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }

  getTaskDescription(type: string): string {
    const taskInfo = this.taskInfos.find(t => t.taskType === type);
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
      [TaskType.CLEANUP_TEMP_METADATA]: 'pi-file'
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
    return this.taskHistories.get(taskType);
  }

  getTaskProgressPercentage(taskType: string): number | null {
    return this.taskHistories.get(taskType)?.progressPercentage || null;
  }

  getTaskUpdatedAt(taskType: string): string | null {
    return this.taskHistories.get(taskType)?.updatedAt || null;
  }

  getTaskStatusMessage(taskType: string): string {
    const history = this.taskHistories.get(taskType);
    if (this.isTaskStale(history)) {
      return this.t.translate('settingsTasks.progress.stuckMessage');
    }
    return history?.message || this.t.translate('settingsTasks.progress.processing');
  }

  getLastRunMessage(taskType: string): string {
    const history = this.taskHistories.get(taskType);
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
    const history = this.taskHistories.get(taskType);
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
    const history = this.taskHistories.get(taskType);
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

  private showMessage(severity: 'success' | 'info' | 'warn' | 'error', summary: string, detail: string): void {
    this.messageService.add({
      severity,
      summary,
      detail
    });
  }
}
