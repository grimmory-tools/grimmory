import {describe, expect, it} from 'vitest';

import {BookMetadataHostService} from './book-metadata-host.service';

describe('BookMetadataHostService', () => {
  it('emits book switch requests through the shared subject', () => {
    const service = new BookMetadataHostService();
    const received: number[] = [];

    const subscription = service.bookSwitches$.subscribe(bookId => received.push(bookId));

    service.requestBookSwitch(42);
    service.switchBook(84);

    expect(received).toEqual([42, 84]);

    subscription.unsubscribe();
  });
});
