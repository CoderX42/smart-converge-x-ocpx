export function deductionRatioToPercentage(ratio: number) {
  return Math.round(Number(ratio) * 10_000) / 100;
}

export function deductionPercentageToRatio(percentage: number) {
  return Math.round(Number(percentage) * 10_000) / 1_000_000;
}
