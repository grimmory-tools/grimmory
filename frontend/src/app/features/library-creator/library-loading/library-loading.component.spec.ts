import {describe, expect, it} from 'vitest';

import {LibraryLoadingComponent} from './library-loading.component';

describe('LibraryLoadingComponent', () => {
  it('computes progress percentage and completion state from the latest update', () => {
    const component = new LibraryLoadingComponent();

    component.updateProgress('Dune', 2, 3);

    expect(component.bookTitle()).toBe('Dune');
    expect(component.current()).toBe(2);
    expect(component.total()).toBe(3);
    expect(component.percentage()).toBe(67);
    expect(component.isComplete()).toBe(false);
  });

  it('marks the flow complete when current reaches the total', () => {
    const component = new LibraryLoadingComponent();

    component.updateProgress('Children of Dune', 5, 5);

    expect(component.isComplete()).toBe(true);
    expect(component.percentage()).toBe(100);
  });

  it('truncates very long titles for display', () => {
    const component = new LibraryLoadingComponent();

    component.updateProgress('A'.repeat(200), 2, 3)

    expect(component.bookTitle()).toBe(`${'A'.repeat(50)}...`);
  });

  it('returns zero percent when there is no total yet', () => {
    const component = new LibraryLoadingComponent();

    expect(component.percentage()).toBe(0);
  });
});
