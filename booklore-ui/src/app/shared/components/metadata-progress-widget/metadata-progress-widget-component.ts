import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {KeyValuePipe} from '@angular/common';
import {ProgressBarModule} from 'primeng/progressbar';
import {ButtonModule} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {Tooltip} from 'primeng/tooltip';
import {DialogService} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';

import {MetadataBatchProgressNotification, MetadataBatchStatus, MetadataBatchStatusLabels} from '../../model/metadata-batch-progress.model';
import {MetadataProgressService} from '../../service/metadata-progress-service';
import {MetadataReviewDialogComponent} from '../../../features/metadata/component/metadata-review-dialog/metadata-review-dialog-component';
import {MetadataTaskService} from '../../../features/book/service/metadata-task';
import {TaskService} from '../../services/task.service';
import {Tag} from 'primeng/tag';

@Component({
  selector: 'app-metadata-progress-widget',
  templateUrl: './metadata-progress-widget-component.html',
  styleUrls: ['./metadata-progress-widget-component.scss'],
  standalone: true,
  imports: [KeyValuePipe, ProgressBarModule, ButtonModule, Divider, Tooltip, Tag]
})
export class MetadataProgressWidgetComponent implements OnInit, OnDestroy {
  activeTasks: { [taskId: string]: MetadataBatchProgressNotification } = {};

  private destroy$ = new Subject<void>();
  private dialogService = inject(DialogService);
  private metadataProgressService = inject(MetadataProgressService);
  private metadataTaskService = inject(MetadataTaskService);
  private taskService = inject(TaskService);
  private messageService = inject(MessageService);

  private lastUpdateMap = new Map<string, number>();
  private timeoutHandles = new Map<string, any>();
  private readonly TASK_STALL_TIMEOUT_MS = 60 * 1000; // 1 minute

  ngOnInit(): void {
    this.metadataProgressService.activeTasks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(tasks => {
        this.activeTasks = tasks;
        this.checkForStalledTasks(tasks);
      });
  }

  private checkForStalledTasks(tasks: { [taskId: string]: MetadataBatchProgressNotification }): void {
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
        }, this.TASK_STALL_TIMEOUT_MS)
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
        message: 'Task stalled or backend unavailable'
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
    this.dialogService.open(MetadataReviewDialogComponent, {
      showHeader: false,
      width: '90vw',
      height: '90vh',
      data: {taskId},
      closable: false,
      modal: true
    });
  }

  cancelTask(taskId: string): void {
    this.taskService.cancelTask(taskId).subscribe({
      next: () => {
        const task = this.activeTasks[taskId];
        if (task) {
          this.activeTasks[taskId] = {
            ...task,
            status: MetadataBatchStatus.ERROR,
            message: 'Task cancelled by user'
          };
          this.activeTasks = {...this.activeTasks};
        }

        this.messageService.add({
          severity: 'success',
          summary: 'Cancellation Scheduled',
          detail: 'Task cancellation has been successfully scheduled'
        });
      },
      error: (error) => {
        console.error('Failed to cancel task:', error);
        this.messageService.add({
          severity: 'error',
          summary: 'Cancel Failed',
          detail: 'Failed to cancel the task. Please try again.'
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

  getTagSeverity(status: 'IN_PROGRESS' | 'COMPLETED' | 'ERROR'): 'info' | 'success' | 'danger' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'ERROR':
        return 'danger';
      case 'IN_PROGRESS':
      default:
        return 'info';
    }
  }

  getStatusLabel(status: MetadataBatchStatus): string {
    return MetadataBatchStatusLabels[status] ?? status;
  }

  protected readonly Object = Object;
}
