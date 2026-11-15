export interface PagedResponse<T> {
  readonly content: readonly T[];
  readonly page: {
    readonly number: number;
    readonly size: number;
    readonly totalElements: number;
    readonly totalPages: number;
  };
}
