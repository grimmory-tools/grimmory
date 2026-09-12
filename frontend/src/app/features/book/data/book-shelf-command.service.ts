import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {reconcilingMutationOptions} from '../../../core/data/command-options';
import {bookCommandChangeSet, requireBookIds} from './book-command.models';
import {bookShelfCommandKeys, bookShelfCommandScopes} from './book-shelf-command-keys';
import {
  UpdateBookShelfMembershipResult,
  UpdateBookShelfMembershipVariables,
} from './book-shelf-command.models';
import {applyBookQueryChangeSet} from './book-query-cache';
import {invalidateShelfDefinitions} from './shelf-definition-query-cache';

@Injectable({providedIn: 'root'})
export class BookShelfCommandService {
  private readonly http = inject(HttpClient);
  private readonly membershipUrl = `${API_CONFIG.BASE_URL}/api/v1/books/shelves`;

  updateMembership() {
    return reconcilingMutationOptions({
      mutationKey: bookShelfCommandKeys.updateMembership(),
      scope: bookShelfCommandScopes.regularShelves,
      mutationFn: (variables: UpdateBookShelfMembershipVariables) => this.postMembership(
        requireBookIds(variables.bookIds),
        variables.assignShelfIds,
        variables.unassignShelfIds,
      ),
      reconcile: async (outcome, variables, client) => {
        await Promise.all([
          applyBookQueryChangeSet(
            client,
            bookCommandChangeSet(outcome, variables.bookIds, ({confirmedBookIds}) => ({
              changedBookIds: confirmedBookIds,
            })),
          ),
          invalidateShelfDefinitions(client),
        ]);
      },
    });
  }

  private async postMembership(
    bookIds: readonly number[],
    assignShelfIds: readonly number[],
    unassignShelfIds: readonly number[],
  ): Promise<UpdateBookShelfMembershipResult> {
    const response = await lastValueFrom(this.http.post<readonly MembershipBookResponse[]>(
      this.membershipUrl,
      {
        bookIds,
        shelvesToAssign: assignShelfIds,
        shelvesToUnassign: unassignShelfIds,
      },
    ));
    return {
      confirmedBookIds: response.map(book => book.id),
    };
  }
}

interface MembershipBookResponse {
  readonly id: number;
}
