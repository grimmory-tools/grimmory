import {Component, EventEmitter, Input, Output} from '@angular/core';
import {TranslocoDirective} from '@jsverse/transloco';
import {Button} from '@openng/optimus-ui/button';
import {Dialog} from '@openng/optimus-ui/dialog';
import {AuthorSearchResult} from '../../model/author.model';

@Component({
  selector: 'app-author-match-validation-dialog',
  standalone: true,
  templateUrl: './author-match-validation-dialog.component.html',
  styleUrls: ['./author-match-validation-dialog.component.scss'],
  imports: [Dialog, Button, TranslocoDirective]
})
export class AuthorMatchValidationDialogComponent {
  @Input() visible = false;
  @Input() authorName = '';
  @Input() candidates: AuthorSearchResult[] = [];
  @Input() disabled = false;

  @Output() candidateSelected = new EventEmitter<AuthorSearchResult>();
  @Output() dismissed = new EventEmitter<void>();
}
