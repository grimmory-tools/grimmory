import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around confirmation-dialog callbacks,
// app-settings effects, and the review service stream orchestration so spoiler, lock, delete,
// and refresh flows can be asserted without the full review panel runtime.
describe.skip('BookReviewsComponent', () => {
  it('needs service seams to verify review loading, refresh success and failure, and review-lock toggling', () => {
    // TODO(seam): Cover loadReviews, fetchNewReviews, and toggleReviewsLock once service streams and confirmation callbacks are isolated behind deterministic doubles.
  });

  it('needs state seams to verify spoiler reveal behavior, delete flows, and sort-order changes', () => {
    // TODO(seam): Cover reveal/hide spoiler state, deleteReview, deleteAllReviews, and sortReviewsByDate after the component is detached from the live overlay and translation runtime.
  });
});
