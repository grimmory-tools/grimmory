import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {KeyValuePipe} from '@angular/common';
import {ProgressBarModule} from 'primeng/progressbar';
import {ButtonModule} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {Tooltip} from 'primeng/tooltip';
import {MessageService} from 'primeng/api';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

import {MetadataBatchProgressNotification, MetadataBatchStatus} from '../../model/metadata-batch-progress.model';
import {MetadataProgressService} from '../../service/metadata-progress.service';
import {MetadataTaskService} from '../../../features/book/service/metadata-task';
import {Tag} from 'primeng/tag';
import {TaskService} from '../../../features/settings/task-management/task.service';
import {DialogLauncherService} from '../../services/dialog-launcher.service';

@Component({
  selector: 'app-metadata-progress-widget',
  templateUrl: './metadata-progress-widget-component.html',
  styleUrls: ['./metadata-progress-widget-component.scss'],
  standalone: true,
  imports: [KeyValuePipe, ProgressBarModule, ButtonModule, Divider, Tooltip, Tag, TranslocoDirective]
})
export class MetadataProgressWidgetComponent implements OnInit, OnDestroy {
  activeTasks: Record<string, MetadataBatchProgressNotification> = {};

  private destroy$ = new Subject<void>();
  private dialogLauncherService = inject(DialogLauncherService);
  private metadataProgressService = inject(MetadataProgressService);
  private metadataTaskService = inject(MetadataTaskService);
  private taskService = inject(TaskService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  private lastUpdateMap = new Map<string, number>();
  private timeoutHandles = new Map<string, number>();
  private readonly TASK_STALL_TIMEOUT_MS = 60 * 1000; // 1 minute

  ngOnInit(): void {
    this.metadataProgressService.activeTasks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(tasks => {
        this.activeTasks = tasks;
        this.checkForStalledTasks(tasks);
      });
  }

  private checkForStalledTasks(tasks: Record<string, MetadataBatchProgressNotification>): void {
    const now = Date.now();

    for (const taskId of this.timeoutHandles.keys()) {
      if (!tasks[taskId]) {
        clearTimeout(this.timeoutHandles.get(taskId));
        this.timeoutHandles.delete(taskId);
        this.lastUpdateMap.delete(taskId);
      }
    }

    for (const [taskId, task] of Object.entries(tasks)) {
      this.lastUpdateMap.set(taskId, now);

      if (this.timeoutHandles.has(taskId)) {
        clearTimeout(this.timeoutHandles.get(taskId));
      }

      this.timeoutHandles.set(
        taskId,
        setTimeout(() => {
          this.markTaskStalled(taskId);
        }, this.TASK_STALL_TIMEOUT_MS) as any
      );
    }
  }

  private markTaskStalled(taskId: string): void {
    const task = this.activeTasks[taskId];
    if (!task) return;
    if (task.status !== MetadataBatchStatus.COMPLETED && task.status !== 'ERROR') {
      this.activeTasks[taskId] = {
        ...task,
        status: MetadataBatchStatus.ERROR,
        message: this.t.translate('shared.metadataProgress.taskStalled')
      };
      this.activeTasks = {...this.activeTasks};
    }
  }

  getProgressPercent(task: MetadataBatchProgressNotification): number {
    if (task.total <= 0) return 0;
    if (task.status === 'COMPLETED') return 100;
    return Math.round((task.completed / task.total) * 100);
  }

  clearTask(taskId: string): void {
    this.metadataTaskService.deleteTask(taskId).subscribe(() => {
      this.metadataProgressService.clearTask(taskId);
      clearTimeout(this.timeoutHandles.get(taskId));
      this.timeoutHandles.delete(taskId);
      this.lastUpdateMap.delete(taskId);
    });
  }

  reviewTask(taskId: string): void {
    this.dialogLauncherService.openMetadataReviewDialog(taskId);
  }

  cancelTask(taskId: string): void {
    this.taskService.cancelTask(taskId).subscribe({
      next: () => {
        const task = this.activeTasks[taskId];
        if (task) {
          this.activeTasks[taskId] = {
            ...task,
            status: MetadataBatchStatus.CANCELLED,
            message: this.t.translate('shared.metadataProgress.taskCancelled')
          };
          this.activeTasks = {...this.activeTasks};
        }

        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('shared.metadataProgress.cancellationScheduledSummary'),
          detail: this.t.translate('shared.metadataProgress.cancellationScheduledDetail')
        });
      },
      error: (error) => {
        console.error('Failed to cancel task:', error);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('shared.metadataProgress.cancelFailedSummary'),
          detail: this.t.translate('shared.metadataProgress.cancelFailedDetail')
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    for (const timeout of this.timeoutHandles.values()) {
      clearTimeout(timeout);
    }
    this.timeoutHandles.clear();
    this.lastUpdateMap.clear();
  }

  getTagSeverity(status: 'IN_PROGRESS' | 'COMPLETED' | 'ERROR' | 'CANCELLED'): 'info' | 'success' | 'danger' | 'warn' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'ERROR':
        return 'danger';
      case 'CANCELLED':
        return 'warn';
      case 'IN_PROGRESS':
      default:
        return 'info';
    }
  }

  private readonly statusLabelKeys: Record<MetadataBatchStatus, string> = {
    [MetadataBatchStatus.IN_PROGRESS]: 'shared.metadataProgress.statusInProgress',
    [MetadataBatchStatus.COMPLETED]: 'shared.metadataProgress.statusCompleted',
    [MetadataBatchStatus.ERROR]: 'shared.metadataProgress.statusError',
    [MetadataBatchStatus.CANCELLED]: 'shared.metadataProgress.statusCancelled',
  };

  getStatusLabel(status: MetadataBatchStatus): string {
    const key = this.statusLabelKeys[status];
    return key ? this.t.translate(key) : status;
  }

  protected readonly Object = Object;
}
