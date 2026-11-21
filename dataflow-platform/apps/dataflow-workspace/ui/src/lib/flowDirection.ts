import type {Positions} from './graphLayout';

export type Direction = 'LR' | 'TB';
export type Size = { width: number; height: number };

const PREFER_ACROSS = 1.15;

export function extent(positions: Positions, node: Size): Size {
    const points = Object.values(positions);
    return {
        width: Math.max(0, ...points.map((point) => point.x)) + node.width,
        height: Math.max(0, ...points.map((point) => point.y)) + node.height,
    };
}

export const fitZoom = (content: Size, canvas: Size) => Math.min(canvas.width / content.width, canvas.height / content.height);

export function readableDirection(across: Size, down: Size, canvas: Size): Direction {
    return fitZoom(down, canvas) > fitZoom(across, canvas) * PREFER_ACROSS ? 'TB' : 'LR';
}
