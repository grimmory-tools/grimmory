import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {MetadataRefreshRequest} from '../../metadata/model/request/metadata-refresh-request.model';

export enum TaskType {
  CLEAR_PDF_CACHE = 'CLEAR_PDF_CACHE',
  REFRESH_LIBRARY_METADATA = 'REFRESH_LIBRARY_METADATA',
  UPDATE_BOOK_RECOMMENDATIONS = 'UPDATE_BOOK_RECOMMENDATIONS',
  CLEANUP_DELETED_BOOKS = 'CLEANUP_DELETED_BOOKS',
  SYNC_LIBRARY_FILES = 'SYNC_LIBRARY_FILES',
  BOOKDROP_PERIODIC_SCANNING = 'BOOKDROP_PERIODIC_SCANNING',
  CLEANUP_TEMP_METADATA = 'CLEANUP_TEMP_METADATA',
  REFRESH_METADATA_MANUAL = 'REFRESH_METADATA_MANUAL'
}

export const TASK_TYPE_CONFIG: Record<TaskType, { parallel: boolean; async: boolean; displayOrder: number }> = {
  [TaskType.REFRESH_LIBRARY_METADATA]: {parallel: false, async: true, displayOrder: 1},
  [TaskType.SYNC_LIBRARY_FILES]: {parallel: false, async: false, displayOrder: 2},
  [TaskType.BOOKDROP_PERIODIC_SCANNING]: {parallel: false, async: false, displayOrder: 3},
  [TaskType.UPDATE_BOOK_RECOMMENDATIONS]: {parallel: false, async: true, displayOrder: 4},
  [TaskType.CLEANUP_DELETED_BOOKS]: {parallel: false, async: false, displayOrder: 5},
  [TaskType.CLEANUP_TEMP_METADATA]: {parallel: false, async: false, displayOrder: 6},
  [TaskType.REFRESH_METADATA_MANUAL]: {parallel: false, async: false, displayOrder: 7},
  [TaskType.CLEAR_PDF_CACHE]: {parallel: false, async: false, displayOrder: 8},
};

export enum MetadataReplaceMode {
  REPLACE_ALL = 'REPLACE_ALL',
  REPLACE_MISSING = 'REPLACE_MISSING'
}

export interface LibraryRescanOptions {
  metadataReplaceMode?: MetadataReplaceMode;
}

export interface TaskCreateRequest {
  taskType: TaskType;
  triggeredByCron?: boolean;
  options?: LibraryRescanOptions | MetadataRefreshRequest | null;
}

export interface TaskCreateResponse {
  id?: string;
  type: string;
  status: TaskStatus;
  message?: string;
  createdAt?: string;
}

export interface TaskStatusResponse {
  taskHistories: TaskHistory[];
}

export enum TaskStatus {
  ACCEPTED = 'ACCEPTED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
  PENDING = 'PENDING'
}

export interface CronConfig {
  id: number | null;
  taskType: string;
  cronExpression: string | null;
  enabled: boolean;
  options: Record<string, unknown> | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TaskInfo {
  taskType: string;
  name: string;
  description: string;
  parallel: boolean;
  async: boolean;
  cronSupported: boolean;
  cronConfig: CronConfig | null;
  metadata?: string | null;
}

export interface TaskHistory {
  id: string | null;
  type: string;
  status: TaskStatus | null;
  progressPercentage: number | null;
  message: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  completedAt: string | null;
}

export interface TaskCancelResponse {
  taskId: string;
  cancelled: boolean;
  message: string;
}

export interface TaskCronConfigRequest {
  cronExpression?: string | null;
  enabled?: boolean | null;
}

export interface TaskProgressPayload {
  taskId: string;
  taskType: string;
  message: string;
  progress: number; // 0-100 percentage
  taskStatus: TaskStatus;
}

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/tasks`;

  private taskProgressSubject = new BehaviorSubject<TaskProgressPayload | null>(null);
  public taskProgress$ = this.taskProgressSubject.asObservable();

  getAvailableTasks(): Observable<TaskInfo[]> {
    return this.http.get<TaskInfo[]>(`${this.baseUrl}`);
  }

  startTask(request: TaskCreateRequest): Observable<TaskCreateResponse> {
    return this.http.post<TaskCreateResponse>(`${this.baseUrl}/start`, request);
  }

  getLatestTasksForEachType(): Observable<TaskStatusResponse> {
    return this.http.get<TaskStatusResponse>(`${this.baseUrl}/last`);
  }

  cancelTask(taskId: string): Observable<TaskCancelResponse> {
    return this.http.delete<TaskCancelResponse>(`${this.baseUrl}/${taskId}/cancel`);
  }

  updateCronConfig(taskType: string, request: TaskCronConfigRequest): Observable<CronConfig> {
    return this.http.patch<CronConfig>(`${this.baseUrl}/${taskType}/cron`, request);
  }

  handleTaskProgress(progress: TaskProgressPayload): void {
    this.taskProgressSubject.next(progress);
  }
}
