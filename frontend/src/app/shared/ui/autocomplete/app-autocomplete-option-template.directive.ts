import { Directive, TemplateRef, inject } from '@angular/core';

import { type AppAutocompleteOption } from './app-autocomplete-option';

export interface AppAutocompleteOptionContext<T = unknown> {
  $implicit: AppAutocompleteOption<T>;
}

@Directive({
  selector: 'ng-template[appAutocompleteOption]',
  standalone: true,
})
export class AppAutocompleteOptionTemplateDirective<T = unknown> {
  readonly template = inject<TemplateRef<AppAutocompleteOptionContext<T>>>(TemplateRef);
}
