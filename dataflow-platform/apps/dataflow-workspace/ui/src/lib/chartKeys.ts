const PERCENTILE = 0.9;
const FEW = 10;

export function barScale(values: number[]): number {
    const sorted = values.filter((value) => value > 0).sort((a, b) => a - b);
    if (!sorted.length) return 1;
    if (sorted.length < FEW) return sorted[sorted.length - 1];
    return sorted[Math.ceil(sorted.length * PERCENTILE) - 1];
}

export type Spot = { row: number; col: number };

const STEPS: Record<string, Spot> = {
    ArrowLeft: {row: 0, col: -1},
    ArrowRight: {row: 0, col: 1},
    ArrowUp: {row: -1, col: 0},
    ArrowDown: {row: 1, col: 0},
};

export function nextBar(present: boolean[][], from: Spot, key: string): Spot | undefined {
    const rows = present.length;
    const cols = present[0]?.length ?? 0;
    if (key === 'Home' || key === 'End') {
        const order = [...present[from.row].keys()];
        const col = (key === 'Home' ? order : order.reverse()).find((index) => present[from.row][index]);
        return col === undefined ? undefined : {row: from.row, col};
    }
    const step = STEPS[key];
    if (!step) return undefined;
    for (let row = from.row + step.row, col = from.col + step.col; row >= 0 && row < rows && col >= 0 && col < cols;
         row += step.row, col += step.col) {
        if (present[row][col]) return {row, col};
    }
    return undefined;
}
