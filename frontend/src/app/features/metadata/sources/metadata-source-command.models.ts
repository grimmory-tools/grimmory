import type {MetadataRefreshOptions} from '../model/request/metadata-refresh-options.model';

export type RefreshMetadataVariables = {readonly refreshOptions?: MetadataRefreshOptions} & (
  | {readonly refreshType: 'BOOKS'; readonly bookIds: readonly number[]}
  | {readonly refreshType: 'LIBRARY'; readonly libraryId: number}
);

export interface SetMetadataProposalStatusVariables {
  readonly taskId: string;
  readonly proposalId: number;
  readonly status: 'ACCEPTED' | 'REJECTED';
}
