import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';

export type TagColor =
  | 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'danger'
  | 'blue' | 'indigo' | 'purple' | 'pink' | 'red' | 'orange' | 'yellow'
  | 'green' | 'teal' | 'cyan' | 'gray' | 'slate' | 'zinc' | 'neutral'
  | 'stone' | 'amber' | 'lime' | 'emerald' | 'sky' | 'violet' | 'fuchsia'
  | 'rose' | 'dark' | 'light';

export type TagSize = '5xs' | '4xs' | '3xs' | '2xs' | 'xs' | 's' | 'm' | 'l' | 'xl' | '2xl' | '3xl' | '4xl' | '5xl';

export type TagVariant = 'label' | 'pill';

@Component({
  selector: 'app-tag',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      [class]="tagClasses()"
      [style.background-color]="customBgColor()"
      [style.color]="customTextColor()"
    >
      <ng-content></ng-content>
    </span>
  `,
  styleUrls: ['./tag.component.scss']
})
export class TagComponent {
  color = input<TagColor>('primary');
  size = input<TagSize>('m');
  variant = input<TagVariant>('label');
  rounded = input(false);
  pill = input(false);
  customBgColor = input<string>();
  customTextColor = input<string>();

  protected tagClasses = computed(() => {
    const classes = ['app-tag', `app-tag-${this.color()}`, `app-tag-${this.size()}`];
    if (this.variant() === 'pill') classes.push('app-tag-variant-pill');
    if (this.rounded()) classes.push('app-tag-rounded');
    if (this.pill()) classes.push('app-tag-pill');
    return classes.join(' ');
  });
}
