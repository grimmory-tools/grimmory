export enum MetadataBatchStatus {
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR',
  CANCELLED = 'CANCELLED',
}

export const MetadataBatchStatusLabels: Record<MetadataBatchStatus, string> = {
  [MetadataBatchStatus.IN_PROGRESS]: 'In Progress',
  [MetadataBatchStatus.COMPLETED]: 'Completed',
  [MetadataBatchStatus.ERROR]: 'Error',
  [MetadataBatchStatus.CANCELLED]: 'Cancelled',
};

export interface MetadataBatchProgressNotification {
  taskId: string;
  completed: number;
  total: number;
  message: string;
  status: MetadataBatchStatus;
  review: boolean;
}
