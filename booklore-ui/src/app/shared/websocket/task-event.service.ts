import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { TaskMessage } from './model/log-notification.model';

@Injectable({
  providedIn: 'root'
})
export class TaskEventService {
  private tasksSubject = new BehaviorSubject<Map<string, TaskMessage>>(new Map());

  tasks$: Observable<TaskMessage[]> = this.tasksSubject.asObservable().pipe(
    map(taskMap => Array.from(taskMap.values()))
  );

  handleTaskMessage(taskMessage: TaskMessage): void {
    const currentTasks = new Map(this.tasksSubject.value);
    currentTasks.set(taskMessage.taskId, taskMessage);
    this.tasksSubject.next(currentTasks);
  }

  removeTask(taskId: string): void {
    const currentTasks = new Map(this.tasksSubject.value);
    currentTasks.delete(taskId);
    this.tasksSubject.next(currentTasks);
  }

  getTask(taskId: string): TaskMessage | undefined {
    return this.tasksSubject.value.get(taskId);
  }

  getTaskById$(taskId: string): Observable<TaskMessage | undefined> {
    return this.tasksSubject.asObservable().pipe(
      map(taskMap => taskMap.get(taskId))
    );
  }

  clearAllTasks(): void {
    this.tasksSubject.next(new Map());
  }
}

