export type LibraryShelfMenuTarget =
  | {type: 'library'; entity: {id: number; name: string}}
  | {type: 'shelf'; entity: {id: number; name: string; userId?: number; publicShelf?: boolean}}
  | {
      type: 'magicShelf';
      entity: {id: number; name: string; filterJson: string; isPublic?: boolean};
    };
