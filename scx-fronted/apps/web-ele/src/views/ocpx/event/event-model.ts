import type {
  CanonicalEventDefinition,
  CanonicalEventMapping,
} from '#/api/ocpx';

export type EventPageState =
  'ERROR' | 'FILTER_EMPTY' | 'INITIAL_EMPTY' | 'NO_PERMISSION' | 'READY';

export function resolveEventPageState(input: {
  canView: boolean;
  error: boolean;
  filtered: boolean;
  total: number;
}): EventPageState {
  if (!input.canView) return 'NO_PERMISSION';
  if (input.error) return 'ERROR';
  if (input.total > 0) return 'READY';
  return input.filtered ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
}

export function statusNeedsReferenceConfirmation(
  row: CanonicalEventDefinition | CanonicalEventMapping,
  status: 'ARCHIVED' | 'DISABLED',
) {
  return (
    status !== row.status && row.references.statusChangeRequiresConfirmation
  );
}

export function compatibleDefinitions(
  definitions: CanonicalEventDefinition[],
  direction: CanonicalEventMapping['direction'],
) {
  return definitions.filter(
    (item) =>
      item.status === 'ACTIVE' &&
      (item.direction === direction || item.direction === 'BIDIRECTIONAL'),
  );
}

export function sanitizedEventEditorSnapshot(value: object) {
  return JSON.stringify(value, Object.keys(value).sort());
}
