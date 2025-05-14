import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';

export interface OidcProviderDetails {
  providerName: string;
  clientId: string;
  issuerUri: string;
  jwksUrl: string;
  claimMapping: {
    username: string;
    email: string;
    name: string;
  };
}

export interface OidcAutoProvisionDetails {
  enableAutoProvisioning: boolean;
  defaultPermissions: string[];
  defaultLibraryIds: number[];
}

export interface AppSettings {
  autoBookSearch: boolean;
  similarBookRecommendation: boolean;
  metadataRefreshOptions: MetadataRefreshOptions;
  coverResolution: string;
  uploadPattern: string;
  opdsServerEnabled: boolean;
  oidcEnabled: boolean;
  oidcProviderDetails: OidcProviderDetails;
  oidcAutoProvisionDetails: OidcAutoProvisionDetails;
}
