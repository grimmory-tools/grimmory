import {Directive, TemplateRef, inject, input} from '@angular/core';

export interface BrowseGridItemContext<T> {
  $implicit: T;
  index: number;
}

@Directive({
  selector: 'ng-template[appBrowseGridItemOf]',
})
export class BrowseGridItemDef<T> {
  readonly templateRef = inject<TemplateRef<BrowseGridItemContext<T>>>(TemplateRef);
  readonly items = input.required<readonly T[]>({alias: 'appBrowseGridItemOf'});

  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- Angular uses this signature for template type checking.
  static ngTemplateContextGuard<T>(dir: BrowseGridItemDef<T>, ctx: unknown): ctx is BrowseGridItemContext<T> {
    return true;
  }
}

@Directive({
  selector: 'ng-template[appBrowseGridSkeleton]',
})
export class BrowseGridSkeletonDef {
  readonly templateRef = inject<TemplateRef<unknown>>(TemplateRef);
}

@Directive({
  selector: 'ng-template[appBrowseEmpty]',
})
export class BrowseEmptyDef {
  readonly templateRef = inject<TemplateRef<unknown>>(TemplateRef);
}
