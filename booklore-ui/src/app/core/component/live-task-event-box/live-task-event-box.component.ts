import {Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TaskMessage, TaskStatus} from '../../../shared/websocket/model/log-notification.model';
import {TaskEventService} from '../../../shared/websocket/task-event.service';
import {TaskService} from '../../../shared/services/task.service';
import {Observable, take} from 'rxjs';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-live-task-event-box',
  standalone: true,
  imports: [CommonModule, Button],
  templateUrl: './live-task-event-box.component.html',
  styleUrls: ['./live-task-event-box.component.scss'],
  host: {
    class: 'config-panel'
  }
})
export class LiveTaskEventBoxComponent {
  tasks$: Observable<TaskMessage[]>;
  TaskStatus = TaskStatus;

  private taskEventService = inject(TaskEventService);
  private taskService = inject(TaskService);
  private messageService = inject(MessageService);

  constructor() {
    this.tasks$ = this.taskEventService.tasks$;
  }

  cancelTask(taskId: string): void {
    this.taskService.cancelTask(taskId).pipe(take(1)).subscribe({
      next: (response) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: `Task cancellation scheduled`
        });
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Cancellation failed',
          detail: error.error?.error || 'Failed to cancel task'
        });
      }
    });
  }

  removeTask(taskId: string): void {
    this.taskEventService.removeTask(taskId);
  }

  getStatusClasses(status: TaskStatus): string {
    switch (status) {
      case TaskStatus.IN_PROGRESS:
        return 'bg-blue-700 text-zinc-100';
      case TaskStatus.COMPLETED:
        return 'bg-green-700 text-zinc-100';
      case TaskStatus.FAILED:
        return 'bg-red-700 text-zinc-100';
      case TaskStatus.CANCELLED:
        return 'bg-gray-700 text-zinc-100';
      default:
        return 'bg-zinc-700 text-zinc-100';
    }
  }

  getStatusText(status: TaskStatus): string {
    switch (status) {
      case TaskStatus.IN_PROGRESS:
        return 'Running';
      case TaskStatus.COMPLETED:
        return 'Completed';
      case TaskStatus.FAILED:
        return 'Failed';
      case TaskStatus.CANCELLED:
        return 'Cancelled';
      default:
        return 'Unknown';
    }
  }
}
