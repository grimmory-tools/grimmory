import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {EmailProviderService} from '../../../settings/email/email-provider/email-provider.service';
import {EmailRecipientService} from '../../../settings/email/email-recipient/email-recipient.service';
import {EmailProvider} from '../../../settings/email/email-provider/email-provider.model';
import {EmailRecipient} from '../../../settings/email/email-recipient/email-recipient.model';
import {EmailService} from '../../../settings/email/email.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Book} from '../../model/book.model';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-book-sender',
  imports: [
    Button,
    Select,
    FormsModule
  ],
  templateUrl: './book-sender.component.html',
  styleUrls: ['./book-sender.component.scss']
})
export class BookSenderComponent implements OnInit {

  private emailProviderService = inject(EmailProviderService);
  private emailRecipientService = inject(EmailRecipientService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dynamicDialogConfig = inject(DynamicDialogConfig);

  bookId: number = this.dynamicDialogConfig.data.bookId;

  emailProviders: { label: string, value: EmailProvider }[] = [];
  emailRecipients: { label: string, value: EmailRecipient }[] = [];
  selectedProvider?: any;
  selectedRecipient?: any;

  ngOnInit(): void {
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

  sendBook() {
    if (this.selectedProvider && this.selectedRecipient && this.bookId) {
      const bookId = this.bookId;
      const recipientId = this.selectedRecipient.value.id;
      const providerId = this.selectedProvider.value.id;

      const emailRequest = {
        bookId,
        providerId,
        recipientId: recipientId,
      };

      this.emailService.emailBook(emailRequest).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Email Scheduled',
            detail: 'The book has been successfully scheduled for sending.'
          });
          this.dynamicDialogRef.close(true);
        },
        error: (error) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Sending Failed',
            detail: 'There was an issue while scheduling the book for sending. Please try again later.'
          });
          console.error('Error sending book:', error);
        }
      });
    } else {
      if (!this.selectedProvider) {
        this.messageService.add({
          severity: 'error',
          summary: 'Email Provider Missing',
          detail: 'Please select an email provider to proceed.'
        });
      }
      if (!this.selectedRecipient) {
        this.messageService.add({
          severity: 'error',
          summary: 'Recipient Missing',
          detail: 'Please select a recipient to send the book.'
        });
      }
      if (!this.bookId) {
        this.messageService.add({
          severity: 'error',
          summary: 'Book Not Selected',
          detail: 'Please select a book to send.'
        });
      }
    }
  }
}
