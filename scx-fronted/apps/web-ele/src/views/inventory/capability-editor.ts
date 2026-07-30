export type TargetingEntry = { key: string; value: string };

export type CapabilityEditorState = {
  advanced: boolean;
  billingModes: string[];
  creativeFormats: string[];
  parseError?: string;
  raw: {
    billingModes: string;
    creativeFormats: string;
    targetEvents: string;
    targetingFields: string;
  };
  targetEvents: string[];
  targetingFields: TargetingEntry[];
};

function parseArray(value?: string): string[] {
  if (!value) return [];
  const parsed = JSON.parse(value);
  if (!Array.isArray(parsed) || parsed.some((item) => typeof item !== 'string'))
    throw new Error('必须是字符串数组');
  return parsed;
}

function parseObject(value?: string): TargetingEntry[] {
  if (!value) return [];
  const parsed = JSON.parse(value);
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object')
    throw new Error('必须是键值对象');
  return Object.entries(parsed).map(([key, item]) => ({
    key,
    value: typeof item === 'string' ? item : JSON.stringify(item, undefined, 0),
  }));
}

export function parseCapability(input: {
  billingModes?: string;
  creativeFormats?: string;
  targetEvents?: string;
  targetingFields?: string;
}): CapabilityEditorState {
  const raw = {
    billingModes: input.billingModes || '[]',
    creativeFormats: input.creativeFormats || '[]',
    targetEvents: input.targetEvents || '[]',
    targetingFields: input.targetingFields || '{}',
  };
  try {
    return {
      advanced: false,
      billingModes: parseArray(input.billingModes),
      creativeFormats: parseArray(input.creativeFormats),
      raw,
      targetEvents: parseArray(input.targetEvents),
      targetingFields: parseObject(input.targetingFields),
    };
  } catch (error) {
    return {
      advanced: true,
      billingModes: [],
      creativeFormats: [],
      parseError: error instanceof Error ? error.message : '历史 JSON 无法解析',
      raw,
      targetEvents: [],
      targetingFields: [],
    };
  }
}

export function serializeCapability(state: CapabilityEditorState) {
  if (state.advanced) {
    for (const value of Object.values(state.raw)) JSON.parse(value);
    return { ...state.raw };
  }
  const targeting = Object.fromEntries(
    state.targetingFields
      .filter((item) => item.key.trim())
      .map((item) => [item.key.trim(), item.value]),
  );
  return {
    billingModes: JSON.stringify(state.billingModes),
    creativeFormats: JSON.stringify(state.creativeFormats),
    targetEvents: JSON.stringify(state.targetEvents),
    targetingFields: JSON.stringify(targeting),
  };
}
