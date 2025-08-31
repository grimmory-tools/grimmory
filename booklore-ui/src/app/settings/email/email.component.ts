import {Component} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {EmailProviderComponent} from './email-provider/email-provider.component';
import {EmailRecipientComponent} from './email-recipient/email-recipient.component';
import {Divider} from 'primeng/divider';

@Component({
  selector: 'app-email',
  imports: [
    FormsModule,
    TableModule,
    EmailProviderComponent,
    EmailRecipientComponent,
    Divider
  ],
  templateUrl: './email.component.html',
  styleUrls: ['./email.component.scss'],
})
export class EmailComponent {

}
