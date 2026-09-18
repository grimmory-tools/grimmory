import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';
import { LucideChevronDown } from '@lucide/angular';
import { cn } from '../cn';
import { connectedGroupClass, connectedItemClass } from '../connected-group';
import { AppButtonComponent } from '../button/app-button.component';
import { type ButtonSize, type ButtonTone, type ButtonVariant } from '../button/app-button.variants';
import { AppMenuComponent } from '../menu/app-menu.component';

@Component({
  selector: 'app-split-button',
  standalone: true,
  imports: [AppButtonComponent, LucideChevronDown],
  host: {
    class: 'inline-block align-middle',
    '[class.w-full]': 'fluid()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="rootClass()">
      <app-button
        [class.min-w-0]="fluid()"
        [class.flex-1]="fluid()"
        [buttonId]="buttonId()"
        [tone]="tone()"
        [variant]="variant()"
        [size]="size()"
        [fluid]="fluid()"
        [styleClass]="mainButtonClass()"
        [disabled]="disabled()"
        [loading]="loading()"
        [type]="type()"
        [name]="name()"
        [value]="value()"
        [form]="form()"
        [title]="title()"
        [tabIndex]="tabIndex()"
        [label]="label()"
        [ariaLabel]="ariaLabel()"
        [iconPos]="iconPos()"
        (clicked)="clicked.emit($event)">
        <ng-content />
      </app-button>
      <app-button
        #menuButton
        [buttonId]="menuButtonId()"
        type="button"
        iconOnly
        [tone]="tone()"
        [variant]="variant()"
        [size]="size()"
        [styleClass]="menuButtonClass()"
        [disabled]="isMenuDisabled()"
        [form]="form()"
        ariaHasPopup="menu"
        [ariaExpanded]="menuExpanded()"
        [ariaLabel]="menuAriaLabel() || ariaLabel() || label()"
        (clicked)="toggleMenu()">
        <svg lucideChevronDown aria-hidden="true"></svg>
      </app-button>
      <ng-content select="app-menu" />
    </span>
  `,
})
export class AppSplitButtonComponent {
  readonly tone = input<ButtonTone>('neutral');
  readonly variant = input<ButtonVariant>('soft');
  readonly size = input<ButtonSize>('md');
  readonly fluid = input(false, { transform: booleanAttribute });
  readonly styleClass = input('');
  readonly mainStyleClass = input('');
  readonly menuButtonStyleClass = input('');
  readonly buttonId = input('');
  readonly menuButtonId = input('');
  readonly name = input('');
  readonly value = input<string | number | null>(null);
  readonly form = input('');
  readonly title = input('');
  readonly tabIndex = input<number | null>(null);
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly menuAriaLabel = input('');
  readonly iconPos = input<'left' | 'right'>('left');
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly type = input<'button' | 'submit' | 'reset'>('button');

  readonly clicked = output<MouseEvent>();

  private readonly menu = contentChild(AppMenuComponent);
  private readonly menuButton = viewChild<unknown, ElementRef<HTMLElement>>('menuButton', { read: ElementRef });

  protected readonly isMenuDisabled = computed(() => this.disabled() || this.loading() || !this.menu()?.hasItems());
  protected readonly menuExpanded = computed(() =>
    this.menu()?.openerElement() === this.menuButton()?.nativeElement ? 'true' : 'false',
  );
  protected readonly rootClass = computed(() => cn(connectedGroupClass, this.fluid() && 'w-full', this.styleClass()));
  protected readonly mainButtonClass = computed(() =>
    cn(
      connectedItemClass({ first: true, last: false, prominent: this.tone() !== 'neutral' }),
      'relative focus-visible:z-10',
      this.mainStyleClass(),
    ),
  );
  protected readonly menuButtonClass = computed(() =>
    cn(
      connectedItemClass({ first: false, last: true, prominent: this.tone() !== 'neutral' }),
      'relative shrink-0 focus-visible:z-10',
      this.menuButtonStyleClass(),
    ),
  );

  protected toggleMenu(): void {
    const menu = this.menu();
    const origin = this.menuButton()?.nativeElement;
    if (!menu || !origin) return;
    if (menu.openerElement() === origin) {
      menu.close();
    } else {
      menu.open(origin);
    }
  }
}
