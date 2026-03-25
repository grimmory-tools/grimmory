export interface EmailProvider {
  isEditing: boolean;
  id: number;
  userId: number;
  name: string;
  host: string;
  port: number;
  username: string;
  password: string;
  fromAddress: string;
  auth: boolean;
  startTls: boolean;
  defaultProvider: boolean;
  shared: boolean;
}
