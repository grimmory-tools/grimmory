import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {CoverGeneratorComponent} from './cover-generator.component';

describe('CoverGeneratorComponent', () => {
  let fixture: ComponentFixture<CoverGeneratorComponent>;
  let component: CoverGeneratorComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoverGeneratorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CoverGeneratorComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  function decodeCover(dataUrl: string): string {
    const encoded = dataUrl.replace('data:image/svg+xml;base64,', '');
    return decodeURIComponent(
      Array.from(atob(encoded))
        .map(char => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    );
  }

  it('generates a base64-encoded ebook cover with escaped title and author text', () => {
    component.title = 'Dune & <Messiah>';
    component.author = 'Frank "Herbert"';

    const svg = decodeCover(component.generateCover());

    expect(svg).toContain('<svg');
    expect(svg).toContain('Dune &amp;');
    expect(svg).toContain('&lt;Messiah&gt;');
    expect(svg).toContain('&quot;Herbert&quot;');
    expect(svg).toContain('width="250" height="350"');
  });

  it('generates a square audiobook cover when requested', () => {
    component.title = 'Project Hail Mary';
    component.author = 'Andy Weir';
    component.isSquare = true;

    const svg = decodeCover(component.generateCover());

    expect(svg).toContain('width="250" height="250"');
    expect(svg).toContain('L-6,8 L8,0 Z');
  });

  it('wraps and truncates long titles into multiple lines', () => {
    component.title = 'A Very Long Book Title That Keeps Going Beyond The Expected Width Of The Cover';
    component.author = 'Someone';

    const svg = decodeCover(component.generateCover());

    expect((svg.match(/<text x="125"/g) ?? []).length).toBeGreaterThan(2);
    expect(svg).toContain('Going Beyond');
    expect(svg).not.toContain('Expected Width');
  });

  it('produces deterministic palette selection for the same inputs', () => {
    component.title = 'Dune';
    component.author = 'Frank Herbert';

    const first = component.generateCover();
    const second = component.generateCover();

    expect(first).toBe(second);
  });
});
