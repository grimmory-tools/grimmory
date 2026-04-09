import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {TagColor, TagComponent, TagSize, TagVariant} from './tag.component';

@Component({
  standalone: true,
  imports: [TagComponent],
  template: `
    <app-tag
      [color]="color"
      [size]="size"
      [variant]="variant"
      [rounded]="rounded"
      [pill]="pill"
      [customBgColor]="customBgColor"
      [customTextColor]="customTextColor"
    >
      Test
    </app-tag>
  `,
})
class TestHostComponent {
  color: TagColor = 'primary';
  size: TagSize = 'm';
  variant: TagVariant = 'label';
  rounded = false;
  pill = false;
  customBgColor = '#112233';
  customTextColor = '#fefefe';
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
    fixture.detectChanges();
  });

  it('renders the default color and size classes', () => {
    const tag = fixture.nativeElement.querySelector('span') as HTMLSpanElement;

    expect(tag.className).toContain('app-tag');
    expect(tag.className).toContain('app-tag-primary');
    expect(tag.className).toContain('app-tag-m');
    expect(tag.textContent?.trim()).toBe('Test');
  });

  it('adds pill and rounded classes when those inputs are enabled', () => {
    host.variant = 'pill';
    host.rounded = true;
    host.pill = true;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

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
