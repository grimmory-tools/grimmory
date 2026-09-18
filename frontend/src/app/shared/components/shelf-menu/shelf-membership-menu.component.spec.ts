import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {ShelfMembershipMenuComponent, type ShelfMembershipItem} from './shelf-membership-menu.component';

function shelves(count: number): ShelfMembershipItem[] {
  return Array.from({length: count}, (_, i) => ({id: i + 1, name: `Shelf ${i + 1}`, checked: false}));
}

describe('ShelfMembershipMenuComponent', () => {
  let fixture: ComponentFixture<ShelfMembershipMenuComponent>;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShelfMembershipMenuComponent, getTranslocoModule()],
    });
    fixture = TestBed.createComponent(ShelfMembershipMenuComponent);
    host = fixture.nativeElement as HTMLElement;
  });

  async function render(items: ShelfMembershipItem[]): Promise<void> {
    fixture.componentRef.setInput('shelves', items);
    await fixture.whenStable();
    if (!fixture.componentInstance.menu().isOpen()) {
      fixture.componentInstance.menu().open(host);
      await fixture.whenStable();
    }
  }

  async function setQuery(value: string): Promise<void> {
    const input = menuElement().querySelector('input') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input', {bubbles: true}));
    await fixture.whenStable();
  }

  function menuElement(): HTMLElement {
    return document.querySelector('app-menu[aria-label="Add to shelf"]') as HTMLElement;
  }

  function rowLabels(): string[] {
    return Array.from(menuElement().querySelectorAll('app-menu-checkbox')).map(el => el.textContent?.trim() ?? '');
  }

  it('does not render shelf rows until the menu first opens', async () => {
    fixture.componentRef.setInput('shelves', shelves(3));
    await fixture.whenStable();

    expect(host.querySelector('app-menu-checkbox')).toBeNull();

    fixture.componentInstance.menu().open(host);
    await fixture.whenStable();

    expect(rowLabels()).toHaveLength(3);
  });

  it('renders translated menu labels', async () => {
    await render(shelves(3));
    expect(menuElement().getAttribute('aria-label')).toBe('Add to shelf');
    expect(menuElement().querySelector('app-menu-item')?.textContent?.trim()).toBe('New shelf…');
  });

  it('hides the filter input for a short, scannable list', async () => {
    await render(shelves(8));
    expect(menuElement().querySelector('input')).toBeNull();
    expect(rowLabels().length).toBe(8);
  });

  it('shows the filter input past the threshold and filters rows by name', async () => {
    await render(shelves(12));
    expect(menuElement().querySelector('input')).not.toBeNull();

    await setQuery('Shelf 1');
    expect(rowLabels()).toEqual(['Shelf 1', 'Shelf 10', 'Shelf 11', 'Shelf 12']);
  });

  it('focuses the filter when a long menu opens', async () => {
    await render(shelves(12));
    expect(document.activeElement).toBe(menuElement().querySelector('input'));
  });

  it('shows a polite no-matches status instead of an empty gap', async () => {
    await render(shelves(12));
    await setQuery('zzz');
    expect(rowLabels()).toEqual([]);
    expect(menuElement().querySelector('[role="status"]')?.textContent).toContain('No results');
  });

  it('keeps typing away from the menu typeahead but hands Escape through', async () => {
    await render(shelves(12));
    const input = menuElement().querySelector('input') as HTMLInputElement;
    const reachedMenu = vi.fn();
    menuElement().addEventListener('keydown', reachedMenu);

    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'a', bubbles: true, cancelable: true}));
    expect(reachedMenu).not.toHaveBeenCalled();

    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true, cancelable: true}));
    expect(reachedMenu).toHaveBeenCalledTimes(1);
  });

  it('moves from the filter to the first or last item without skipping a shelf', async () => {
    await render(shelves(12));
    const input = menuElement().querySelector('input') as HTMLInputElement;
    const rows = menuElement().querySelectorAll('app-menu-checkbox');
    const newShelf = menuElement().querySelector('app-menu-item') as HTMLElement;

    input.focus();
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true, cancelable: true}));
    expect(document.activeElement).toBe(rows[0]);

    input.focus();
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowUp', bubbles: true, cancelable: true}));
    expect(document.activeElement).toBe(newShelf);
  });

  it('emits toggleShelf with the shelf id and next state', async () => {
    await render(shelves(3));
    const spy = vi.fn();
    fixture.componentInstance.toggleShelf.subscribe(spy);
    const row = menuElement().querySelectorAll('app-menu-checkbox')[1] as HTMLElement;
    row.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}));
    expect(spy).toHaveBeenCalledWith({shelfId: 2, checked: true});
  });

  it('clears a retained filter when the list shrinks below the threshold', async () => {
    await render(shelves(12));
    await setQuery('zzz');
    expect(rowLabels()).toEqual([]);

    fixture.componentRef.setInput('shelves', shelves(8));
    await fixture.whenStable();

    const retainedInput = menuElement().querySelector('input') as HTMLInputElement;
    expect(retainedInput.value).toBe('');
    expect(rowLabels().length).toBe(8);
    expect(fixture.componentInstance.menu().isOpen()).toBe(true);
    expect(document.activeElement).toBe(retainedInput);

    retainedInput.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowDown', bubbles: true, cancelable: true}));
    await fixture.whenStable();

    expect(menuElement().querySelector('input')).toBe(retainedInput);
    expect(document.activeElement).toBe(menuElement().querySelector('app-menu-checkbox'));

    await render(shelves(12));
    expect((menuElement().querySelector('input') as HTMLInputElement).value).toBe('');
    expect(rowLabels().length).toBe(12);

    fixture.componentRef.setInput('shelves', shelves(8));
    fixture.componentInstance.menu().close();
    await fixture.whenStable();
    fixture.componentInstance.menu().open(host);
    await fixture.whenStable();
    expect(menuElement().querySelector('input')).toBeNull();
  });

  it('emits createShelf when the New shelf item is chosen', async () => {
    await render(shelves(3));
    const spy = vi.fn();
    fixture.componentInstance.createShelf.subscribe(spy);
    const newShelf = menuElement().querySelector('app-menu-item') as HTMLElement;
    newShelf.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}));
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('renders the filter, rows and New shelf in logical order', async () => {
    await render(shelves(12));
    const order = Array.from(menuElement().querySelectorAll('input, app-menu-checkbox, app-menu-item')).map(el => {
      const tag = el.tagName.toLowerCase();
      return tag === 'input' ? 'filter' : tag === 'app-menu-item' ? 'newShelf' : 'row';
    });
    expect(order[0]).toBe('filter');
    expect(order.at(-1)).toBe('newShelf');
  });
});
