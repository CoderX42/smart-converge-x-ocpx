const shanghaiDateFormatter = new Intl.DateTimeFormat('en-CA', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
});

export function shanghaiDate(daysOffset = 0): string {
  const value = new Date(Date.now() + daysOffset * 86_400_000);
  return shanghaiDateFormatter.format(value);
}
