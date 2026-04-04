import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {CoverPlaceholderComponent, coverColorFor} from './cover-generator.component';

describe('CoverPlaceholderComponent', () => {
  let fixture: ComponentFixture<CoverPlaceholderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoverPlaceholderComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CoverPlaceholderComponent);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders the title and author text', () => {
    fixture.componentRef.setInput('title', 'Dune');
    fixture.componentRef.setInput('author', 'Frank Herbert');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Dune');
    expect(el.textContent).toContain('Frank Herbert');
  });

  it('applies the computed background color', () => {
    fixture.componentRef.setInput('title', 'Dune');
    fixture.componentRef.setInput('author', 'Frank Herbert');
    fixture.detectChanges();

    const container = fixture.nativeElement.querySelector('.placeholder');
    expect(container.style.background).toBeTruthy();
  });

  it('applies the correct size class', () => {
    fixture.componentRef.setInput('title', 'Test');
    fixture.componentRef.setInput('size', 'lg');
    fixture.detectChanges();

    const container = fixture.nativeElement.querySelector('.placeholder');
    expect(container.classList.contains('size-lg')).toBe(true);
  });
});

describe('coverColorFor', () => {
  it('returns a deterministic color for the same inputs', () => {
    const first = coverColorFor('Dune', 'Frank Herbert');
    const second = coverColorFor('Dune', 'Frank Herbert');
    expect(first).toBe(second);
  });

  it('returns different colors for different inputs', () => {
    const a = coverColorFor('Dune', 'Frank Herbert');
    const b = coverColorFor('Project Hail Mary', 'Andy Weir');
    expect(a).not.toBe(b);
  });

  it('returns a valid hex color', () => {
    const color = coverColorFor('Test', 'Author');
    expect(color).toMatch(/^#[0-9A-Fa-f]{6}$/);
  });
});
