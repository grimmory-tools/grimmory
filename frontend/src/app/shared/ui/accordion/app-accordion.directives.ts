import { Directive, inject, TemplateRef } from '@angular/core';

@Directive({ selector: 'ng-template[appAccordionHeader]', standalone: true })
export class AppAccordionHeaderDirective {
  readonly template = inject(TemplateRef);
}

@Directive({ selector: 'ng-template[appAccordionContent]', standalone: true })
export class AppAccordionContentDirective {
  readonly template = inject(TemplateRef);
}

@Directive({ selector: 'ng-template[appAccordionActions]', standalone: true })
export class AppAccordionActionsDirective {
  readonly template = inject(TemplateRef);
}
