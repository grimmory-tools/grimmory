import {ChangeDetectionStrategy, Component, computed, inject, input, viewChild} from '@angular/core';
import {NgClass, NgTemplateOutlet} from '@angular/common';
import {Router, RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideArrowLeft, LucideEllipsisVertical} from '@lucide/angular';
import {AppMenuComponent} from '../../ui/menu/app-menu.component';
import {AppMenuItem} from '../../ui/menu/app-menu.items';
import {AppButtonComponent} from '../../ui/button/app-button.component';
import {PageHeaderBreadcrumb} from './page-header.service';

const COLLAPSE_AFTER_ITEMS = 4;

@Component({
  selector: 'app-page-header-breadcrumbs',
  standalone: true,
  imports: [
    AppButtonComponent,
    AppMenuComponent,
    LucideArrowLeft,
    LucideEllipsisVertical,
    NgClass,
    NgTemplateOutlet,
    RouterLink,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  template: `
    @if (trail().length) {
    <nav
      class="flex min-w-0 items-center gap-1.5"
      [attr.aria-label]="'layout.pageHeader.breadcrumbAriaLabel' | transloco"
    >
      @if (backButton(); as target) {
        <app-button
          class="-my-0.5 -ml-[7px]"
          iconOnly
          variant="ghost"
          size="sm"
          styleClass="h-7 w-7 pointer-coarse:h-7 pointer-coarse:w-7"
          [ariaLabel]="'layout.pageHeader.backTo' | transloco: {label: target.label}"
          (clicked)="navigate(target)">
          <svg lucideArrowLeft></svg>
        </app-button>
      }
      <ol class="m-0 -ml-1 flex min-w-0 list-none items-center gap-1.5 overflow-hidden p-0 text-sm font-medium text-text-secondary">
        @if (leadingBreadcrumb(); as breadcrumb) {
          <li class="inline-flex min-w-0 items-center gap-1.5">
            <ng-container [ngTemplateOutlet]="breadcrumbTpl" [ngTemplateOutletContext]="{breadcrumb, last: false}" />
          </li>
          <li class="inline-flex items-center gap-1.5">
            <ng-container [ngTemplateOutlet]="separatorTpl" />
            <app-button
              class="-my-0.5"
              iconOnly
              variant="ghost"
              size="sm"
              styleClass="h-7 w-7 pointer-coarse:h-7 pointer-coarse:w-7"
              [ariaLabel]="'layout.pageHeader.moreBreadcrumbs' | transloco"
              ariaHasPopup="menu"
              (clicked)="toggleHiddenBreadcrumbs($event)">
              <svg lucideEllipsisVertical></svg>
            </app-button>
            <ng-container [ngTemplateOutlet]="separatorTpl" />
          </li>
        }
        @for (breadcrumb of trailingBreadcrumbs(); track breadcrumbKey(breadcrumb); let last = $last) {
          <li class="inline-flex min-w-0 items-center gap-1.5">
            <ng-container [ngTemplateOutlet]="breadcrumbTpl" [ngTemplateOutletContext]="{breadcrumb, last: last && !dropCurrent()}" />
            @if (!last) {
              <ng-container [ngTemplateOutlet]="separatorTpl" />
            }
          </li>
        }
      </ol>
    </nav>
    @if (hiddenBreadcrumbs().length) {
      <app-menu #hiddenBreadcrumbMenu [model]="hiddenBreadcrumbMenuItems()" />
    }
    }

    <ng-template #breadcrumbTpl let-breadcrumb="breadcrumb" let-last="last">
      @if (breadcrumb.commands?.length && !last) {
        <a
          class="inline-flex min-h-6 min-w-0 max-w-40 items-center rounded px-1 text-inherit transition-colors duration-150 hover:bg-text/8 hover:text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary md:max-w-72"
          [ngClass]="mobile() ? touchTargetClass : ''"
          [routerLink]="routeCommands(breadcrumb)"
        >
          <span class="truncate">{{ breadcrumb.label }}</span>
        </a>
      } @else {
        <span
          class="inline-flex min-h-6 min-w-0 max-w-40 items-center overflow-hidden rounded px-1 md:max-w-72"
          [class.text-text]="last"
          [attr.aria-current]="last ? 'page' : null"
        >
          <span class="truncate">{{ breadcrumb.label }}</span>
        </span>
      }
    </ng-template>

    <ng-template #separatorTpl>
      <span class="shrink-0 select-none text-text-secondary/70" aria-hidden="true">/</span>
    </ng-template>
  `,
})
export class AppPageHeaderBreadcrumbsComponent {
  readonly breadcrumbs = input.required<readonly PageHeaderBreadcrumb[]>();
  readonly mobile = input(false);
  readonly dropCurrent = input(false);
  readonly touchTargetClass = "relative after:absolute after:inset-x-0 after:-inset-y-3 after:content-['']";

  private readonly router = inject(Router);
  private readonly hiddenBreadcrumbMenu = viewChild<AppMenuComponent>('hiddenBreadcrumbMenu');

  readonly backButton = computed(() => {
    if (this.mobile()) return null;
    const parent = this.breadcrumbs().at(-2);
    return parent?.commands?.length ? parent : null;
  });
  readonly trail = computed(() => (this.dropCurrent() ? this.breadcrumbs().slice(0, -1) : this.breadcrumbs()));
  readonly shouldCollapse = computed(() => this.trail().length > COLLAPSE_AFTER_ITEMS);
  readonly leadingBreadcrumb = computed(() => (this.shouldCollapse() ? this.trail()[0] : null));
  readonly hiddenBreadcrumbs = computed(() => (this.shouldCollapse() ? this.trail().slice(1, -2) : []));
  readonly trailingBreadcrumbs = computed(() => (this.shouldCollapse() ? this.trail().slice(-2) : this.trail()));
  readonly hiddenBreadcrumbMenuItems = computed<AppMenuItem[]>(() =>
    this.hiddenBreadcrumbs().map(breadcrumb => ({
      label: breadcrumb.label,
      disabled: !breadcrumb.commands?.length,
      command: () => this.navigate(breadcrumb),
    })),
  );

  navigate(breadcrumb: PageHeaderBreadcrumb): void {
    if (breadcrumb.commands?.length) {
      this.router.navigate([...breadcrumb.commands]);
    }
  }

  routeCommands(breadcrumb: PageHeaderBreadcrumb): unknown[] {
    return [...(breadcrumb.commands ?? [])];
  }

  toggleHiddenBreadcrumbs(event: MouseEvent): void {
    this.hiddenBreadcrumbMenu()?.toggle(event);
  }

  breadcrumbKey(breadcrumb: PageHeaderBreadcrumb): string {
    const commands = breadcrumb.commands?.map(command => String(command)).join('/') ?? '';
    return `${commands}|${breadcrumb.label}`;
  }
}
