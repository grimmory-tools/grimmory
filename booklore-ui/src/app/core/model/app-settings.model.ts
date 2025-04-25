import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';

export interface AppSettings {
  autoBookSearch: boolean;
  similarBookRecommendation: boolean;
  metadataRefreshOptions: MetadataRefreshOptions;
  coverResolution: string;
  uploadPattern: string;
}
