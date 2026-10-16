import {Component, EventEmitter, provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {AutoComplete, type AutoCompleteCompleteEvent} from '@openng/optimus-ui/autocomplete';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {expect, it, vi} from 'vitest';

import {BookQueryService} from '../data/book-query.service';
import {FacetSuggestionsDirective} from './facet-suggestions.directive';

@Component({
  selector: 'app-stub-autocomplete',
  template: '',
  providers: [{provide: AutoComplete, useExisting: StubAutoComplete}],
})
class StubAutoComplete {
  readonly answers: string[][] = [];
  readonly completeMethod = new EventEmitter<AutoCompleteCompleteEvent>();
  set suggestions(values: string[]) {
    this.answers.push(values);
  }
}

@Component({
  imports: [StubAutoComplete, FacetSuggestionsDirective],
  template: '<app-stub-autocomplete appFacetSuggestions="author" />',
})
class Host {}

it('answers every search, including the same query typed again', async () => {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideTanStackQuery(new QueryClient()),
      {
        provide: BookQueryService,
        useValue: {
          facet: (key: string, _params: unknown, search: string) => ({
            queryKey: ['facet', key, search],
            queryFn: async () => ({key, values: [{value: 'Joseph Conrad', title: 'Joseph Conrad', count: 1}]}),
          }),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  const autoComplete = fixture.debugElement.children[0].injector.get(StubAutoComplete);

  autoComplete.completeMethod.emit({query: 'conr', originalEvent: new Event('input')});
  await vi.waitFor(() => expect(autoComplete.answers).toHaveLength(1));
  autoComplete.completeMethod.emit({query: 'conr', originalEvent: new Event('input')});
  await vi.waitFor(() => expect(autoComplete.answers).toHaveLength(2));

  expect(autoComplete.answers).toEqual([['Joseph Conrad'], ['Joseph Conrad']]);
});
