import {Injectable} from '@angular/core';
import {FormGroup} from '@angular/forms';
import {BookMetadata} from '../../features/book/model/book.model';

@Injectable({
  providedIn: 'root'
})
export class MetadataUtilsService {

  copyFieldToForm(
    field: string,
    fetchedMetadata: BookMetadata,
    metadataForm: FormGroup,
    copiedFields: Record<string, boolean>
  ): boolean {
    const value = fetchedMetadata[field as keyof BookMetadata];
    if (value !== null && value !== undefined && value !== '') {
      metadataForm.get(field)?.setValue(value);
      copiedFields[field] = true;
      return true;
    }
    return false;
  }

  copyMissingFields(
    fetchedMetadata: BookMetadata,
    metadataForm: FormGroup,
    copiedFields: Record<string, boolean>,
    copyCallback: (field: string) => void
  ): void {
    for (const field of Object.keys(fetchedMetadata)) {
      const isLocked = metadataForm.get(`${field}Locked`)?.value;
      const currentValue = metadataForm.get(field)?.value;
      const fetchedValue = fetchedMetadata[field as keyof BookMetadata];

      const isEmpty = Array.isArray(currentValue)
        ? currentValue.length === 0
        : (currentValue === null || currentValue === undefined || currentValue === '');

      const hasFetchedValue = fetchedValue !== null && fetchedValue !== undefined && fetchedValue !== '';

      if (!isLocked && isEmpty && hasFetchedValue) {
        copyCallback(field);
      }
    }
  }

  copyAllFields(
    fetchedMetadata: BookMetadata,
    metadataForm: FormGroup,
    copyCallback: (field: string) => void,
    excludeFields: string[] = ['thumbnailUrl', 'bookId']
  ): void {
    for (const field of Object.keys(fetchedMetadata)) {
      if (excludeFields.includes(field)) continue;

      const isLocked = metadataForm.get(`${field}Locked`)?.value;
      const fetchedValue = fetchedMetadata[field as keyof BookMetadata];

      if (!isLocked && fetchedValue != null && fetchedValue !== '') {
        copyCallback(field);
      }
    }
  }

  isValueEmpty(value: unknown): boolean {
    if (value === null || value === undefined || value === '') return true;
    if (Array.isArray(value)) return value.length === 0;
    return false;
  }

  areFieldsEqual(field1: unknown, field2: unknown): boolean {
    const [normalized1, normalized2] = this.normalizeForComparison(field1, field2);
    return normalized1 === normalized2;
  }

  normalizeForComparison(field1: unknown, field2: unknown): [string | undefined, string | undefined] {
    let val1: string | undefined = undefined;
    let val2: string | undefined = undefined;

    if (Array.isArray(field1)) {
      val1 = field1.length > 0 ? JSON.stringify([...field1].sort()) : undefined;
    } else if (field1 != null && field1 !== '') {
      val1 = String(field1);
    }

    if (Array.isArray(field2)) {
      val2 = field2.length > 0 ? JSON.stringify([...field2].sort()) : undefined;
    } else if (field2 != null && field2 !== '') {
      val2 = String(field2);
    }

    return [val1, val2];
  }

  isValueChanged(field: string, metadataForm: FormGroup, originalMetadata?: BookMetadata): boolean {
    if (!originalMetadata) return false;
    const [current, original] = this.normalizeForComparison(
      metadataForm.get(field)?.value,
      originalMetadata[field as keyof BookMetadata]
    );
    return (!!current && current !== original) || (!current && !!original);
  }

  isFetchedDifferent(field: string, metadataForm: FormGroup, fetchedMetadata: BookMetadata): boolean {
    const [current, fetched] = this.normalizeForComparison(
      metadataForm.get(field)?.value,
      fetchedMetadata[field as keyof BookMetadata]
    );
    return !!fetched && fetched !== current;
  }

  resetField(
    field: string,
    metadataForm: FormGroup,
    originalMetadata: BookMetadata | undefined,
    copiedFields: Record<string, boolean>,
    hoveredFields?: Record<string, boolean>
  ): void {
    metadataForm.get(field)?.setValue(originalMetadata?.[field as keyof BookMetadata]);
    copiedFields[field] = false;
    if (hoveredFields) {
      hoveredFields[field] = false;
    }
  }

  patchMetadataToForm(
    metadata: BookMetadata,
    metadataForm: FormGroup,
    fields: Array<{ controlName: string; lockedKey: string; type: string }>
  ): void {
    const patchData: Record<string, unknown> = {};

    for (const field of fields) {
      const key = field.controlName as keyof BookMetadata;
      const lockedKey = field.lockedKey as keyof BookMetadata;
      const value = metadata[key];

      if (field.type === 'array') {
        patchData[field.controlName] = [...(value as string[] ?? [])].sort();
      } else {
        patchData[field.controlName] = value ?? null;
      }
      patchData[field.lockedKey] = metadata[lockedKey] ?? false;
    }

    metadataForm.patchValue(patchData);
  }
}
