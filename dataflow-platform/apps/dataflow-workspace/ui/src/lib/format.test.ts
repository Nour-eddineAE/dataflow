import {describe, expect, it} from 'vitest';
import {columnText, describeCron, formatAxisTime, formatBytes, formatClock, formatDuration, formatNumber, formatTime, nounFor, parseTime, plural} from './format';

describe('format', () => {
    it('writes durations the way people read them', () => {
        expect(formatDuration(850)).toBe('850 ms');
        expect(formatDuration(3400)).toBe('3.4 s');
        expect(formatDuration(38_000)).toBe('38 s');
        expect(formatDuration(186_000)).toBe('3m 6s');
        expect(formatDuration(119_600)).toBe('2m 0s');
        expect(formatDuration(59_700)).toBe('1m 0s');
        expect(formatDuration(undefined)).toBe('—');
    });

    it('writes times on a 24-hour clock', () => {
        const afternoon = new Date(2026, 10, 6, 13, 5, 9);
        expect(formatClock(afternoon.getTime())).toBe('13:05:09');
        expect(formatTime(afternoon.toISOString())).toMatch(/13:05$/);
        expect(formatAxisTime(afternoon.toISOString())).toMatch(/13:05$/);
    });

    it('writes sizes in binary units', () => {
        expect(formatBytes(512)).toBe('512 B');
        expect(formatBytes(3_460_000)).toBe('3.3 MB');
        expect(formatBytes(null)).toBe('—');
    });

    it('describes the schedules the specs use', () => {
        expect(describeCron('0 */6 * * *')).toBe('Every 6 h');
        expect(describeCron('30 6 * * *')).toBe('Daily at 06:30 UTC');
        expect(describeCron(undefined)).toBe('Manual');
        expect(describeCron('0 6 * * 1')).toBe('0 6 * * 1');
    });

    it('reads ClickHouse timestamps as UTC', () => {
        expect(parseTime('2026-10-25 00:00:30.544')?.toISOString()).toBe('2026-10-25T00:00:30.544Z');
        expect(parseTime('not a time')).toBeUndefined();
    });

    it('groups thousands', () => {
        expect(formatNumber(80015)).toBe('80,015');
    });
});

describe('plural', () => {
    it('says one thing without an s', () => {
        expect(plural(1, 'draft')).toBe('1 draft');
        expect(plural(2, 'draft')).toBe('2 drafts');
        expect(plural(0, 'draft')).toBe('0 drafts');
    });

    it('gives the noun alone when the count is written elsewhere', () => {
        expect(nounFor(1, 'row')).toBe('row');
        expect(nounFor(1_204, 'row')).toBe('rows');
    });

    it('prints floating cells with at most six fraction digits', () => {
        expect(columnText('8.938076479018297E10', 'double')).toBe('89380764790.18297');
        expect(columnText(0.1234567891, 'Float64')).toBe('0.123457');
        expect(columnText('NaN', 'double')).toBe('NaN');
        expect(columnText(null, 'double')).toBe('null');
        expect(columnText('12.3456789', 'decimal(10,7)')).toBe('12.3456789');
    });
});
