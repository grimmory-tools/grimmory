import type {BookMetadata} from '../../book/model/book.model';
import type {MetadataProviderDescriptor, MetadataProviderId} from '../../../shared/metadata/metadata-providers';
import type {MetadataBatchStatus} from '../../../shared/model/metadata-batch-progress.model';

export interface MetadataSourceProvider extends MetadataProviderDescriptor {
  readonly enabled: boolean;
}

export interface MetadataSearchParams {
  readonly bookId: number;
  readonly providers: readonly MetadataProviderId[];
  readonly title?: string;
  readonly author?: string;
  readonly isbn?: string;
}

export type MetadataSearchResult = BookMetadata & {readonly provider: MetadataProviderId};

export interface CoverImage {
  readonly url: string;
  readonly width: number;
  readonly height: number;
  readonly index: number;
}

export interface CoverSearchParams {
  readonly bookId: number;
  readonly title?: string;
  readonly author?: string;
  readonly coverType: 'ebook' | 'audiobook';
}

export interface MetadataReviewProposal {
  readonly proposalId: number;
  readonly taskId: string;
  readonly bookId: number;
  readonly fetchedAt: string;
  readonly reviewedAt: string | null;
  readonly reviewerUserId: string | null;
  readonly status: 'FETCHED' | 'ACCEPTED' | 'REJECTED';
  readonly metadataJson: BookMetadata;
}

export interface MetadataReviewTask {
  readonly id: string;
  readonly status: MetadataBatchStatus;
  readonly completed: number;
  readonly totalBooks: number;
  readonly startedAt: string;
  readonly completedAt: string | null;
  readonly initiatedBy: number | null;
  readonly proposals: readonly MetadataReviewProposal[];
}
