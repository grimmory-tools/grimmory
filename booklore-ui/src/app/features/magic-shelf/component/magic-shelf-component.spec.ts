import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {MagicShelfComponent, Rule} from './magic-shelf-component';
import {TranslocoService} from '@jsverse/transloco';
import {LibraryService} from '../../book/service/library.service';
import {ShelfService} from '../../book/service/shelf.service';
import {BookService} from '../../book/service/book.service';
import {MagicShelfService} from '../service/magic-shelf.service';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {UserService} from '../../settings/user-management/user.service';
import {IconPickerService} from '../../../shared/service/icon-picker.service';
import {of} from 'rxjs';

describe('MagicShelfComponent (Part 3)', () => {
  let component: MagicShelfComponent;

  const mockTransloco = {
    translate: vi.fn((key: string) => key),
    selectTranslation: vi.fn(() => of({})),
    langChanges$: of('en'),
    getActiveLang: vi.fn(() => 'en'),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {provide: TranslocoService, useValue: mockTransloco},
        {provide: LibraryService, useValue: {getLibrariesFromState: vi.fn(() => [])}},
        {provide: ShelfService, useValue: {getShelvesFromState: vi.fn(() => [])}},
        {provide: BookService, useValue: {bookState$: of({loaded: false, books: []})}},
        {provide: MagicShelfService, useValue: {}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: null}},
        {provide: UserService, useValue: {getCurrentUser: vi.fn(() => ({permissions: {admin: false}}))}},
        {provide: IconPickerService, useValue: {open: vi.fn(() => of(null))}},
      ]
    });

    const fixture = TestBed.createComponent(MagicShelfComponent);
    component = fixture.componentInstance;
  });

  describe('getOperatorOptionsForField', () => {
    it('should return is/isNot operators for seriesStatus', () => {
      const operators = component.getOperatorOptionsForField('seriesStatus');
      expect(operators).toHaveLength(2);
      expect(operators.map(o => o.value)).toEqual(['equals', 'not_equals']);
      expect(operators[0].label).toContain('is');
    });

    it('should return is/isNot operators for seriesPosition', () => {
      const operators = component.getOperatorOptionsForField('seriesPosition');
      expect(operators).toHaveLength(2);
      expect(operators.map(o => o.value)).toEqual(['equals', 'not_equals']);
    });

    it('should return has/hasNot operators for seriesGaps', () => {
      const operators = component.getOperatorOptionsForField('seriesGaps');
      expect(operators).toHaveLength(2);
      expect(operators.map(o => o.value)).toEqual(['equals', 'not_equals']);
      expect(operators[0].label).toContain('has');
    });

    it('should return has/hasNot operators for metadataPresence', () => {
      const operators = component.getOperatorOptionsForField('metadataPresence');
      expect(operators).toHaveLength(2);
      expect(operators.map(o => o.value)).toEqual(['equals', 'not_equals']);
      expect(operators[0].label).toContain('has');
      expect(operators[1].label).toContain('hasNot');
    });

    it('should include relative date operators for date fields', () => {
      const operators = component.getOperatorOptionsForField('dateFinished');
      const values = operators.map(o => o.value);
      expect(values).toContain('within_last');
      expect(values).toContain('older_than');
      expect(values).toContain('this_period');
    });

    it('should include relative date operators for addedOn', () => {
      const operators = component.getOperatorOptionsForField('addedOn');
      const values = operators.map(o => o.value);
      expect(values).toContain('within_last');
      expect(values).toContain('older_than');
      expect(values).toContain('this_period');
    });

    it('should include relative date operators for publishedDate', () => {
      const operators = component.getOperatorOptionsForField('publishedDate');
      const values = operators.map(o => o.value);
      expect(values).toContain('within_last');
      expect(values).toContain('older_than');
      expect(values).toContain('this_period');
    });

    it('should NOT include relative date operators for number fields', () => {
      const operators = component.getOperatorOptionsForField('pageCount');
      const values = operators.map(o => o.value);
      expect(values).not.toContain('within_last');
      expect(values).not.toContain('older_than');
      expect(values).not.toContain('this_period');
    });

    it('should include comparison operators for readingProgress', () => {
      const operators = component.getOperatorOptionsForField('readingProgress');
      const values = operators.map(o => o.value);
      expect(values).toContain('greater_than');
      expect(values).toContain('less_than');
      expect(values).toContain('in_between');
    });

    it('should NOT include relative date operators for readingProgress', () => {
      const operators = component.getOperatorOptionsForField('readingProgress');
      const values = operators.map(o => o.value);
      expect(values).not.toContain('within_last');
    });

    it('should return only base operators for boolean fields', () => {
      const operators = component.getOperatorOptionsForField('abridged');
      const values = operators.map(o => o.value);
      expect(values).toContain('equals');
      expect(values).toContain('not_equals');
      expect(values).toContain('is_empty');
      expect(values).toContain('is_not_empty');
      expect(values).not.toContain('contains');
      expect(values).not.toContain('greater_than');
    });
  });

  describe('buildRuleFromData', () => {
    it('should parse within_last value as number, not date', () => {
      const rule: Rule = {field: 'dateFinished', operator: 'within_last', value: 30, valueEnd: 'days'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe(30);
      expect(formGroup.get('valueEnd')?.value).toBe('days');
    });

    it('should preserve this_period value as string', () => {
      const rule: Rule = {field: 'addedOn', operator: 'this_period', value: 'month'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe('month');
      expect(formGroup.get('valueEnd')?.value).toBeNull();
    });

    it('should parse older_than value as number', () => {
      const rule: Rule = {field: 'lastReadTime', operator: 'older_than', value: 6, valueEnd: 'months'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe(6);
      expect(formGroup.get('valueEnd')?.value).toBe('months');
    });

    it('should still parse regular date values as Date objects', () => {
      const rule: Rule = {field: 'dateFinished', operator: 'equals', value: '2024-06-15'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBeInstanceOf(Date);
    });

    it('should parse composite field values as strings', () => {
      const rule: Rule = {field: 'seriesStatus', operator: 'equals', value: 'reading'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe('reading');
    });

    it('should parse readingProgress values as numbers', () => {
      const rule: Rule = {field: 'readingProgress', operator: 'greater_than', value: 50};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe(50);
    });
  });

  describe('onOperatorChange', () => {
    it('should set valueEnd to days for within_last operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('within_last');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('valueEnd')?.value).toBe('days');
      expect(ruleCtrl.get('value')?.value).toBeNull();
    });

    it('should set valueEnd to days for older_than operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('older_than');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('valueEnd')?.value).toBe('days');
      expect(ruleCtrl.get('value')?.value).toBeNull();
    });

    it('should set valueEnd to null for this_period operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('this_period');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('valueEnd')?.value).toBeNull();
      expect(ruleCtrl.get('value')?.value).toBeNull();
    });

    it('should set value to empty array for includes_any operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('includes_any');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toEqual([]);
    });

    it('should clear all values for is_empty operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('value')?.setValue('something');
      ruleCtrl.get('operator')?.setValue('is_empty');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toBeNull();
      expect(ruleCtrl.get('valueStart')?.value).toBeNull();
      expect(ruleCtrl.get('valueEnd')?.value).toBeNull();
    });
  });

  describe('option getters', () => {
    it('should return 5 series status options', () => {
      const options = component.seriesStatusOptions;
      expect(options).toHaveLength(5);
      expect(options.map(o => o.value)).toEqual(['reading', 'completed', 'ongoing', 'not_started', 'fully_read']);
    });

    it('should return 4 series gaps options', () => {
      const options = component.seriesGapsOptions;
      expect(options).toHaveLength(4);
      expect(options.map(o => o.value)).toEqual(['any_gap', 'missing_first', 'missing_latest', 'duplicate_number']);
    });

    it('should return 3 series position options', () => {
      const options = component.seriesPositionOptions;
      expect(options).toHaveLength(3);
      expect(options.map(o => o.value)).toEqual(['next_unread', 'first_in_series', 'last_in_series']);
    });

    it('should return 4 date unit options', () => {
      const options = component.dateUnitOptions;
      expect(options).toHaveLength(4);
      expect(options.map(o => o.value)).toEqual(['days', 'weeks', 'months', 'years']);
    });

    it('should return 3 date period options', () => {
      const options = component.datePeriodOptions;
      expect(options).toHaveLength(3);
      expect(options.map(o => o.value)).toEqual(['week', 'month', 'year']);
    });
  });

  describe('fieldOptions grouping', () => {
    it('should include readingProgress in organization group', () => {
      const groups = component.fieldOptions;
      const orgGroup = groups.find(g => g.label.includes('organization'));
      const fieldValues = orgGroup?.items.map(i => i.value) ?? [];
      expect(fieldValues).toContain('readingProgress');
    });

    it('should include seriesStatus, seriesGaps, seriesPosition in series group', () => {
      const groups = component.fieldOptions;
      const seriesGroup = groups.find(g => g.label.includes('series'));
      const fieldValues = seriesGroup?.items.map(i => i.value) ?? [];
      expect(fieldValues).toContain('seriesStatus');
      expect(fieldValues).toContain('seriesGaps');
      expect(fieldValues).toContain('seriesPosition');
    });
  });

  describe('getOperatorOptionsForField edge cases', () => {
    it('should return baseOperators + multiValueOperators for null field', () => {
      const operators = component.getOperatorOptionsForField(null);
      const values = operators.map(o => o.value);
      expect(operators).toHaveLength(7);
      expect(values).toContain('equals');
      expect(values).toContain('not_equals');
      expect(values).toContain('is_empty');
      expect(values).toContain('is_not_empty');
      expect(values).toContain('includes_any');
      expect(values).toContain('excludes_all');
      expect(values).toContain('includes_all');
    });

    it('should return baseOperators + multiValueOperators for undefined field', () => {
      const operators = component.getOperatorOptionsForField(undefined);
      const values = operators.map(o => o.value);
      expect(operators).toHaveLength(7);
      expect(values).toContain('equals');
      expect(values).toContain('not_equals');
      expect(values).toContain('is_empty');
      expect(values).toContain('is_not_empty');
      expect(values).toContain('includes_any');
      expect(values).toContain('excludes_all');
      expect(values).toContain('includes_all');
    });

    it('should include text operators for title field', () => {
      const operators = component.getOperatorOptionsForField('title');
      const values = operators.map(o => o.value);
      expect(values).toContain('contains');
      expect(values).toContain('does_not_contain');
      expect(values).toContain('starts_with');
      expect(values).toContain('ends_with');
      expect(values).toContain('includes_any');
      expect(values).toContain('excludes_all');
      expect(values).toContain('includes_all');
    });

    it('should NOT include text operators for library field', () => {
      const operators = component.getOperatorOptionsForField('library');
      const values = operators.map(o => o.value);
      expect(values).toContain('includes_any');
      expect(values).not.toContain('contains');
      expect(values).not.toContain('does_not_contain');
      expect(values).not.toContain('starts_with');
      expect(values).not.toContain('ends_with');
    });

    it('should NOT include text operators for readStatus field', () => {
      const operators = component.getOperatorOptionsForField('readStatus');
      const values = operators.map(o => o.value);
      expect(values).toContain('includes_any');
      expect(values).not.toContain('contains');
      expect(values).not.toContain('does_not_contain');
      expect(values).not.toContain('starts_with');
      expect(values).not.toContain('ends_with');
    });

    it('should include comparison operators for metadataScore', () => {
      const operators = component.getOperatorOptionsForField('metadataScore');
      const values = operators.map(o => o.value);
      expect(values).toContain('greater_than');
      expect(values).toContain('greater_than_equal_to');
      expect(values).toContain('less_than');
      expect(values).toContain('less_than_equal_to');
      expect(values).toContain('in_between');
    });

    it('should include relative date operators for lastReadTime', () => {
      const operators = component.getOperatorOptionsForField('lastReadTime');
      const values = operators.map(o => o.value);
      expect(values).toContain('within_last');
      expect(values).toContain('older_than');
      expect(values).toContain('this_period');
    });

    it('should NOT include comparison operators for string fields', () => {
      const operators = component.getOperatorOptionsForField('title');
      const values = operators.map(o => o.value);
      expect(values).not.toContain('greater_than');
      expect(values).not.toContain('greater_than_equal_to');
      expect(values).not.toContain('less_than');
      expect(values).not.toContain('less_than_equal_to');
      expect(values).not.toContain('in_between');
    });

    it('should include both text and multi-value operators for authors', () => {
      const operators = component.getOperatorOptionsForField('authors');
      const values = operators.map(o => o.value);
      expect(values).toContain('contains');
      expect(values).toContain('does_not_contain');
      expect(values).toContain('starts_with');
      expect(values).toContain('ends_with');
      expect(values).toContain('includes_any');
      expect(values).toContain('excludes_all');
      expect(values).toContain('includes_all');
    });
  });

  describe('buildRuleFromData edge cases', () => {
    it('should handle in_between for date fields', () => {
      const rule: Rule = {field: 'publishedDate', operator: 'in_between' as any, value: null, valueStart: '2024-01-01', valueEnd: '2024-12-31'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('valueStart')?.value).toBeInstanceOf(Date);
      expect(formGroup.get('valueEnd')?.value).toBeInstanceOf(Date);
    });

    it('should handle in_between for number fields', () => {
      const rule: Rule = {field: 'pageCount', operator: 'in_between' as any, value: null, valueStart: 100, valueEnd: 500};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('valueStart')?.value).toBe(100);
      expect(formGroup.get('valueEnd')?.value).toBe(500);
    });

    it('should preserve array values for includes_any', () => {
      const rule: Rule = {field: 'readStatus', operator: 'includes_any' as any, value: ['READ', 'READING']};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toEqual(['READ', 'READING']);
    });

    it('should handle is_empty operator with no value', () => {
      const rule: Rule = {field: 'title', operator: 'is_empty' as any, value: null};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBeNull();
    });

    it('should handle boolean field values', () => {
      const rule: Rule = {field: 'abridged', operator: 'equals', value: 'true'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe('true');
    });

    it('should handle string values for text fields', () => {
      const rule: Rule = {field: 'title', operator: 'contains' as any, value: 'Harry Potter'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe('Harry Potter');
    });

    it('should handle within_last with string number value', () => {
      const rule: Rule = {field: 'dateFinished', operator: 'within_last', value: '30', valueEnd: 'days'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('value')?.value).toBe(30);
    });

    it('should handle this_period with null valueEnd', () => {
      const rule: Rule = {field: 'addedOn', operator: 'this_period', value: 'week', valueEnd: 'something'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('valueEnd')?.value).toBeNull();
    });
  });

  describe('onOperatorChange edge cases', () => {
    it('should set value to empty array for excludes_all operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('excludes_all');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toEqual([]);
    });

    it('should set value to empty array for includes_all operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('includes_all');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toEqual([]);
    });

    it('should clear all values for is_not_empty operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('value')?.setValue('something');
      ruleCtrl.get('valueStart')?.setValue('start');
      ruleCtrl.get('valueEnd')?.setValue('end');
      ruleCtrl.get('operator')?.setValue('is_not_empty');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toBeNull();
      expect(ruleCtrl.get('valueStart')?.value).toBeNull();
      expect(ruleCtrl.get('valueEnd')?.value).toBeNull();
    });

    it('should set value to empty string for regular operators', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('equals');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toBe('');
      expect(ruleCtrl.get('valueStart')?.value).toBeNull();
      expect(ruleCtrl.get('valueEnd')?.value).toBeNull();
    });

    it('should set value to empty string for in_between operator', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('in_between');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toBe('');
    });

    it('should not affect form when called with already-set within_last', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('operator')?.setValue('within_last');
      component.onOperatorChange(ruleCtrl);
      ruleCtrl.get('value')?.setValue('15');
      ruleCtrl.get('operator')?.setValue('within_last');
      component.onOperatorChange(ruleCtrl);
      expect(ruleCtrl.get('value')?.value).toBeNull();
    });
  });

  describe('onFieldChange', () => {
    it('should reset operator and all values', () => {
      const ruleCtrl = component.createRule();
      ruleCtrl.get('field')?.setValue('title');
      ruleCtrl.get('operator')?.setValue('contains');
      ruleCtrl.get('value')?.setValue('some value');
      ruleCtrl.get('valueStart')?.setValue('start');
      ruleCtrl.get('valueEnd')?.setValue('end');
      component.onFieldChange(ruleCtrl);
      expect(ruleCtrl.get('operator')?.value).toBe('');
      expect(ruleCtrl.get('value')?.value).toBeNull();
      expect(ruleCtrl.get('valueStart')?.value).toBeNull();
      expect(ruleCtrl.get('valueEnd')?.value).toBeNull();
    });
  });

  describe('createRule and createGroup', () => {
    it('should create rule with empty defaults', () => {
      const rule = component.createRule();
      expect(rule.get('field')?.value).toBe('');
      expect(rule.get('operator')?.value).toBe('');
      expect(rule.get('value')?.value).toBeNull();
      expect(rule.get('valueStart')?.value).toBeNull();
      expect(rule.get('valueEnd')?.value).toBeNull();
    });

    it('should create group with and join and empty rules', () => {
      const group = component.createGroup();
      expect(group.get('type')?.value).toBe('group');
      expect(group.get('join')?.value).toBe('and');
      expect((group.get('rules') as any).length).toBe(0);
    });
  });

  describe('fieldOptions completeness', () => {
    it('should have 9 groups total', () => {
      expect(component.fieldOptions.length).toBe(9);
    });

    it('should map categories field to genre translation key', () => {
      const groups = component.fieldOptions;
      const bookInfoGroup = groups.find(g => g.label.includes('bookInfo'));
      const categoriesItem = bookInfoGroup?.items.find(i => i.value === 'categories');
      expect(categoriesItem).toBeDefined();
      expect(categoriesItem!.label).toContain('genre');
    });

    it('should include all audiobook fields in audiobook group', () => {
      const groups = component.fieldOptions;
      const audiobookGroup = groups.find(g => g.label.includes('audiobook'));
      const fieldValues = audiobookGroup?.items.map(i => i.value) ?? [];
      expect(fieldValues).toContain('narrator');
      expect(fieldValues).toContain('abridged');
      expect(fieldValues).toContain('audiobookDuration');
    });

    it('should include all date fields in dates group', () => {
      const groups = component.fieldOptions;
      const datesGroup = groups.find(g => g.label.includes('dates'));
      const fieldValues = datesGroup?.items.map(i => i.value) ?? [];
      expect(fieldValues).toContain('publishedDate');
      expect(fieldValues).toContain('dateFinished');
      expect(fieldValues).toContain('lastReadTime');
      expect(fieldValues).toContain('addedOn');
    });

    it('should include metadataScore and metadataPresence in qualityMetadata group', () => {
      const groups = component.fieldOptions;
      const qualityGroup = groups.find(g => g.label.includes('qualityMetadata'));
      const fieldValues = qualityGroup?.items.map(i => i.value) ?? [];
      expect(fieldValues).toContain('metadataScore');
      expect(fieldValues).toContain('metadataPresence');
    });

    it('should NOT include metadataScore in ratingsReviews group', () => {
      const groups = component.fieldOptions;
      const ratingsGroup = groups.find(g => g.label.includes('ratingsReviews'));
      const fieldValues = ratingsGroup?.items.map(i => i.value) ?? [];
      expect(fieldValues).not.toContain('metadataScore');
    });
  });

  describe('metadataPresenceOptions', () => {
    it('should return 10 groups', () => {
      const options = component.metadataPresenceOptions;
      expect(options).toHaveLength(10);
    });

    it('should have grouped structure with label and items', () => {
      const options = component.metadataPresenceOptions;
      options.forEach(group => {
        expect(group.label).toBeDefined();
        expect(group.items).toBeDefined();
        expect(group.items.length).toBeGreaterThan(0);
        group.items.forEach(item => {
          expect(item.label).toBeDefined();
          expect(item.value).toBeDefined();
        });
      });
    });

    it('should include key metadata fields', () => {
      const options = component.metadataPresenceOptions;
      const allValues = options.flatMap(g => g.items.map(i => i.value));
      expect(allValues).toContain('title');
      expect(allValues).toContain('thumbnailUrl');
      expect(allValues).toContain('authors');
      expect(allValues).toContain('isbn13');
      expect(allValues).toContain('goodreadsId');
      expect(allValues).toContain('audiobookDuration');
      expect(allValues).toContain('comicCharacters');
    });
  });

  describe('buildRuleFromData for metadataPresence', () => {
    it('should parse metadataPresence rule with string value', () => {
      const rule: Rule = {field: 'metadataPresence', operator: 'equals', value: 'thumbnailUrl'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('field')?.value).toBe('metadataPresence');
      expect(formGroup.get('operator')?.value).toBe('equals');
      expect(formGroup.get('value')?.value).toBe('thumbnailUrl');
    });

    it('should parse metadataPresence rule with not_equals', () => {
      const rule: Rule = {field: 'metadataPresence', operator: 'not_equals', value: 'description'};
      const formGroup = component.buildRuleFromData(rule);
      expect(formGroup.get('operator')?.value).toBe('not_equals');
      expect(formGroup.get('value')?.value).toBe('description');
    });
  });

  describe('numericFieldConfigMap', () => {
    it('should contain readingProgress with max 100', () => {
      const config = component.numericFieldConfigMap.get('readingProgress');
      expect(config).toBeDefined();
      expect(config!.type).toBe('decimal');
      expect(config!.max).toBe(100);
    });

    it('should contain personalRating with max 10', () => {
      const config = component.numericFieldConfigMap.get('personalRating');
      expect(config).toBeDefined();
      expect(config!.type).toBe('decimal');
      expect(config!.max).toBe(10);
    });

    it('should contain pageCount with type number and no max', () => {
      const config = component.numericFieldConfigMap.get('pageCount');
      expect(config).toBeDefined();
      expect(config!.type).toBe('number');
      expect(config!.max).toBeUndefined();
    });

    it('should not contain title', () => {
      const config = component.numericFieldConfigMap.get('title' as any);
      expect(config).toBeUndefined();
    });

    it('should contain abridged with type boolean', () => {
      const config = component.numericFieldConfigMap.get('abridged');
      expect(config).toBeDefined();
      expect(config!.type).toBe('boolean');
    });
  });

  describe('buildGroupFromData', () => {
    it('should build group with mixed rules and nested groups', () => {
      const groupData = {
        name: '',
        type: 'group' as const,
        join: 'or' as const,
        rules: [
          {field: 'title', operator: 'contains', value: 'test'} as Rule,
          {
            type: 'group' as const,
            join: 'and' as const,
            rules: [
              {field: 'pageCount', operator: 'greater_than', value: 100} as Rule
            ],
            name: ''
          }
        ]
      };
      const result = component.buildGroupFromData(groupData);
      expect(result.get('join')?.value).toBe('or');
      const rulesArray = result.get('rules') as any;
      expect(rulesArray.length).toBe(2);
      const nestedGroup = rulesArray.at(1);
      expect(nestedGroup.get('rules')).toBeDefined();
      expect(nestedGroup.get('rules').length).toBe(1);
    });

    it('should preserve join type', () => {
      const groupData = {
        name: '',
        type: 'group' as const,
        join: 'or' as const,
        rules: []
      };
      const result = component.buildGroupFromData(groupData);
      expect(result.get('join')?.value).toBe('or');
    });
  });
});
