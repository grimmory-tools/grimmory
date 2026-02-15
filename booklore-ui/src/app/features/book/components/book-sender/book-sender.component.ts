import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {EmailProvider} from '../../../settings/email-v2/email-provider.model';
import {EmailRecipient} from '../../../settings/email-v2/email-recipient.model';
import {EmailService} from '../../../settings/email-v2/email.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {EmailV2ProviderService} from '../../../settings/email-v2/email-v2-provider/email-v2-provider.service';
import {EmailV2RecipientService} from '../../../settings/email-v2/email-v2-recipient/email-v2-recipient.service';
import {Book, BookFile} from '../../model/book.model';
import {RadioButton} from 'primeng/radiobutton';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface EmailableFile {
  id: number;
  bookType?: string;
  fileSizeKb?: number;
  isPrimary: boolean;
}

const LARGE_FILE_THRESHOLD_KB = 25 * 1024; // 25MB

@Component({
  selector: 'app-book-sender',
  imports: [
    Button,
    Select,
    FormsModule,
    RadioButton,
    TranslocoDirective,
  ],
  templateUrl: './book-sender.component.html',
  styleUrls: ['./book-sender.component.scss']
})
export class BookSenderComponent implements OnInit {

  private emailProviderService = inject(EmailV2ProviderService);
  private emailRecipientService = inject(EmailV2RecipientService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  dynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig = inject(DynamicDialogConfig);

  book: Book = this.dynamicDialogConfig.data.book;

  emailProviders: { label: string, value: EmailProvider }[] = [];
  emailRecipients: { label: string, value: EmailRecipient }[] = [];
  selectedProvider?: { label: string; value: EmailProvider };
  selectedRecipient?: { label: string; value: EmailRecipient };

  emailableFiles: EmailableFile[] = [];
  selectedFileId?: number;

  ngOnInit(): void {
    this.buildEmailableFiles();

    this.emailProviderService.getEmailProviders().subscribe({
      next: (emailProviders: EmailProvider[]) => {
        this.emailProviders = emailProviders.map(provider => ({
          label: `${provider.name} | ${provider.fromAddress || provider.host}`,
          value: provider
        }));
      }
    });

    this.emailRecipientService.getRecipients().subscribe({
      next: (emailRecipients: EmailRecipient[]) => {
        this.emailRecipients = emailRecipients.map(recipient => ({
          label: `${recipient.name} | ${recipient.email}`,
          value: recipient
        }));
      }
    });
  }

  private buildEmailableFiles(): void {
    this.emailableFiles = [];

    // Add primary file
    if (this.book.primaryFile) {
      this.emailableFiles.push({
        id: this.book.primaryFile.id,
        bookType: this.book.primaryFile.bookType,
        fileSizeKb: this.book.primaryFile.fileSizeKb,
        isPrimary: true
      });
      this.selectedFileId = this.book.primaryFile.id;
    }

    // Add alternative formats (only book formats, not supplementary files)
    if (this.book.alternativeFormats) {
      for (const format of this.book.alternativeFormats) {
        this.emailableFiles.push({
          id: format.id,
          bookType: format.bookType,
          fileSizeKb: format.fileSizeKb,
          isPrimary: false
        });
      }
    }
  }

  get showLargeFileWarning(): boolean {
    if (!this.selectedFileId) return false;
    const selectedFile = this.emailableFiles.find(f => f.id === this.selectedFileId);
    return !!selectedFile?.fileSizeKb && selectedFile.fileSizeKb > LARGE_FILE_THRESHOLD_KB;
  }

  formatFileSize(fileSizeKb?: number): string {
    if (fileSizeKb == null) return '-';

    const units = ['KB', 'MB', 'GB', 'TB'];
    let size = fileSizeKb;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    const decimals = size >= 100 ? 0 : size >= 10 ? 1 : 2;
    return `${size.toFixed(decimals)} ${units[unitIndex]}`;
  }

  sendBook() {
    if (this.selectedProvider && this.selectedRecipient && this.book?.id) {
      const bookId = this.book.id;
      const recipientId = this.selectedRecipient.value.id;
      const providerId = this.selectedProvider.value.id;

      const emailRequest: { bookId: number, providerId: number, recipientId: number, bookFileId?: number } = {
        bookId,
        providerId,
        recipientId: recipientId,
      };

      // Include bookFileId if a specific file is selected and it's not the primary
      if (this.selectedFileId) {
        emailRequest.bookFileId = this.selectedFileId;
      }

      this.emailService.emailBook(emailRequest).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('book.sender.toast.emailScheduledSummary'),
            detail: this.t.translate('book.sender.toast.emailScheduledDetail')
          });
          this.dynamicDialogRef.close(true);
        },
        error: (error) => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('book.sender.toast.sendingFailedSummary'),
            detail: this.t.translate('book.sender.toast.sendingFailedDetail')
          });
          console.error('Error sending book:', error);
        }
      });
    } else {
      if (!this.selectedProvider) {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.sender.toast.providerMissingSummary'),
          detail: this.t.translate('book.sender.toast.providerMissingDetail')
        });
      }
      if (!this.selectedRecipient) {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.sender.toast.recipientMissingSummary'),
          detail: this.t.translate('book.sender.toast.recipientMissingDetail')
        });
      }
      if (!this.book?.id) {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.sender.toast.bookNotSelectedSummary'),
          detail: this.t.translate('book.sender.toast.bookNotSelectedDetail')
        });
      }
    }
  }
}
