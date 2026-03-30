import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {EmptyComponent} from './empty.component';

describe('EmptyComponent', () => {
  let fixture: ComponentFixture<EmptyComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyComponent);
    fixture.detectChanges();
  });

  it('creates successfully', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the empty state template', () => {
    expect(fixture.nativeElement.textContent?.trim().length).toBeGreaterThan(0);
  });
});
