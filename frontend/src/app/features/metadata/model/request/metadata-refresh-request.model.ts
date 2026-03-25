import {MetadataRefreshType} from './metadata-refresh-type.enum';
import {MetadataRefreshOptions} from './metadata-refresh-options.model';

export interface MetadataRefreshRequest {
  refreshType: MetadataRefreshType;
  libraryId?: number;
  bookIds?: number[];
  refreshOptions?: MetadataRefreshOptions;
}
