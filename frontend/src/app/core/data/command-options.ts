import {
  mutationOptions,
  MutationKey,
  MutationScope,
  QueryClient,
} from '@tanstack/angular-query-experimental';

export type CommandOutcome<TData> =
  | {readonly status: 'success'; readonly data: TData}
  | {readonly status: 'failure'; readonly error: unknown};

export function reconcilingMutationOptions<TData, TVariables>({mutationKey, scope, mutationFn, reconcile}: {
  readonly mutationKey: MutationKey;
  readonly scope?: MutationScope;
  readonly mutationFn: (variables: TVariables) => Promise<TData>;
  readonly reconcile: (
    outcome: CommandOutcome<TData>,
    variables: TVariables,
    client: QueryClient,
  ) => Promise<void>;
}) {
  return mutationOptions({
    mutationKey,
    scope,
    mutationFn,
    onSuccess: (data, variables, _onMutateResult, {client}) =>
      reconcile({status: 'success', data}, variables, client),
    onError: (error, variables, _onMutateResult, {client}) =>
      reconcile({status: 'failure', error}, variables, client),
  });
}
