import type { LocationQueryRaw } from 'vue-router';

export interface MediaContextItem {
  id: string;
}

export function resolveMediaId(
  media: MediaContextItem[],
  queryMediaId: unknown,
): string {
  const requested = Array.isArray(queryMediaId)
    ? queryMediaId[0]
    : queryMediaId;
  if (
    typeof requested === 'string' &&
    media.some((item) => item.id === requested)
  ) {
    return requested;
  }
  return media.length === 1 ? media[0]?.id || '' : '';
}

export function withMediaContext(
  query: LocationQueryRaw,
  mediaId: string,
): LocationQueryRaw {
  const next = { ...query };
  if (mediaId) next.mediaId = mediaId;
  else delete next.mediaId;
  return next;
}
