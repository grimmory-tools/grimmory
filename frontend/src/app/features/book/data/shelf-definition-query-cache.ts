import {QueryClient} from '@tanstack/angular-query-experimental';

import {shelfDefinitionQueryKeys} from './shelf-definition-query-keys';

export function invalidateShelfDefinitions(client: QueryClient): Promise<void> {
  return client.invalidateQueries({
    queryKey: shelfDefinitionQueryKeys.all(),
  });
}
