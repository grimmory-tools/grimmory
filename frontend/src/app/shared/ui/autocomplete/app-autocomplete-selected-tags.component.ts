import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { AppTagComponent } from '../tag/app-tag.component';
import { type TagSize } from '../tag/app-tag.variants';
import { type AppAutocompleteOption } from './app-autocomplete-option';

@Component({
  selector: 'app-autocomplete-selected-tags',
  standalone: true,
  imports: [AppTagComponent],
  host: { class: 'contents' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (tag of tags(); track tag.value) {
      <app-tag
        [size]="size()"
        [removable]="removable()"
        [label]="tag.label"
        [removeLabel]="removeTagLabel()"
        (remove)="remove.emit(tag.value)" />
    }
  `,
})
export class AppAutocompleteSelectedTagsComponent {
  readonly tags = input<readonly AppAutocompleteOption[]>([]);
  readonly size = input<TagSize>('md');
  readonly removable = input(true);
  readonly removeTagLabel = input('');

  readonly remove = output<string>();
}
