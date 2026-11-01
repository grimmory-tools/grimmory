import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {postSseJson} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {reconcileAllBookCaches} from '../../book/service/legacy-book-cache';
import {invalidateAuthors} from './author-query-cache';
import {authorQueryKeys} from './author-query-keys';
import {type AuthorDetail, type AuthorSummary} from './author-response.models';

const authorCommandKeys = {
  all: () => ['authors', 'command'] as const,
  delete: () => [...authorCommandKeys.all(), 'delete'] as const,
  quickMatch: () => [...authorCommandKeys.all(), 'quick-match'] as const,
  autoMatch: () => [...authorCommandKeys.all(), 'auto-match'] as const,
  update: () => [...authorCommandKeys.all(), 'update'] as const,
  match: () => [...authorCommandKeys.all(), 'match'] as const,
  uploadPhoto: () => [...authorCommandKeys.all(), 'upload-photo'] as const,
};

interface AuthorIdsVariables {
  readonly authorIds: readonly number[];
}

interface QuickMatchAuthorVariables {
  readonly authorId: number;
}

interface AuthorUpdateRequest {
  name?: string;
  description?: string;
  asin?: string;
  nameLocked?: boolean;
  descriptionLocked?: boolean;
  asinLocked?: boolean;
  photoLocked?: boolean;
}

export interface UpdateAuthorVariables {
  readonly authorId: number;
  readonly request: AuthorUpdateRequest;
}

interface AuthorMatchRequest {
  source: string;
  asin: string;
  region: string;
}

export interface MatchAuthorVariables {
  readonly authorId: number;
  readonly request: AuthorMatchRequest;
}

export interface UploadAuthorPhotoVariables {
  readonly authorId: number;
  readonly url: string;
}

@Service()
export class AuthorCommandService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/authors`;

  photoUploadUrl(authorId: number): string {
    return `${this.baseUrl}/${authorId}/photo/upload`;
  }

  deleteAuthors() {
    return mutationOptions({
      mutationKey: authorCommandKeys.delete(),
      mutationFn: ({authorIds}: AuthorIdsVariables) =>
        lastValueFrom(this.http.delete<void>(this.baseUrl, {body: authorIds})),
      onSettled: async (_data, error, {authorIds}, _onMutateResult, {client}) => {
        if (error === null) {
          for (const authorId of authorIds) {
            client.removeQueries({queryKey: authorQueryKeys.detail(authorId)});
          }
        }
        await reconcileAllBookCaches(client);
      },
    });
  }

  quickMatch() {
    return mutationOptions({
      mutationKey: authorCommandKeys.quickMatch(),
      mutationFn: ({authorId}: QuickMatchAuthorVariables) =>
        lastValueFrom(this.http.post<AuthorDetail>(`${this.baseUrl}/${authorId}/quick-match`, null)),
      onSettled: (_data, _error, {authorId}, _onMutateResult, {client}) => invalidateAuthors(client, [authorId]),
    });
  }

  autoMatch() {
    return mutationOptions({
      mutationKey: authorCommandKeys.autoMatch(),
      mutationFn: async ({authorIds}: AuthorIdsVariables) => {
        const stream = await postSseJson<AuthorSummary>(
          this.auth,
          `${this.baseUrl}/auto-match`,
          JSON.stringify(authorIds),
        );
        const matched: AuthorSummary[] = [];
        for await (const author of stream) {
          matched.push(author);
        }
        return matched;
      },
      onSettled: (_data, _error, {authorIds}, _onMutateResult, {client}) => invalidateAuthors(client, authorIds),
    });
  }

  update() {
    return mutationOptions({
      mutationKey: authorCommandKeys.update(),
      mutationFn: ({authorId, request}: UpdateAuthorVariables) =>
        lastValueFrom(this.http.put<AuthorDetail>(`${this.baseUrl}/${authorId}`, request)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => reconcileAllBookCaches(client),
    });
  }

  match() {
    return mutationOptions({
      mutationKey: authorCommandKeys.match(),
      mutationFn: ({authorId, request}: MatchAuthorVariables) =>
        lastValueFrom(this.http.post<AuthorDetail>(`${this.baseUrl}/${authorId}/match`, request)),
      onSettled: (_data, _error, {authorId}, _onMutateResult, {client}) => invalidateAuthors(client, [authorId]),
    });
  }

  uploadPhotoFromUrl() {
    return mutationOptions({
      mutationKey: authorCommandKeys.uploadPhoto(),
      mutationFn: ({authorId, url}: UploadAuthorPhotoVariables) =>
        lastValueFrom(this.http.post<void>(`${this.baseUrl}/${authorId}/photo/url`, null, {params: {url}})),
      onSettled: (_data, _error, {authorId}, _onMutateResult, {client}) => invalidateAuthors(client, [authorId]),
    });
  }
}
