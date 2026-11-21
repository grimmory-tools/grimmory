import {Component, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {Button} from '@openng/optimus-ui/button';
import {DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {AutoComplete} from '@openng/optimus-ui/autocomplete';

import {FacetSuggestionsDirective} from '../../../book/components/facet-suggestions.directive';
import {MagicShelfService} from '../../service/magic-shelf.service';

@Component({
  selector: 'app-genre-shelf-dialog',
  standalone: true,
  imports: [AutoComplete, Button, FacetSuggestionsDirective, FormsModule, TranslocoDirective],
  templateUrl: './genre-shelf-dialog.component.html',
  styleUrl: './genre-shelf-dialog.component.scss',
})
export class GenreShelfDialogComponent {
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  readonly saving = signal(false);
  genre: string | null = null;

  createShelf(): void {
    const genre = this.genre?.trim();
    if (!genre || this.saving()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('genreShelf.toast.validationSummary'),
        detail: this.t.translate('genreShelf.toast.validationDetail'),
      });
      return;
    }

    this.saving.set(true);
    this.magicShelfService.saveShelf({
      name: genre,
      icon: null,
      isPublic: false,
      group: {
        type: 'group',
        join: 'and',
        rules: [{field: 'categories', operator: 'includes_any', value: [genre]}],
      },
    }).subscribe({
      next: (shelf) => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('genreShelf.toast.successSummary'),
          detail: this.t.translate('genreShelf.toast.successDetail', {name: shelf.name}),
        });
        this.dialogRef.close(shelf);
      },
      error: (error) => {
        this.saving.set(false);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('genreShelf.toast.errorSummary'),
          detail: error?.error?.message || this.t.translate('genreShelf.toast.errorDetail'),
        });
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
