export interface APIException {
  status: number;
  message: string;
  timestamp?: string;
  error?: string;
}
