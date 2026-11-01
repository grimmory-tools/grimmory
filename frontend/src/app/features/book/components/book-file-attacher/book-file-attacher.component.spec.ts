import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {MessageService} from '@openng/optimus-ui/api';
import {AutoComplete} from '@openng/optimus-ui/autocomplete';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {BookCommandService} from '../../data/book-command.service';
import {BookSummary} from '../../data/book-response.models';
import {BookQueryService} from '../../data/book-query.service';
import {BookFileAttacherComponent} from './book-file-attacher.component';

function book(id: number): BookSummary {
  return {id, libraryId: 7, libraryName: 'Library', metadata: {bookId: id, title: `Book ${id}`, allMetadataLocked: false}};
}

async function setup() {
  const target = book(2);
  const attach = vi.fn();
  const fetchPage = vi.fn(async () => ({content: [target]}));
  TestBed.configureTestingModule({
    imports: [BookFileAttacherComponent, getTranslocoModule()],
    providers: [
      provideTanStackQuery(new QueryClient({defaultOptions: {queries: {retry: false}}})),
      {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
      {provide: DynamicDialogConfig, useValue: {data: {sourceBooks: [book(1)]}}},
      {provide: MessageService, useValue: {add: vi.fn()}},
      {provide: BookCommandService, useValue: {attachBookFiles: () => ({mutationKey: ['attach'], mutationFn: attach})}},
      {provide: BookQueryService, useValue: {page: (params: unknown) => ({queryKey: ['page', params], queryFn: fetchPage})}},
      {provide: AppSettingsService, useValue: {appSettings: () => null}},
    ],
  });
  const fixture = TestBed.createComponent(BookFileAttacherComponent);
  await vi.waitFor(() => expect(fixture.componentInstance.isSearching()).toBe(false));
  const control = fixture.debugElement.query(By.directive(AutoComplete));
  return {
    fixture, target, attach, fetchPage,
    component: fixture.componentInstance,
    autocomplete: control.componentInstance as AutoComplete,
    input: control.nativeElement.querySelector('input[role="combobox"]') as HTMLInputElement,
    dropdown: control.nativeElement.querySelector('button') as HTMLButtonElement,
  };
}

let originalScrollIntoView: PropertyDescriptor | undefined;
beforeEach(() => {
  vi.stubGlobal('matchMedia', vi.fn((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
    addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn(),
  })));
  originalScrollIntoView = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {configurable: true, value: vi.fn()});
});

afterEach(() => {
  TestBed.resetTestingModule();
  if (originalScrollIntoView) {
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', originalScrollIntoView);
  } else {
    delete (HTMLElement.prototype as {scrollIntoView?: unknown}).scrollIntoView;
  }
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('BookFileAttacherComponent', () => {
  it('reopens unchanged suggestions without leaving the Optimus spinner running', async () => {
    const {fixture, dropdown, autocomplete, target} = await setup();
    dropdown.click();
    await fixture.whenStable();

    autocomplete.overlayVisible = false;
    dropdown.click();
    await fixture.whenStable();
    expect(autocomplete.loading).toBe(false);
    expect(autocomplete.overlayVisible).toBe(true);
    expect(autocomplete.suggestions).toEqual([target]);
  });

  it('lets the dropdown request another attempt for the same failed search', async () => {
    const {fixture, component, dropdown, fetchPage, autocomplete, target} = await setup();
    fetchPage.mockRejectedValueOnce(new Error('offline'));
    await component.targetsQuery.refetch();
    await vi.waitFor(() => expect(component.targetsQuery.isError()).toBe(true));
    dropdown.click();
    await vi.waitFor(() => expect(component.targetsQuery.isSuccess()).toBe(true));
    await fixture.whenStable();
    expect(autocomplete.loading).toBe(false);
    expect(autocomplete.suggestions).toEqual([target]);
  });

  it('does not attach the previously selected book after its label is edited', async () => {
    const {fixture, component, dropdown, input, attach} = await setup();
    dropdown.click();
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('li[role="option"]') as HTMLElement).click();
    await fixture.whenStable();

    input.value = 'different';
    input.dispatchEvent(new InputEvent('input', {bubbles: true}));
    component.attach();
    expect(attach).not.toHaveBeenCalled();
  });
});
