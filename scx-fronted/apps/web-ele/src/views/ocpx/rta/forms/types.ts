import type { RtaFixTarget } from '#/api/ocpx/rta';

export type RtaOpenTarget = {
  id: string;
  selectId?: string;
  target: Exclude<RtaFixTarget, 'binding'>;
};

export function openTargetOf(
  target: RtaFixTarget | undefined,
  route: { advertiserId: string; mediaId: string; upstreamId?: string },
): RtaOpenTarget | undefined {
  if (target === 'media') return { id: route.mediaId, target: 'media' };
  if (target === 'upstream')
    return {
      id: route.advertiserId,
      selectId: route.upstreamId,
      target: 'upstream',
    };
  return undefined;
}

export function openPermission(target: RtaOpenTarget) {
  return target.target === 'media'
    ? 'ocpx:rta:media:view'
    : 'ocpx:rta:upstreams:view';
}
