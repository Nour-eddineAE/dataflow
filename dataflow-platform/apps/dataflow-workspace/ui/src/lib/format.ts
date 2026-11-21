import {isFloatingType} from './types';
import {HOURS_PER_DAY, MINUTES_PER_HOUR, SECOND, SECONDS_PER_MINUTE} from './timing';

const BYTES_PER_UNIT = 1024;
const ONE_DECIMAL_BELOW = 10;
const JUST_NOW_SECONDS = 45;
const DAYS_BEFORE_DATE = 30;

const numbers = new Intl.NumberFormat('en-US');
const compactNumbers = new Intl.NumberFormat('en-US', {notation: 'compact', maximumFractionDigits: 1});

export const formatNumber = (value?: number | null) => (value === undefined || value === null ? '—' : numbers.format(value));

export const formatCompact = (value?: number | null) => (value === undefined || value === null ? '—' : compactNumbers.format(value));

export function formatBytes(value?: number | null) {
    if (value === undefined || value === null) return '—';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = value;
    let unit = 0;
    while (size >= BYTES_PER_UNIT && unit < units.length - 1) {
        size /= BYTES_PER_UNIT;
        unit++;
    }
    return `${size < ONE_DECIMAL_BELOW && unit > 0 ? size.toFixed(1) : Math.round(size)} ${units[unit]}`;
}

export function formatDuration(ms?: number | null) {
    if (ms === undefined || ms === null) return '—';
    if (ms < SECOND) return `${ms} ms`;
    const seconds = ms / SECOND;
    if (seconds < ONE_DECIMAL_BELOW) return `${seconds.toFixed(1)} s`;
    const whole = Math.round(seconds);
    if (whole < SECONDS_PER_MINUTE) return `${whole} s`;
    const minutes = Math.floor(whole / SECONDS_PER_MINUTE);
    const rest = whole % SECONDS_PER_MINUTE;
    if (minutes < MINUTES_PER_HOUR) return `${minutes}m ${rest}s`;
    return `${Math.floor(minutes / MINUTES_PER_HOUR)}h ${minutes % MINUTES_PER_HOUR}m`;
}

export const nounFor = (count: number, noun: string) => `${noun}${count === 1 ? '' : 's'}`;

export const plural = (count: number, noun: string) => `${count} ${nounFor(count, noun)}`;

export const formatPercent = (percent: number) => `${percent.toFixed(percent < ONE_DECIMAL_BELOW ? 1 : 0)}%`;

export function parseTime(value?: string | null) {
    if (!value) return undefined;
    const iso = value.includes('T') ? value : value.replace(' ', 'T') + 'Z';
    const time = new Date(iso);
    return Number.isNaN(time.getTime()) ? undefined : time;
}

export function formatAgo(value?: string | null) {
    const time = parseTime(value);
    if (!time) return '—';
    const seconds = Math.round((Date.now() - time.getTime()) / SECOND);
    if (seconds < JUST_NOW_SECONDS) return 'just now';
    const minutes = Math.round(seconds / SECONDS_PER_MINUTE);
    if (minutes < MINUTES_PER_HOUR) return `${minutes} min ago`;
    const hours = Math.round(minutes / MINUTES_PER_HOUR);
    if (hours < HOURS_PER_DAY) return `${hours} h ago`;
    const days = Math.round(hours / HOURS_PER_DAY);
    return days < DAYS_BEFORE_DATE ? `${days} d ago` : time.toLocaleDateString();
}

const PADDED = '2-digit';
const dateTime = new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short', hourCycle: 'h23'});
const axisTime = new Intl.DateTimeFormat(undefined, {month: PADDED, day: PADDED, hour: PADDED, minute: PADDED, hourCycle: 'h23'});

export function formatTime(value?: string | null) {
    const time = parseTime(value);
    return time ? dateTime.format(time) : '—';
}

const clock = new Intl.DateTimeFormat(undefined, {hour: PADDED, minute: PADDED, second: PADDED, hourCycle: 'h23'});

export const formatClock = (epochMs: number) => clock.format(new Date(epochMs));

export function formatAxisTime(value?: string | null) {
    const time = parseTime(value);
    return time ? axisTime.format(time) : '—';
}

export function describeTrigger(cron: string | undefined, readsInputs: boolean) {
    return !cron && readsInputs ? 'When its inputs land' : describeCron(cron);
}

export function describeCron(cron?: string) {
    if (!cron) return 'Manual';
    const [minute, hour, day, month, weekday] = cron.trim().split(/\s+/);
    if (day === '*' && month === '*' && weekday === '*') {
        if (hour.startsWith('*/')) return `Every ${hour.slice(2)} h`;
        if (hour === '*') return minute === '0' ? 'Hourly' : `Hourly at :${minute.padStart(2, '0')}`;
        if (/^\d+$/.test(hour) && /^\d+$/.test(minute)) return `Daily at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')} UTC`;
    }
    return cron;
}

export function cellText(value: unknown): string {
    if (value === null || value === undefined) return 'null';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
}

const FLOAT_FRACTION_DIGITS = 6;
const floats = new Intl.NumberFormat('en-US', {maximumFractionDigits: FLOAT_FRACTION_DIGITS, useGrouping: false});

export function columnText(value: unknown, type: string): string {
    if (!isFloatingType(type) || (typeof value !== 'number' && typeof value !== 'string') || value === '') return cellText(value);
    const number = Number(value);
    return Number.isFinite(number) ? floats.format(number) : cellText(value);
}

const SHORT_RUN_ID = 8;

export const shortRunId = (runId: string) => runId.slice(0, SHORT_RUN_ID);

const SHORT_COMMIT = 7;

export const shortCommit = (commit: string) => commit.slice(0, SHORT_COMMIT);
