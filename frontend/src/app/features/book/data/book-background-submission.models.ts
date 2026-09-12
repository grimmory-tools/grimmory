export interface ChangeCoversVariables {
  readonly kind: 'regenerate' | 'generate';
  readonly bookIds: readonly number[];
}

export interface QuickSendBookVariables {
  readonly bookId: number;
}
