export const MOTION = {
    instant: 80,
    fast: 140,
    base: 200,
    slow: 280,
    arrange: 460,
} as const;

export const EASE = 'cubic-bezier(0.2, 0, 0, 1)';

export const FADE = 'opacity var(--df-motion-fast) var(--df-ease)';
