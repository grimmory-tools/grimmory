import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {postFormData, postSseJson} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {invalidateAllBookCaches} from '../../book/service/legacy-book-cache';
import {authorCommandKeys, authorCommandScopes} from './author-command-keys';
import {
  requireAuthorIds,
  type AuthorIdsVariables,
  type MatchAuthorVariables,
  type UpdateAuthorVariables,
  type UploadAuthorPhotoFromUrlVariables,
  type UploadAuthorPhotoVariables,
} from './author-command.models';
import {invalidateAuthors} from './author-query-cache';
import {authorQueryKeys} from './author-query-keys';
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
      onSettled: async (_data, error, {authorIds}, _onMutateResult, {client}) => {
        if (error === null) {
          for (const authorId of authorIds) {
            client.removeQueries({queryKey: authorQueryKeys.detail(authorId)});
          }
        }
        await invalidateAllBookCaches(client);
      },
    });
  }

  quickMatch() {
    return mutationOptions({
      mutationKey: authorCommandKeys.quickMatch(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorId}: {readonly authorId: number}) =>
        lastValueFrom(this.http.post<AuthorDetail>(`${this.baseUrl}/${authorId}/quick-match`, null)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthors(client),
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
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthors(client),
    });
  }

  update() {
    return mutationOptions({
      mutationKey: authorCommandKeys.update(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorId, request}: UpdateAuthorVariables) =>
        lastValueFrom(this.http.put<AuthorDetail>(`${this.baseUrl}/${authorId}`, request)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAllBookCaches(client),
    });
  }

  match() {
    return mutationOptions({
      mutationKey: authorCommandKeys.match(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorId, request}: MatchAuthorVariables) =>
        lastValueFrom(this.http.post<AuthorDetail>(`${this.baseUrl}/${authorId}/match`, request)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthors(client),
    });
  }

  uploadPhoto() {
    return mutationOptions({
      mutationKey: authorCommandKeys.uploadPhoto(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorId, file}: UploadAuthorPhotoVariables) =>
        postFormData(this.http, `${this.baseUrl}/${authorId}/photo/upload`, {file}),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthors(client),
    });
  }

  uploadPhotoFromUrl() {
    return mutationOptions({
      mutationKey: authorCommandKeys.uploadPhotoFromUrl(),
      scope: authorCommandScopes.records,
      mutationFn: ({authorId, url}: UploadAuthorPhotoFromUrlVariables) =>
        lastValueFrom(this.http.post<void>(`${this.baseUrl}/${authorId}/photo/url`, null, {params: {url}})),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateAuthors(client),
    });
  }
}
