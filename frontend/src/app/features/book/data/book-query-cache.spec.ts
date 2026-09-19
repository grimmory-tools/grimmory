import {QueryClient} from '@tanstack/angular-query-experimental';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {observeActiveQuery} from '../../../core/testing/query-testing';
import {
  invalidateAllBookQueries,
  invalidateBookCollections,
  applyBookQueryChangeSet,
} from './book-query-cache';
import {bookQueryKeys} from './book-query-keys';
import {normalizeBookPageParams} from './book-query-params';

const firstBook = {id: 1};
const secondBook = {id: 2};
const pageKey = bookQueryKeys.boundedPage(normalizeBookPageParams({
  facets: {},
  facetLogic: 'or',
  sort: [],
  size: 20,
}));
const firstDetailKey = bookQueryKeys.detail(1, false);
const secondDetailKey = bookQueryKeys.detail(2, false);
const firstRecommendationKey = bookQueryKeys.recommendation(1, 20);
const secondRecommendationKey = bookQueryKeys.recommendation(2, 20);

function seedAllQueryFamilies(queryClient: QueryClient): void {
  queryClient.setQueryData(pageKey, {content: [firstBook, secondBook]});
  queryClient.setQueryData(firstDetailKey, firstBook);
  queryClient.setQueryData(secondDetailKey, secondBook);
  queryClient.setQueryData(firstRecommendationKey, [secondBook]);
  queryClient.setQueryData(secondRecommendationKey, [firstBook]);
}

describe('book query cache', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it('invalidates every new book query from the root', async () => {
    seedAllQueryFamilies(queryClient);

    await invalidateAllBookQueries(queryClient);

    for (const query of queryClient.getQueryCache().findAll({queryKey: bookQueryKeys.all()})) {
      expect(query.state.isInvalidated).toBe(true);
    }
  });

  it('invalidates only collection queries through the collection action', async () => {
    seedAllQueryFamilies(queryClient);

    await invalidateBookCollections(queryClient);

    expect(queryClient.getQueryState(pageKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(firstDetailKey)?.isInvalidated).toBe(false);
    expect(queryClient.getQueryState(firstRecommendationKey)?.isInvalidated).toBe(false);
  });

  it('invalidates changed details and every dependent query family once', async () => {
    seedAllQueryFamilies(queryClient);
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await applyBookQueryChangeSet(queryClient, {changedBookIds: [1, 1]});

    expect(queryClient.getQueryState(pageKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(firstDetailKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(secondDetailKey)?.isInvalidated).toBe(false);
    expect(queryClient.getQueryState(firstRecommendationKey)?.isInvalidated).toBe(true);
    expect(invalidateQueriesSpy).toHaveBeenCalledTimes(3);
  });

  it('resolves without work when no changed or deleted book IDs are provided', async () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const removeQueriesSpy = vi.spyOn(queryClient, 'removeQueries');

    const reconciliation = applyBookQueryChangeSet(queryClient, {
      changedBookIds: [],
      deletedBookIds: [],
    });

    expect(reconciliation).toBeInstanceOf(Promise);
    await reconciliation;

    expect(invalidateQueriesSpy).not.toHaveBeenCalled();
    expect(removeQueriesSpy).not.toHaveBeenCalled();
  });

  it('removes deleted leaves and invalidates surviving dependents once', async () => {
    seedAllQueryFamilies(queryClient);
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const removeQueriesSpy = vi.spyOn(queryClient, 'removeQueries');

    await applyBookQueryChangeSet(queryClient, {deletedBookIds: [1, 1]});

    expect(queryClient.getQueryData(firstDetailKey)).toBeUndefined();
    expect(queryClient.getQueryData(firstRecommendationKey)).toBeUndefined();
    expect(queryClient.getQueryData(secondDetailKey)).toEqual(secondBook);
    expect(queryClient.getQueryData(secondRecommendationKey)).toEqual([firstBook]);
    expect(queryClient.getQueryState(pageKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(secondRecommendationKey)?.isInvalidated).toBe(true);
    expect(removeQueriesSpy).toHaveBeenCalledTimes(1);
    expect(invalidateQueriesSpy).toHaveBeenCalledTimes(2);
  });

  it('refetches each active dependent once without aborting it after a change', async () => {
    const page = observeActiveQuery(queryClient, pageKey, {content: [firstBook]});
    const detail = observeActiveQuery(queryClient, firstDetailKey, firstBook);
    const recommendations = observeActiveQuery(queryClient, firstRecommendationKey, [secondBook]);

    const reconciliation = applyBookQueryChangeSet(queryClient, {changedBookIds: [1, 1]});

    await vi.waitFor(() => {
      expect(page.fetchCount()).toBe(1);
      expect(detail.fetchCount()).toBe(1);
      expect(recommendations.fetchCount()).toBe(1);
    });
    expect(page.abortCount()).toBe(0);
    expect(detail.abortCount()).toBe(0);
    expect(recommendations.abortCount()).toBe(0);

    let settled = false;
    void reconciliation.then(() => {
      settled = true;
    });
    await Promise.resolve();
    expect(settled).toBe(false);

    page.finish();
    detail.finish();
    recommendations.finish();
    await reconciliation;
    expect(settled).toBe(true);
  });

  it('does not refetch removed active leaves and refetches surviving dependents once', async () => {
    const removedDetail = observeActiveQuery(queryClient, firstDetailKey, firstBook);
    const removedRecommendations = observeActiveQuery(
      queryClient,
      firstRecommendationKey,
      [secondBook],
    );
    const page = observeActiveQuery(queryClient, pageKey, {content: [firstBook, secondBook]});
    const survivingRecommendations = observeActiveQuery(
      queryClient,
      secondRecommendationKey,
      [firstBook],
    );

    const reconciliation = applyBookQueryChangeSet(queryClient, {deletedBookIds: [1, 1]});

    await vi.waitFor(() => {
      expect(page.fetchCount()).toBe(1);
      expect(survivingRecommendations.fetchCount()).toBe(1);
    });
    expect(removedDetail.fetchCount()).toBe(0);
    expect(removedRecommendations.fetchCount()).toBe(0);
    expect(removedDetail.abortCount()).toBe(0);
    expect(removedRecommendations.abortCount()).toBe(0);
    expect(page.abortCount()).toBe(0);
    expect(survivingRecommendations.abortCount()).toBe(0);
    expect(queryClient.getQueryData(firstDetailKey)).toBeUndefined();
    expect(queryClient.getQueryData(firstRecommendationKey)).toBeUndefined();

    removedDetail.finish();
    removedRecommendations.finish();
    page.finish();
    survivingRecommendations.finish();
    await reconciliation;
  });

  it('combines changes and deletions with deletion winning for overlapping IDs', async () => {
    const changedDetail = observeActiveQuery(queryClient, secondDetailKey, secondBook);
    const removedDetail = observeActiveQuery(queryClient, firstDetailKey, firstBook);
    const removedRecommendations = observeActiveQuery(
      queryClient,
      firstRecommendationKey,
      [secondBook],
    );
    const page = observeActiveQuery(queryClient, pageKey, {content: [firstBook, secondBook]});
    const survivingRecommendations = observeActiveQuery(
      queryClient,
      secondRecommendationKey,
      [firstBook],
    );

    const reconciliation = applyBookQueryChangeSet(queryClient, {
      changedBookIds: [1, 2, 2],
      deletedBookIds: [1, 1],
    });

    await vi.waitFor(() => {
      expect(changedDetail.fetchCount()).toBe(1);
      expect(page.fetchCount()).toBe(1);
      expect(survivingRecommendations.fetchCount()).toBe(1);
    });
    expect(removedDetail.fetchCount()).toBe(0);
    expect(removedRecommendations.fetchCount()).toBe(0);
    expect(changedDetail.abortCount()).toBe(0);
    expect(removedDetail.abortCount()).toBe(0);
    expect(removedRecommendations.abortCount()).toBe(0);
    expect(page.abortCount()).toBe(0);
    expect(survivingRecommendations.abortCount()).toBe(0);
    expect(queryClient.getQueryData(firstDetailKey)).toBeUndefined();
    expect(queryClient.getQueryData(firstRecommendationKey)).toBeUndefined();

    changedDetail.finish();
    removedDetail.finish();
    removedRecommendations.finish();
    page.finish();
    survivingRecommendations.finish();
    await reconciliation;
  });

  it('keeps root invalidation pending until active refetches settle', async () => {
    const detail = observeActiveQuery(queryClient, firstDetailKey, firstBook);

    const invalidation = invalidateAllBookQueries(queryClient);

    await vi.waitFor(() => {
      expect(detail.fetchCount()).toBe(1);
    });
    let settled = false;
    void invalidation.then(() => {
      settled = true;
    });
    await Promise.resolve();
    expect(settled).toBe(false);

    detail.finish();
    await invalidation;
    expect(settled).toBe(true);
  });

  it('keeps collection invalidation pending until active collection refetches settle', async () => {
    const page = observeActiveQuery(queryClient, pageKey, {content: [firstBook]});
    const detail = observeActiveQuery(queryClient, firstDetailKey, firstBook);

    const invalidation = invalidateBookCollections(queryClient);

    await vi.waitFor(() => {
      expect(page.fetchCount()).toBe(1);
    });
    expect(detail.fetchCount()).toBe(0);
    let settled = false;
    void invalidation.then(() => {
      settled = true;
    });
    await Promise.resolve();
    expect(settled).toBe(false);

    page.finish();
    detail.finish();
    await invalidation;
    expect(settled).toBe(true);
  });
});
