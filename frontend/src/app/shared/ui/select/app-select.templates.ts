import { Directive, TemplateRef, inject } from '@angular/core';

interface AppSelectOptionTemplateContext<T = unknown> {
  $implicit: T;
}

@Directive({
  selector: 'ng-template[appSelectOption]',
  standalone: true,
})
export class AppSelectOptionTemplateDirective<T = unknown> {
  readonly template = inject<TemplateRef<AppSelectOptionTemplateContext<T>>>(TemplateRef);
}

@Directive({
  selector: 'ng-template[appSelectSelected]',
  standalone: true,
})
export class AppSelectSelectedTemplateDirective<T = unknown> {
  readonly template = inject<TemplateRef<AppSelectOptionTemplateContext<T>>>(TemplateRef);
}
