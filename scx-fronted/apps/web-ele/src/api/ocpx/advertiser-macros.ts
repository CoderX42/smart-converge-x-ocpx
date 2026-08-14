function queryKey(pair: string) {
  return (pair.split('=', 1)[0] || '').trim();
}

/** Mirrors the backend query assembly for display and copy previews. */
export function appendAdvertiserMacroParams(
  bidUrl: null | string | undefined,
  macroParams: null | string | undefined,
) {
  if (!bidUrl || !macroParams) return bidUrl || '';
  const query = macroParams.trim().replaceAll(/^[?&]+|&+$/g, '');
  if (!query || !query.includes('=')) return bidUrl;

  const fragmentIndex = bidUrl.indexOf('#');
  const base = fragmentIndex === -1 ? bidUrl : bidUrl.slice(0, fragmentIndex);
  const fragment = fragmentIndex === -1 ? '' : bidUrl.slice(fragmentIndex);
  const queryIndex = base.indexOf('?');
  const existingKeys = new Set(
    (queryIndex === -1 ? '' : base.slice(queryIndex + 1))
      .split('&')
      .map((pair) => queryKey(pair))
      .filter(Boolean),
  );
  const missingPairs = query
    .split('&')
    .map((pair) => pair.trim())
    .filter((pair) => {
      const key = queryKey(pair);
      if (!pair || !key || existingKeys.has(key)) return false;
      existingKeys.add(key);
      return true;
    });
  if (missingPairs.length === 0) return bidUrl;
  let separator = '?';
  if (/[?&]$/.test(base)) separator = '';
  else if (base.includes('?')) separator = '&';
  return `${base}${separator}${missingPairs.join('&')}${fragment}`;
}
