import { generateRandomBusinessCode } from '../delivery/components/random-code';

export function generateRandomPlacementCode(randomValues?: Uint8Array) {
  return generateRandomBusinessCode('placement', randomValues);
}
