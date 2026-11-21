import type {Positions} from './graphLayout';
import {MOTION} from './motion';

export const ARRANGE_MOTION = MOTION.arrange;

export function ease(t: number) {
    return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}

export function between(from: Positions, to: Positions, progress: number): Positions {
    const k = ease(Math.min(1, Math.max(0, progress)));
    return Object.fromEntries(Object.entries(to).map(([id, end]) => {
        const start = from[id] ?? end;
        return [id, {x: start.x + (end.x - start.x) * k, y: start.y + (end.y - start.y) * k}];
    }));
}

export function tween(from: Positions, to: Positions, duration: number, onFrame: (positions: Positions) => void, onDone: () => void) {
    if (duration <= 0) {
        onFrame(to);
        onDone();
        return () => undefined;
    }
    const start = performance.now();
    let frame = 0;
    const step = (now: number) => {
        const progress = (now - start) / duration;
        onFrame(between(from, to, progress));
        if (progress < 1) frame = requestAnimationFrame(step);
        else onDone();
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
}
