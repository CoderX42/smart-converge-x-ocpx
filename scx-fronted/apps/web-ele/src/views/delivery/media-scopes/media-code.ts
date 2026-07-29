const MEDIA_CODE_ALPHABET = 'abcdefghijklmnopqrstuvwxyz0123456789';
const RANDOM_PART_LENGTH = 12;

export function generateRandomMediaCode(randomValues?: Uint8Array) {
  const values =
    randomValues ??
    globalThis.crypto.getRandomValues(new Uint8Array(RANDOM_PART_LENGTH));
  if (values.length < RANDOM_PART_LENGTH) {
    throw new RangeError(
      `Expected at least ${RANDOM_PART_LENGTH} random bytes`,
    );
  }
  const randomPart = Array.from(values.slice(0, RANDOM_PART_LENGTH), (value) =>
    MEDIA_CODE_ALPHABET.charAt(value % MEDIA_CODE_ALPHABET.length),
  ).join('');
  return `media-${randomPart}`;
}
