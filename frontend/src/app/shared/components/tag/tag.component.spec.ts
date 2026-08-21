import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {TagColor, TagComponent, TagSize, TagVariant} from './tag.component';

@Component({
  standalone: true,
  imports: [TagComponent],
  template: `
    <app-tag
      [color]="color()"
      [size]="size()"
      [variant]="variant()"
      [rounded]="rounded()"
      [pill]="pill()"
      [customBgColor]="customBgColor()"
      [customTextColor]="customTextColor()"
    >
      Test
    </app-tag>
  `,
})
class TestHostComponent {
  readonly color = signal<TagColor>('primary');
  readonly size = signal<TagSize>('m');
  readonly variant = signal<TagVariant>('label');
  readonly rounded = signal(false);
  readonly pill = signal(false);
  readonly customBgColor = signal('#112233');
  readonly customTextColor = signal('#fefefe');
}

describe('TagComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('renders the default color and size classes', () => {
    const tag = fixture.nativeElement.querySelector('span') as HTMLSpanElement;

    expect(tag.className).toContain('app-tag');
    expect(tag.className).toContain('app-tag-primary');
    expect(tag.className).toContain('app-tag-m');
    expect(tag.textContent?.trim()).toBe('Test');
  });

  it('adds pill and rounded classes when those inputs are enabled', async () => {
    host.variant.set('pill');
    host.rounded.set(true);
    host.pill.set(true);
    await fixture.whenStable();

    const tag = fixture.nativeElement.querySelector('span') as HTMLSpanElement;
    expect(tag.className).toContain('app-tag-variant-pill');
    expect(tag.className).toContain('app-tag-rounded');
    expect(tag.className).toContain('app-tag-pill');
  });

  it('applies the provided custom colors inline', () => {
    const tag = fixture.nativeElement.querySelector('span') as HTMLSpanElement;

    expect(tag.style.backgroundColor).toBe('rgb(17, 34, 51)');
    expect(tag.style.color).toBe('rgb(254, 254, 254)');
  });
});
