import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {postSseJson} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {invalidateAllBookCaches} from '../../book/service/legacy-book-cache';
import {authorCommandKeys, authorCommandScopes} from './author-command-keys';
import {requireAuthorIds, type AuthorIdsVariables} from './author-command.models';
import {invalidateAuthorCollections} from './author-query-cache';
import {type AuthorDetail, type AuthorSummary} from './author-query.models';

@Service()
export class AuthorCommandService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/authors`;

  deleteAuthors() {
    return mutationOptions({
      mutationKey: authorCommandKeys.deleteAuthors(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorIds}: AuthorIdsVariables) =>
        lastValueFrom(this.http.delete<void>(this.baseUrl, {body: requireAuthorIds(authorIds)})),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAllBookCaches(client),
    });
  }

  quickMatch() {
    return mutationOptions({
      mutationKey: authorCommandKeys.quickMatch(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorId}: {readonly authorId: number}) =>
        lastValueFrom(this.http.post<AuthorDetail>(`${this.baseUrl}/${authorId}/quick-match`, null)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthorCollections(client),
    });
  }

  autoMatch() {
    return mutationOptions({
      mutationKey: authorCommandKeys.autoMatch(),
      scope: authorCommandScopes.records,
      mutationFn: async ({authorIds}: AuthorIdsVariables) => {
        const stream = await postSseJson<AuthorSummary>(
          this.auth,
          `${this.baseUrl}/auto-match`,
          JSON.stringify(requireAuthorIds(authorIds)),
        );
        const matched: AuthorSummary[] = [];
        for await (const author of stream) {
          matched.push(author);
        }
        return matched;
      },
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthorCollections(client),
    });
  }
}
