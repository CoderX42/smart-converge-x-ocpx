import { generateRandomBusinessCode } from '../components/random-code';

export function generateRandomMediaCode(randomValues?: Uint8Array) {
  return generateRandomBusinessCode('media', randomValues);
}
