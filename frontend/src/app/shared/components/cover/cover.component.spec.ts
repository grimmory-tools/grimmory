import {TestBed} from '@angular/core/testing';
import {describe, expect, it} from 'vitest';

import {CoverComponent} from './cover.component';

function render(inputs: Record<string, unknown>): HTMLElement {
  const fixture = TestBed.createComponent(CoverComponent);
  for (const [name, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(name, value);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('CoverComponent placeholders', () => {
  it('shows the first and last initials for an initials placeholder', () => {
    expect(render({title: 'Charles A. Lindbergh', placeholder: 'initials'}).textContent?.trim()).toBe('CL');
    expect(render({title: 'Plato', placeholder: 'initials'}).textContent?.trim()).toBe('P');
    expect(render({title: 'E\u0301mile Zola', placeholder: 'initials'}).textContent?.trim()).toBe('E\u0301Z');
    expect(render({title: '   ', placeholder: 'initials'}).textContent?.trim()).toBe('');
  });

  it('falls back to the initials when the photo fails to load', () => {
    const fixture = TestBed.createComponent(CoverComponent);
    fixture.componentRef.setInput('src', '/author/1/thumbnail');
    fixture.componentRef.setInput('title', 'Ada Lovelace');
    fixture.componentRef.setInput('placeholder', 'initials');
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('img')!.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('img')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('AL');
  });

  it('keeps the typeset book placeholder by default', () => {
    const host = render({title: 'Dune', authors: ['Frank Herbert']});

    expect(host.querySelector('.placeholder-title')?.textContent?.trim()).toBe('Dune');
  });
});
