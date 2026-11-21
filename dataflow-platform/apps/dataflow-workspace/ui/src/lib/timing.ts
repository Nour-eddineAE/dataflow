export const POLL = {
    running: 2_000,
    active: 3_000,
    detail: 10_000,
    list: 15_000,
    background: 30_000,
    slow: 60_000,
} as const;

export const FRESH = {
    moment: 5_000,
    list: 30_000,
    minute: 60_000,
    usage: 300_000,
} as const;

export const NOTICE = {
    brief: 6_000,
    error: 10_000,
} as const;

export const SECOND = 1_000;
export const SECONDS_PER_MINUTE = 60;
export const MINUTES_PER_HOUR = 60;
export const HOURS_PER_DAY = 24;
export const TOOLTIP_DELAY = 400;
export const TOOLTIP_DELAY_FAST = 200;
export const TYPING_PAUSE = 400;
export const CLOCK_TICK = 1_000;
