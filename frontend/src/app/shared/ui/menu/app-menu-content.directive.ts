import { Directive, inject, TemplateRef, ViewContainerRef } from '@angular/core';

@Directive({
  selector: 'ng-template[appMenuContent]',
  standalone: true,
})
export class AppMenuContentDirective {
  private readonly template = inject(TemplateRef);
  private readonly viewContainer = inject(ViewContainerRef);
  private rendered = false;

  render(): void {
    if (this.rendered) return;
    this.viewContainer.createEmbeddedView(this.template);
    this.rendered = true;
  }
}
