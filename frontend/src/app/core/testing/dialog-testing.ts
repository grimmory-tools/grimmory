import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService, ConfirmationService} from 'primeng/api';
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
