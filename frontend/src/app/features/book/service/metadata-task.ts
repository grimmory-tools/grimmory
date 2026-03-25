import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, map} from 'rxjs';
import {BookMetadata} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MetadataBatchProgressNotification} from '../../../shared/model/metadata-batch-progress.model';

export enum FetchedMetadataProposalStatus {
  FETCHED = 'FETCHED',
  ACCEPTED = 'ACCEPTED',
  REJECTED = 'REJECTED',
}

export interface FetchedProposal {
  proposalId: number;
  taskId: string;
  bookId: number;
  fetchedAt: string;
  reviewedAt: string | null;
  reviewerUserId: string | null;
  status: FetchedMetadataProposalStatus;
  metadataJson: BookMetadata;
}

export interface MetadataFetchTask {
  id: string;
  status: string;
  completed: number;
  totalBooks: number;
  startedAt: string;
  completedAt: string | null;
  initiatedBy: string;
  errorMessage: string | null;

  proposals: FetchedProposal[];
}

@Injectable({
  providedIn: 'root'
})
export class MetadataTaskService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/metadata/tasks`;
  private http = inject(HttpClient);

  getTaskWithProposals(taskId: string): Observable<MetadataFetchTask> {
    return this.http.get<{ task: MetadataFetchTask }>(`${this.url}/${taskId}`)
      .pipe(
        map(response => response.task)
      );
  }

  deleteTask(taskId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${taskId}`);
  }

  updateProposalStatus(taskId: string, proposalId: number, status: string): Observable<void> {
    return this.http.post<void>(`${this.url}/${taskId}/proposals/${proposalId}/status`, null, {
      params: {status}
    });
  }

  getActiveTasks(): Observable<MetadataBatchProgressNotification[]> {
    return this.http.get<MetadataBatchProgressNotification[]>(`${this.url}/active`);
  }
}
