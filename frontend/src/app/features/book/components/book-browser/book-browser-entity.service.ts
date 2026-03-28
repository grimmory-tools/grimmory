import {Injectable, inject} from '@angular/core';
import {ParamMap} from '@angular/router';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {Book} from '../../model/book.model';
import {LibraryService} from '../../service/library.service';
import {ShelfService} from '../../service/shelf.service';
import {MagicShelf, MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../magic-shelf/component/magic-shelf-component';
import {EntityType} from './book-browser.component';

export interface EntityInfo {
  entityId: number;
  entityType: EntityType;
}

@Injectable({providedIn: 'root'})
export class BookBrowserEntityService {
  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private magicShelfService = inject(MagicShelfService);
  private bookRuleEvaluatorService = inject(BookRuleEvaluatorService);

  getEntityInfo(paramMap: ParamMap): EntityInfo {
    const libraryId = Number(paramMap.get('libraryId') || NaN);
    const shelfId = Number(paramMap.get('shelfId') || NaN);
    const magicShelfId = Number(paramMap.get('magicShelfId') || NaN);

    if (!Number.isNaN(libraryId)) {
      return {entityId: libraryId, entityType: EntityType.LIBRARY};
    }

    if (!Number.isNaN(shelfId)) {
      return {entityId: shelfId, entityType: EntityType.SHELF};
    }

    if (!Number.isNaN(magicShelfId)) {
      return {entityId: magicShelfId, entityType: EntityType.MAGIC_SHELF};
    }

    return {entityId: NaN, entityType: EntityType.ALL_BOOKS};
  }

  getEntity(entityId: number, entityType: EntityType): Library | Shelf | MagicShelf | null {
    switch (entityType) {
      case EntityType.LIBRARY:
        return this.libraryService.libraries().find(lib => lib.id === entityId) ?? null;
      case EntityType.SHELF:
        return this.shelfService.shelves().find(shelf => shelf.id === entityId) ?? null;
      case EntityType.MAGIC_SHELF:
        return this.magicShelfService.findShelfById(entityId) ?? null;
      default:
        return null;
    }
  }

  getBooksByEntity(books: Book[], entityId: number, entityType: EntityType): Book[] {
    switch (entityType) {
      case EntityType.LIBRARY:
        return books.filter(book => book.libraryId === entityId);
      case EntityType.SHELF:
        return books.filter(book => book.shelves?.some(shelf => shelf.id === entityId) ?? false);
      case EntityType.MAGIC_SHELF: {
        const magicShelf = this.getEntity(entityId, entityType);
        return this.isMagicShelf(magicShelf) ? this.filterByMagicShelf(books, magicShelf) : [];
      }
      case EntityType.UNSHELVED:
        return books.filter(book => !book.shelves || book.shelves.length === 0);
      case EntityType.ALL_BOOKS:
      default:
        return books;
    }
  }

  isLibrary(entity: Library | Shelf | MagicShelf): entity is Library {
    return (entity as Library).paths !== undefined;
  }

  isMagicShelf(entity: Library | Shelf | MagicShelf | null): entity is MagicShelf {
    return !!entity && 'filterJson' in entity;
  }

  private filterByMagicShelf(books: Book[], magicShelf: MagicShelf): Book[] {
    if (!magicShelf.filterJson) {
      return [];
    }

    try {
      const groupRule = JSON.parse(magicShelf.filterJson) as GroupRule;
      return books.filter(book => this.bookRuleEvaluatorService.evaluateGroup(book, groupRule, books));
    } catch {
      console.warn('Invalid filterJson for MagicShelf');
      return [];
    }
  }
}
