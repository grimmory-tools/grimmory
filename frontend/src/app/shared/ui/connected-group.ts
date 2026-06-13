import { cn } from './cn';

interface ConnectedItemState {
  first: boolean;
  last: boolean;
  prominent?: boolean;
}

export function connectedItemClass(state: ConnectedItemState): string {
  return cn(
    'app-cg-item relative',
    state.prominent && 'app-cg-prominent',
    state.first ? 'rounded-l-md' : 'rounded-l-none',
    state.last ? 'app-cg-last rounded-r-md' : 'rounded-r-none',
    !state.first && '-ml-px',
  );
}
