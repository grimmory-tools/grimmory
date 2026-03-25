export enum Severity {
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR'
}

export interface LogNotification {
  timestamp?: string;
  message: string;
  severity?: Severity;
}

export function parseLogNotification(messageBody: string): LogNotification {
  const raw = JSON.parse(messageBody);
  return {
    timestamp: raw.timestamp ? new Date(raw.timestamp).toLocaleTimeString() : undefined,
    message: raw.message,
    severity: raw.severity ? Severity[raw.severity as keyof typeof Severity] : undefined
  };
}
