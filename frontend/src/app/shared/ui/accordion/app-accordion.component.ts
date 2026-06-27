import { NgTemplateOutlet } from '@angular/common';
import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  contentChild,
  input,
  model,
  numberAttribute,
} from '@angular/core';
import { AccordionContent, AccordionGroup, AccordionPanel, AccordionTrigger } from '@angular/aria/accordion';
import { LucideChevronDown } from '@lucide/angular';

import {
  AppAccordionActionsDirective,
  AppAccordionContentDirective,
  AppAccordionHeaderDirective,
} from './app-accordion.directives';
import { neutralControlBorderClass } from '../control.styles';

@Component({
  selector: 'app-accordion',
  standalone: true,
  imports: [NgTemplateOutlet, AccordionGroup, AccordionPanel, AccordionTrigger, AccordionContent, LucideChevronDown],
  host: { class: 'block' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div ngAccordionGroup [multiExpandable]="multiple()" [disabled]="disabled()" [class]="rootClass">
      @for (item of items(); track $index; let i = $index) {
        <div>
          <div [class]="headerRowClass">
            <div role="heading" [attr.aria-level]="headingLevel()" class="min-w-0 flex-1">
              <button
                type="button"
                ngAccordionTrigger
                #trigger="ngAccordionTrigger"
                [panel]="panelRef"
                [expanded]="isOpen(i)"
                (expandedChange)="onToggle(i, $event)"
                [class]="triggerClass">
                <span [class]="actionsTemplate() ? labelWithActionsClass : labelClass">
                  @if (headerTemplate(); as tpl) {
                    <ng-container
                      [ngTemplateOutlet]="tpl.template"
                      [ngTemplateOutletContext]="{ $implicit: item, index: i, expanded: trigger.expanded() }" />
                  } @else {
                    {{ item }}
                  }
                </span>
                <svg lucideChevronDown [class]="chevronClass" [class.rotate-180]="trigger.expanded()" aria-hidden="true"></svg>
              </button>
            </div>
            @if (actionsTemplate(); as tpl) {
              <div [class]="actionsOverlayClass">
                <div [class]="actionsClass" tabindex="-1" (keydown)="$event.stopPropagation()">
                  <ng-container
                    [ngTemplateOutlet]="tpl.template"
                    [ngTemplateOutletContext]="{ $implicit: item, index: i, expanded: trigger.expanded() }" />
                </div>
              </div>
            }
          </div>

          <div
            ngAccordionPanel
            #panelRef="ngAccordionPanel"
            [preserveContent]="preserveContent()"
            class="grid transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none"
            [style.grid-template-rows]="trigger.expanded() ? '1fr' : '0fr'">
            <div class="overflow-hidden">
              <ng-template ngAccordionContent>
                <div [class]="contentClass">
                  @if (contentTemplate(); as tpl) {
                    <ng-container [ngTemplateOutlet]="tpl.template" [ngTemplateOutletContext]="{ $implicit: item, index: i }" />
                  }
                </div>
              </ng-template>
            </div>
          </div>
        </div>
      }
    </div>
  `,
})
export class AppAccordionComponent {
  readonly items = input<readonly unknown[]>([]);
  readonly multiple = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly preserveContent = input(true, { transform: booleanAttribute });
  readonly headingLevel = input(3, { transform: numberAttribute });
  readonly value = model<number | number[] | null>(null);

  protected readonly headerTemplate = contentChild(AppAccordionHeaderDirective);
  protected readonly contentTemplate = contentChild(AppAccordionContentDirective);
  protected readonly actionsTemplate = contentChild(AppAccordionActionsDirective);

  protected readonly rootClass =
    'divide-y divide-border overflow-hidden rounded-md border ' +
    `${neutralControlBorderClass} bg-card shadow-control`;
  protected readonly headerRowClass = 'relative flex items-stretch';
  protected readonly triggerClass =
    'relative flex h-full w-full cursor-pointer select-none items-center px-3 py-2.5 text-sm font-medium text-text-strong outline-hidden ' +
    'transition-colors hover:bg-surface-hover focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary ' +
    'aria-disabled:cursor-default aria-disabled:opacity-50';
  protected readonly labelClass = 'min-w-0 flex-1 pr-7 text-left';
  protected readonly labelWithActionsClass = 'min-w-0 flex-1 pr-24 text-left';
  protected readonly chevronClass =
    'pointer-events-none absolute right-3 top-1/2 size-4 shrink-0 -translate-y-1/2 text-text-muted transition-transform duration-200';
  protected readonly actionsOverlayClass = 'pointer-events-none absolute inset-y-0 right-8 flex items-center';
  protected readonly actionsClass = 'pointer-events-auto flex shrink-0 items-center gap-1';
  protected readonly contentClass = 'surface-page border-t border-border px-3 py-3 text-sm text-text';

  protected isOpen(index: number): boolean {
    const value = this.value();
    return this.multiple() ? Array.isArray(value) && value.includes(index) : value === index;
  }

  protected onToggle(index: number, expanded: boolean): void {
    if (this.multiple()) {
      const value = this.value();
      const current = Array.isArray(value) ? value : [];
      this.value.set(expanded ? [...current, index] : current.filter((open) => open !== index));
      return;
    }
    if (expanded) {
      this.value.set(index);
    } else if (this.value() === index) {
      this.value.set(null);
    }
  }
}
