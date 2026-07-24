import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {MessageService, ConfirmationService} from '@openng/optimus-ui/api';
import {vi} from 'vitest';
import {MockProvider} from 'ng-mocks';

export function createDynamicDialogHarness<T>(data: T) {
  const dialogRef = {
    close: vi.fn(),
  };

  return {
    dialogRef,
    providers: [
      MockProvider(DynamicDialogConfig, {data}),
      MockProvider(DynamicDialogRef, dialogRef),
    ],
  };
}

export function createMessageServiceSpy() {
  return {
    add: vi.fn(),
  };
}

export function createConfirmServiceSpy() {
  return {
    confirm: vi.fn(),
  };
}

export function createMessageServiceProvider(messageService: ReturnType<typeof createMessageServiceSpy>) {
  return MockProvider(MessageService, messageService);
}

export function createConfirmServiceProvider(confirmationService: ReturnType<typeof createConfirmServiceSpy>) {
  return MockProvider(ConfirmationService, confirmationService);
}
