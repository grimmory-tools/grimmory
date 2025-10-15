import {Component, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {EmailProvider} from '../../../settings/email/email-provider/email-provider.model';
import {EmailRecipient} from '../../../settings/email/email-recipient/email-recipient.model';
import {EmailService} from '../../../settings/email/email.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {EmailV2ProviderService} from '../../../settings/email-v2/email-v2-provider/email-v2-provider.service';
import {EmailV2RecipientService} from '../../../settings/email-v2/email-v2-recipient/email-v2-recipient.service';

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

  private emailProviderService = inject(EmailV2ProviderService);
  private emailRecipientService = inject(EmailV2RecipientService);
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
