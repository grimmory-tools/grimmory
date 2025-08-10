import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private http = inject(HttpClient);

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/tasks`;

  cancelTask(taskId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.url}/${taskId}`);
  }
}
