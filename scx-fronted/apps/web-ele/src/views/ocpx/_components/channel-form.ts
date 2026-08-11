const CHANNEL_FORM_FIELDS = [
  'id',
  'channelCode',
  'channelName',
  'adapterCode',
  'macroParams',
  'status',
] as const;

export function buildChannelSavePayload(form: Record<string, unknown>) {
  return Object.fromEntries(
    CHANNEL_FORM_FIELDS.filter((field) => form[field] !== undefined).map(
      (field) => [field, form[field]],
    ),
  );
}
