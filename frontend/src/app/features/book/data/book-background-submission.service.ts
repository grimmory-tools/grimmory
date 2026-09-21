import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {bookBackgroundSubmissionKeys} from './book-background-submission-keys';
import {ChangeCoversVariables, QuickSendBookVariables} from './book-background-submission.models';

@Injectable({providedIn: 'root'})
export class BookBackgroundSubmissionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly emailUrl = `${API_CONFIG.BASE_URL}/api/v1/email`;

  changeCovers() {
    return mutationOptions({
      mutationKey: bookBackgroundSubmissionKeys.changeCovers(),
      mutationFn: (variables: ChangeCoversVariables) => this.requestCoverChanges(variables),
    });
  }

  quickSend() {
    return mutationOptions({
      mutationKey: bookBackgroundSubmissionKeys.quickSend(),
      mutationFn: ({bookId}: QuickSendBookVariables) =>
        lastValueFrom(this.http.post<void>(`${this.emailUrl}/book/${bookId}`, {})),
    });
  }

  private requestCoverChanges(
    variables: ChangeCoversVariables,
  ): Promise<void> {
    switch (variables.kind) {
      case 'regenerate':
        return this.postBookIds('/bulk-regenerate-covers', variables.bookIds);
      case 'generate':
        return this.postBookIds('/bulk-generate-custom-covers', variables.bookIds);
    }
  }

  private postBookIds(path: string, bookIds: readonly number[]): Promise<void> {
    return lastValueFrom(this.http.post<void>(`${this.baseUrl}${path}`, {bookIds}));
  }
}
