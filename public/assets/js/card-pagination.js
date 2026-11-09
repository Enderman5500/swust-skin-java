export const ROW_SIZES = [3, 5, 10, 20];
export const DEFAULT_ROWS = 5;

export function gridColumns(grid) {
  const tracks = getComputedStyle(grid).gridTemplateColumns;
  if (!tracks || tracks === 'none') return 1;
  return Math.max(1, tracks.split(' ').filter(Boolean).length);
}

export function pageSizeForRows(rows, grid) {
  return Math.max(1, Math.min(100, Number(rows) * gridColumns(grid)));
}
