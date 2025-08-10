export enum TaskStatus {
  IN_PROGRESS = 'IN_PROGRESS',
  CANCELLED = 'CANCELLED',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}

export interface LogNotification {
  timestamp?: string;
  message: string;
}

export function parseLogNotification(messageBody: string): LogNotification {
  const raw = JSON.parse(messageBody);
  return {
    timestamp: raw.timestamp ? new Date(raw.timestamp).toLocaleTimeString() : undefined,
    message: raw.message,
  };
}

export interface TaskMessage {
  taskId: string;
  timestamp: string;
  title?: string;
  message: string;
  cancellable: boolean;
  status: TaskStatus;
}

export function parseTaskMessage(messageBody: string): TaskMessage {
  const raw = JSON.parse(messageBody) as TaskMessage;
  return {
    ...raw,
    timestamp: new Date(raw.timestamp).toLocaleTimeString()
  };
}
