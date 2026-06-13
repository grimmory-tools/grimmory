import { booleanAttribute, ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { type FieldState, type ValidationError } from '@angular/forms/signals';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppButtonComponent } from '../button/app-button.component';
import { cn } from '../cn';
import {
  APP_FIELD_DEFAULT_ERROR_KEY,
  appFieldErrorKey,
  appFieldErrorMessage,
  type AppFieldLike,
} from '../field/app-field.context';
import { MESSAGE_ICONS, messageVariants, type MessageColor } from './app-message.variants';

type SummaryLayout = 'list' | 'inline';

@Component({
  selector: 'app-message',
  standalone: true,
  imports: [AppButtonComponent, TranslocoPipe],
  host: {
    '[class.block]': '!inline()',
    '[class.inline-block]': 'inline()',
    '[class.align-middle]': 'inline()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div [class]="rootClass()" [attr.role]="role()">
        <i [class]="iconClass()" aria-hidden="true"></i>
        <div class="min-w-0 flex-1">
          @if (field() && summary()) {
            @if (summaryLayout() === 'inline') {
              @if (hasMultipleSummaryIssues()) {
                <div class="relative flex min-w-0 items-center gap-2 pr-20 text-current">
                  <span class="min-w-0 flex-1 truncate font-medium text-current">
                    {{ activeIssueMessage() }}
                  </span>
                  <span class="shrink-0 text-xs text-current/80">
                    {{ 'shared.ui.message.issuePosition' | transloco: { current: activeSummaryIndex() + 1, count: summaryCount() } }}
                  </span>
                  <div class="absolute right-0 top-1/2 flex -translate-y-1/2 items-center gap-1">
                    <app-button
                      tone="danger"
                      variant="ghost"
                      size="sm"
                      iconOnly
                      icon="pi pi-chevron-up"
                      [ariaLabel]="'shared.ui.message.previousIssue' | transloco"
                      (clicked)="focusAdjacentSummaryIssue(-1)" />
                    <app-button
                      tone="danger"
                      variant="ghost"
                      size="sm"
                      iconOnly
                      icon="pi pi-chevron-down"
                      [ariaLabel]="'shared.ui.message.nextIssue' | transloco"
                      (clicked)="focusAdjacentSummaryIssue(1)" />
                  </div>
                </div>
              } @else {
                <button
                  type="button"
                  class="block w-full truncate text-left font-medium text-current underline-offset-2 hover:underline focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current"
                  (click)="focusAdjacentSummaryIssue(1)">
                  {{ activeIssueMessage() }}
                </button>
              }
            } @else {
              <p class="font-medium text-current">
                {{ 'shared.ui.message.issueCount' | transloco: { count: summaryCount() } }}
              </p>
              <ul class="mt-1 flex flex-col gap-0.5">
                @for (issue of summaryIssues(); track issue) {
                  <li>
                    <button
                      type="button"
                      class="rounded-sm text-left text-current underline underline-offset-2 hover:no-underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current"
                      (click)="focusIssue(issue)">
                      {{ issueLabel(issue) }}
                    </button>
                  </li>
                }
              </ul>
            }
          } @else if (field()) {
            {{ fieldText() }}
          } @else {
            <ng-content />
          }
        </div>
      </div>
    }
  `,
})
export class AppMessageComponent {
  readonly color = input<MessageColor>('neutral');
  readonly field = input<AppFieldLike | undefined>(undefined);
  readonly summary = input(false, { transform: booleanAttribute });
  readonly summaryLayout = input<SummaryLayout>('list');
  readonly inline = input(false, { transform: booleanAttribute });
  readonly styleClass = input('');

  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  private readonly summaryIssueIndex = signal(0);

  private readonly fieldState = computed<FieldState<unknown> | null>(() => this.field()?.() ?? null);

  protected readonly fieldMessages = computed<readonly string[]>(() => {
    this.activeLang();
    const state = this.fieldState();
    if (!state || !state.touched()) return [];
    return state.errors().map(error => appFieldErrorMessage(error, this.translateFallbackError));
  });
  protected readonly fieldText = computed(() => this.fieldMessages().join(' '));

  protected readonly summaryIssues = computed<readonly ValidationError.WithFieldTree[]>(() => {
    const state = this.fieldState();
    if (!state || !state.touched()) return [];
    return state.errorSummary();
  });
  protected readonly summaryCount = computed(() => this.summaryIssues().length);
  protected readonly activeSummaryIndex = computed(() => {
    const count = this.summaryCount();
    if (count === 0) return 0;
    return Math.min(this.summaryIssueIndex(), count - 1);
  });
  protected readonly hasMultipleSummaryIssues = computed(() => this.summaryCount() > 1);
  protected readonly activeIssueMessage = computed(() => {
    this.activeLang();
    const issue = this.summaryIssues()[this.activeSummaryIndex()];
    return issue ? appFieldErrorMessage(issue, this.translateFallbackError) : '';
  });

  protected readonly visible = computed(() => {
    if (!this.field()) return true;
    return this.summary() ? this.summaryCount() > 0 : this.fieldMessages().length > 0;
  });
  protected readonly resolvedColor = computed<MessageColor>(() => (this.field() ? 'red' : this.color()));
  protected readonly role = computed(() => (this.resolvedColor() === 'red' ? 'alert' : 'status'));
  protected readonly iconClass = computed(() =>
    cn(MESSAGE_ICONS[this.resolvedColor()], 'mt-0.5 shrink-0 text-[1.05em] leading-none'),
  );
  protected readonly rootClass = computed(() =>
    cn(messageVariants({ color: this.resolvedColor() }), this.inline() && 'inline-flex align-middle', this.styleClass()),
  );

  protected issueLabel(issue: ValidationError): string {
    this.activeLang();
    return appFieldErrorMessage(issue, this.translateFallbackError);
  }

  protected focusIssue(issue: ValidationError.WithFieldTree): void {
    this.focusAndReveal(issue);
  }

  protected focusAdjacentSummaryIssue(direction: 1 | -1): void {
    const count = this.summaryCount();
    if (count === 0) return;

    const nextIndex = (this.activeSummaryIndex() + direction + count) % count;
    this.summaryIssueIndex.set(nextIndex);
    const issue = this.summaryIssues()[nextIndex];
    if (issue) this.focusAndReveal(issue);
  }

  private focusAndReveal(issue: ValidationError.WithFieldTree): void {
    issue.fieldTree().focusBoundControl();
    const field = (document.activeElement as HTMLElement | null)?.closest('app-field');
    if (!(field instanceof HTMLElement)) return;

    field.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const highlightColor = 'color-mix(in srgb, var(--color-danger) 12%, transparent)';
    const highlightShadow = `0 0 0 6px ${highlightColor}`;
    field.animate(
      [
        { borderRadius: '0.25rem', backgroundColor: highlightColor, boxShadow: highlightShadow },
        {
          borderRadius: '0.25rem',
          backgroundColor: highlightColor,
          boxShadow: highlightShadow,
          offset: 0.45,
        },
        { borderRadius: '0.25rem', backgroundColor: 'transparent', boxShadow: '0 0 0 6px transparent' },
      ],
      { duration: 1400, easing: 'ease-out' },
    );
  }

  private readonly translateFallbackError = (kind: string): string => {
    const key = appFieldErrorKey(kind);
    const translated = this.transloco.translate(key);
    return translated === key ? this.transloco.translate(APP_FIELD_DEFAULT_ERROR_KEY) : translated;
  };
}
