import {cn} from '../../ui/cn';

export const CARD_META_PADDING_TOP = 8;
export const CARD_META_TITLE_HEIGHT = 17;
export const CARD_META_LINE_HEIGHT = 15;

export const CARD_META_TITLE_CLASS = 'min-h-[17px] truncate text-[13px]/[17px] font-[550] text-text';
export const CARD_META_LINE_CLASS = 'min-h-[15px] truncate text-xs/[15px] text-text-secondary';

export const CARD_OVERLAY_REVEAL_CLASS =
  'pointer-events-none translate-y-1 opacity-0 transition-[opacity,translate] duration-150 group-hover/card:pointer-events-auto group-hover/card:translate-y-0 group-hover/card:opacity-100 group-has-[:focus-visible]/card:pointer-events-auto group-has-[:focus-visible]/card:translate-y-0 group-has-[:focus-visible]/card:opacity-100 pointer-fine:group-data-[active=true]/card:pointer-events-auto pointer-fine:group-data-[active=true]/card:translate-y-0 pointer-fine:group-data-[active=true]/card:opacity-100 motion-reduce:transition-none';
export const CARD_OVERLAY_BUTTON_CLASS =
  'inline-flex items-center justify-center gap-1.5 rounded-md bg-black text-xs font-[550] text-white/90 ring-1 ring-inset ring-white/20 hover:text-white active:translate-y-px pointer-coarse:active:translate-y-0 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';

export function cardArtworkClass(selected: boolean): string {
  return cn(
    'relative w-full transition-[box-shadow,scale] duration-100 ease-out motion-reduce:transition-none',
    'shadow-card group-hover/card:shadow-card-hover group-data-[active=true]/card:shadow-card-hover',
    selected && 'scale-[0.93] outline-2 outline-offset-2 outline-primary',
  );
}
