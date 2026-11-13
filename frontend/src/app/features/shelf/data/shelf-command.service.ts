import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {requireBookIds} from '../../book/data/book-command.models';
import {
  applyBookQueryChangeSet,
  invalidateAllBookQueries,
  invalidateBookCollections,
} from '../../book/data/book-query-cache';
import type {BookDetail} from '../../book/data/book-response.models';
import {shelfCommandKeys, shelfCommandScopes} from './shelf-command-keys';
import type {
  CreateMagicShelfVariables,
  CreateShelfVariables,
  UpdateMagicShelfVariables,
  UpdateShelfMembershipVariables,
  UpdateShelfVariables,
} from './shelf-command.models';
import {shelfQueryKeys} from './shelf-query-keys';
import type {MagicShelf, Shelf} from './shelf-query.models';

@Service()
export class ShelfCommandService {
  private readonly http = inject(HttpClient);
  private readonly shelvesUrl = `${API_CONFIG.BASE_URL}/api/v1/shelves`;
  private readonly magicShelvesUrl = `${API_CONFIG.BASE_URL}/api/magic-shelves`;
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  createShelf() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.createShelf(),
      mutationFn: (shelf: CreateShelfVariables) => lastValueFrom(this.http.post<Shelf>(this.shelvesUrl, shelf)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
    });
  }

  updateShelf() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.updateShelf(),
      mutationFn: ({shelfId, shelf}: UpdateShelfVariables) =>
        lastValueFrom(this.http.put<Shelf>(`${this.shelvesUrl}/${shelfId}`, shelf)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => Promise.all([
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
        invalidateAllBookQueries(client),
      ]),
    });
  }

  deleteShelf() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.deleteShelf(),
      mutationFn: ({shelfId}: {readonly shelfId: number}) =>
        lastValueFrom(this.http.delete<void>(`${this.shelvesUrl}/${shelfId}`)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => Promise.all([
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
        invalidateAllBookQueries(client),
      ]),
    });
  }

  createMagicShelf() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.createMagicShelf(),
      mutationFn: (magicShelf: CreateMagicShelfVariables) =>
        lastValueFrom(this.http.post<MagicShelf>(this.magicShelvesUrl, magicShelf)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
    });
  }

  updateMagicShelf() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.updateMagicShelf(),
      mutationFn: ({magicShelfId, magicShelf}: UpdateMagicShelfVariables) =>
        lastValueFrom(this.http.post<MagicShelf>(this.magicShelvesUrl, {id: magicShelfId, ...magicShelf})),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => Promise.all([
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
        invalidateBookCollections(client),
      ]),
    });
  }

  deleteMagicShelf() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.deleteMagicShelf(),
      mutationFn: ({magicShelfId}: {readonly magicShelfId: number}) =>
        lastValueFrom(this.http.delete<void>(`${this.magicShelvesUrl}/${magicShelfId}`)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => Promise.all([
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
        invalidateBookCollections(client),
      ]),
    });
  }

  updateMembership() {
    return mutationOptions({
      mutationKey: shelfCommandKeys.updateMembership(),
      scope: shelfCommandScopes.membership,
      mutationFn: ({bookIds, assignShelfIds, unassignShelfIds}: UpdateShelfMembershipVariables) =>
        lastValueFrom(this.http.post<readonly BookDetail[]>(`${this.booksUrl}/shelves`, {
          bookIds: requireBookIds(bookIds),
          shelvesToAssign: assignShelfIds,
          shelvesToUnassign: unassignShelfIds,
        })),
      onSettled: (_data, _error, {bookIds}, _onMutateResult, {client}) => Promise.all([
        applyBookQueryChangeSet(client, {changedBookIds: bookIds}),
        client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
      ]),
    });
  }
}
