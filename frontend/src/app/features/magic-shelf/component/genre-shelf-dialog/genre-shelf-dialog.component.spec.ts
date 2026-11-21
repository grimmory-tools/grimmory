import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {BookQueryService} from '../../../book/data/book-query.service';
import {MagicShelfService} from '../../service/magic-shelf.service';
import {GenreShelfDialogComponent} from './genre-shelf-dialog.component';

describe('GenreShelfDialogComponent', () => {
  const savedShelf = {id: 12, name: 'Fantasy', filterJson: '{}'};
  const magicShelfService = {saveShelf: vi.fn(() => of(savedShelf))};
  const dialogRef = {close: vi.fn()};
  const messageService = {add: vi.fn()};

  beforeEach(() => {
    vi.clearAllMocks();
    magicShelfService.saveShelf.mockReturnValue(of(savedShelf));
    TestBed.configureTestingModule({
      imports: [GenreShelfDialogComponent],
      providers: [
        {provide: MagicShelfService, useValue: magicShelfService},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
        {provide: BookQueryService, useValue: {}},
        {provide: QueryClient, useValue: {query: vi.fn()}},
      ],
    });
  });

  it('creates a dynamic shelf for the selected genre', () => {
    const fixture = TestBed.createComponent(GenreShelfDialogComponent);
    const component = fixture.componentInstance;
    component.genre = ' Fantasy ';

    component.createShelf();

    expect(magicShelfService.saveShelf).toHaveBeenCalledWith({
      name: 'Fantasy',
      icon: null,
      isPublic: false,
      group: {
        type: 'group',
        join: 'and',
        rules: [{field: 'categories', operator: 'includes_any', value: ['Fantasy']}],
      },
    });
    expect(dialogRef.close).toHaveBeenCalledWith(savedShelf);
  });

  it('does not create a shelf without a selected genre', () => {
    const fixture = TestBed.createComponent(GenreShelfDialogComponent);

    fixture.componentInstance.createShelf();

    expect(magicShelfService.saveShelf).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'warn'}));
  });

  it('keeps the dialog open and reports a save error', () => {
    magicShelfService.saveShelf.mockReturnValue(throwError(() => ({error: {message: 'Conflict'}})));
    const fixture = TestBed.createComponent(GenreShelfDialogComponent);
    fixture.componentInstance.genre = 'Fantasy';

    fixture.componentInstance.createShelf();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'Conflict',
    }));
  });
});
