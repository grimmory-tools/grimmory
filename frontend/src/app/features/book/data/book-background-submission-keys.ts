import {bookCommandKeys} from './book-command-keys';

export const bookBackgroundSubmissionKeys = {
  changeCovers: () => [...bookCommandKeys.all(), 'background-submission', 'change-covers'] as const,
  quickSend: () => [...bookCommandKeys.all(), 'background-submission', 'quick-send'] as const,
};
