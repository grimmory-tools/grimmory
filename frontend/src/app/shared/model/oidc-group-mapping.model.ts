export interface OidcGroupMapping {
  id?: number;
  oidcGroupClaim: string;
  isAdmin: boolean;
  permissions: string[];
  libraryIds: number[];
  description: string;
}
