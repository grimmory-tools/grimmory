import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {
  AdditionalFile,
  AdditionalFileType,
  Book,
  BookFile,
} from '../../model/book.model';
import {BookSenderComponent} from './book-sender.component';
import {EmailProvider} from '../../../settings/email-v2/email-provider.model';
import {EmailRecipient} from '../../../settings/email-v2/email-recipient.model';
import {EmailService} from '../../../settings/email-v2/email.service';
import {EmailV2ProviderService} from '../../../settings/email-v2/email-v2-provider/email-v2-provider.service';
import {EmailV2RecipientService} from '../../../settings/email-v2/email-v2-recipient/email-v2-recipient.service';

type EmailBookRequest = Parameters<EmailService['emailBook']>[0];

describe('BookSenderComponent', () => {
  let emailProviders: EmailProvider[];
  let emailRecipients: EmailRecipient[];
  let emailBookError: Error | null;

  const providerService = {
    getEmailProviders: vi.fn(() => of(emailProviders)),
  };
  const recipientService = {
    getRecipients: vi.fn(() => of(emailRecipients)),
  };
  const emailService = {
    emailBook: vi.fn((request: EmailBookRequest) => {
      void request;
      return emailBookError
        ? throwError(() => emailBookError)
        : of(void 0);
    }),
  };
  const messageService = {
    add: vi.fn(),
  };
  const dialogRef = {
    close: vi.fn(),
  };
  const translocoService = {
    translate: vi.fn((key: string) => key),
  };
  const dialogConfig: {data: {book: Book}} = {
    data: {book: createBook()},
  };

  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    emailProviders = [
      createProvider({
        id: 101,
        name: 'Primary SMTP',
        fromAddress: 'books@example.com',
        host: 'smtp.primary.test',
      }),
      createProvider({
        id: 102,
        name: 'Fallback SMTP',
        fromAddress: '',
        host: 'smtp.fallback.test',
      }),
    ];
    emailRecipients = [
      createRecipient({
        id: 201,
        name: 'Alice Reader',
        email: 'alice@example.com',
      }),
    ];
    emailBookError = null;

    providerService.getEmailProviders.mockClear();
    recipientService.getRecipients.mockClear();
    emailService.emailBook.mockClear();
    messageService.add.mockClear();
    dialogRef.close.mockClear();
    translocoService.translate.mockClear();

    dialogConfig.data = {book: createBook()};

    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: EmailV2ProviderService, useValue: providerService},
        {provide: EmailV2RecipientService, useValue: recipientService},
        {provide: EmailService, useValue: emailService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: DynamicDialogConfig, useValue: dialogConfig},
      ],
    });
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
    TestBed.resetTestingModule();
  });

  it('builds emailable files and maps provider and recipient options on init', () => {
    const component = instantiateComponent(createBook({
      primaryFile: createBookFile({
        id: 11,
        bookId: 1,
        bookType: 'EPUB',
        fileSizeKb: 512,
      }),
      alternativeFormats: [
        createAdditionalFile({
          id: 22,
          bookId: 1,
          bookType: 'PDF',
          fileSizeKb: 4096,
          additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT,
        }),
        createAdditionalFile({
          id: 33,
          bookId: 1,
          bookType: 'MOBI',
          fileSizeKb: 2048,
          additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT,
        }),
      ],
      supplementaryFiles: [
        createAdditionalFile({
          id: 44,
          bookId: 1,
          bookType: 'PDF',
          fileSizeKb: 128,
          additionalFileType: AdditionalFileType.SUPPLEMENTARY,
        }),
      ],
    }));

    component.ngOnInit();

    expect(providerService.getEmailProviders).toHaveBeenCalledOnce();
    expect(recipientService.getRecipients).toHaveBeenCalledOnce();
    expect(component.emailProviders).toEqual([
      {
        label: 'Primary SMTP | books@example.com',
        value: emailProviders[0],
      },
      {
        label: 'Fallback SMTP | smtp.fallback.test',
        value: emailProviders[1],
      },
    ]);
    expect(component.emailRecipients).toEqual([
      {
        label: 'Alice Reader | alice@example.com',
        value: emailRecipients[0],
      },
    ]);
    expect(component.selectedFileId).toBe(11);
    expect(component.emailableFiles).toHaveLength(3);
    expect(component.emailableFiles.map(file => file.id)).toEqual([11, 22, 33]);
    expect(component.emailableFiles).toMatchObject([
      {id: 11, bookType: 'EPUB', fileSizeKb: 512, isPrimary: true},
      {id: 22, bookType: 'PDF', fileSizeKb: 4096, isPrimary: false},
      {id: 33, bookType: 'MOBI', fileSizeKb: 2048, isPrimary: false},
    ]);
  });

  it('formats file sizes across empty, kilobyte, and megabyte values', () => {
    const component = instantiateComponent();

    expect(component.formatFileSize()).toBe('-');
    expect(component.formatFileSize(512)).toBe('512 KB');
    expect(component.formatFileSize(1536)).toBe('1.50 MB');
  });

  it('shows the large-file warning only for selected files above the threshold', () => {
    const component = instantiateComponent(createBook({
      primaryFile: createBookFile({
        id: 11,
        bookId: 1,
        fileSizeKb: 512,
      }),
      alternativeFormats: [
        createAdditionalFile({
          id: 22,
          bookId: 1,
          fileSizeKb: 30 * 1024,
        }),
      ],
    }));

    component.ngOnInit();

    expect(component.showLargeFileWarning).toBe(false);

    component.selectedFileId = 22;
    expect(component.showLargeFileWarning).toBe(true);

    component.selectedFileId = undefined;
    expect(component.showLargeFileWarning).toBe(false);
  });

  it('shows validation errors and does not send when provider, recipient, or book are missing', () => {
    const component = instantiateComponent(createBook({id: 0}));

    component.sendBook();

    expect(emailService.emailBook).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledTimes(3);
    expect(messageService.add).toHaveBeenNthCalledWith(1, {
      severity: 'error',
      summary: 'book.sender.toast.providerMissingSummary',
      detail: 'book.sender.toast.providerMissingDetail',
    });
    expect(messageService.add).toHaveBeenNthCalledWith(2, {
      severity: 'error',
      summary: 'book.sender.toast.recipientMissingSummary',
      detail: 'book.sender.toast.recipientMissingDetail',
    });
    expect(messageService.add).toHaveBeenNthCalledWith(3, {
      severity: 'error',
      summary: 'book.sender.toast.bookNotSelectedSummary',
      detail: 'book.sender.toast.bookNotSelectedDetail',
    });
  });

  it('sends the selected file and closes the dialog after a successful response', () => {
    const component = instantiateComponent();
    component.ngOnInit();
    component.selectedProvider = component.emailProviders[0];
    component.selectedRecipient = component.emailRecipients[0];
    component.selectedFileId = 22;

    component.sendBook();

    expect(emailService.emailBook).toHaveBeenCalledWith({
      bookId: 1,
      providerId: 101,
      recipientId: 201,
      bookFileId: 22,
    });
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.sender.toast.emailScheduledSummary',
      detail: 'book.sender.toast.emailScheduledDetail',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows an error toast and keeps the dialog open when sending fails', () => {
    emailBookError = new Error('SMTP rejected request');

    const component = instantiateComponent();
    component.ngOnInit();
    component.selectedProvider = component.emailProviders[0];
    component.selectedRecipient = component.emailRecipients[0];

    component.sendBook();

    expect(emailService.emailBook).toHaveBeenCalledWith({
      bookId: 1,
      providerId: 101,
      recipientId: 201,
      bookFileId: 11,
    });
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'book.sender.toast.sendingFailedSummary',
      detail: 'book.sender.toast.sendingFailedDetail',
    });
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(consoleErrorSpy).toHaveBeenCalledOnce();
  });

  function instantiateComponent(book: Book = createBook()): BookSenderComponent {
    dialogConfig.data = {book};
    return TestBed.runInInjectionContext(() => new BookSenderComponent());
  }
});

function createBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    libraryId: 7,
    libraryName: 'Main Library',
    primaryFile: createBookFile({
      id: 11,
      bookId: 1,
      bookType: 'EPUB',
      fileSizeKb: 512,
    }),
    alternativeFormats: [
      createAdditionalFile({
        id: 22,
        bookId: 1,
        bookType: 'PDF',
        fileSizeKb: 2048,
      }),
    ],
    supplementaryFiles: [
      createAdditionalFile({
        id: 44,
        bookId: 1,
        bookType: 'PDF',
        fileSizeKb: 128,
        additionalFileType: AdditionalFileType.SUPPLEMENTARY,
      }),
    ],
    ...overrides,
  };
}

function createBookFile(overrides: Partial<BookFile> = {}): BookFile {
  return {
    id: 11,
    bookId: 1,
    fileName: 'book.epub',
    bookType: 'EPUB',
    fileSizeKb: 512,
    ...overrides,
  };
}

function createAdditionalFile(overrides: Partial<AdditionalFile> = {}): AdditionalFile {
  return {
    ...createBookFile(),
    additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT,
    ...overrides,
  };
}

function createProvider(overrides: Partial<EmailProvider> = {}): EmailProvider {
  return {
    isEditing: false,
    id: 101,
    userId: 1,
    name: 'Primary SMTP',
    host: 'smtp.example.test',
    port: 587,
    username: 'mailer',
    password: 'secret',
    fromAddress: 'books@example.com',
    auth: true,
    startTls: true,
    defaultProvider: true,
    shared: false,
    ...overrides,
  };
}

function createRecipient(overrides: Partial<EmailRecipient> = {}): EmailRecipient {
  return {
    id: 201,
    email: 'alice@example.com',
    name: 'Alice Reader',
    defaultRecipient: true,
    isEditing: false,
    ...overrides,
  };
}
