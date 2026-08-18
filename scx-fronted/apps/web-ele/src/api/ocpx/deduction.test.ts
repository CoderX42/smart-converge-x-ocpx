import { describe, expect, it } from 'vitest';

import {
  deductionPercentageToRatio,
  deductionRatioToPercentage,
} from './deduction-ratio';

describe('media callback deduction percentage conversion', () => {
  it('submits 80 percent as ratio 0.8', () => {
    expect(deductionPercentageToRatio(80)).toBe(0.8);
  });

  it('renders ratio 0.8 as 80 percent', () => {
    expect(deductionRatioToPercentage(0.8)).toBe(80);
  });

  it('keeps the zero and full-delivery boundaries exact', () => {
    expect(deductionPercentageToRatio(0)).toBe(0);
    expect(deductionPercentageToRatio(100)).toBe(1);
  });
});
