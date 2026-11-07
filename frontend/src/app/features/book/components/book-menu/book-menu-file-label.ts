import {formatFileSizeKb} from '../../../../shared/util/file-size';
import {type BookFileResponse} from '../../data/book-response.models';

export interface BookFileLabelParts {
  base: string;
  suffix: string;
  full: string;
}

export function bookFileLabelParts(file: BookFileResponse): BookFileLabelParts {
  const name = file.fileName ?? file.bookType ?? '';
  const ext = (file.extension ?? '').trim();
  const size = file.fileSizeKb == null ? null : formatFileSizeKb(file.fileSizeKb);

  let base = name;
  if (ext && base.toLowerCase().endsWith(`.${ext.toLowerCase()}`)) {
    base = base.slice(0, -(ext.length + 1));
  }

  const extensionSuffix = ext ? `.${ext}` : '';
  const sizeSuffix = size ? ` (${size})` : '';
  const suffix = `${extensionSuffix}${sizeSuffix}`;
  const full = `${base}${suffix}`.trim();
  return {base: base || name, suffix, full: full || name};
}
