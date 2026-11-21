import {describe, expect, it} from 'vitest';
import {DESTINATIONS, pageName} from './navigation';

describe('pageName', () => {
    it('names a page by the destination its first segment belongs to', () => {
        expect(pageName('/admin')).toBe('Settings');
        expect(pageName('/sql')).toBe('SQL editor');
        expect(pageName('/runs/1f5022d6')).toBe('Runs');
    });

    it('names pages that are only reached by search', () => {
        expect(pageName('/storage')).toBe('Storage');
    });

    it('falls back to the segment itself', () => {
        expect(pageName('/elsewhere/x')).toBe('elsewhere');
    });
});

describe('DESTINATIONS', () => {
    it('lists each page once', () => {
        const paths = DESTINATIONS.map((destination) => destination.to);
        expect(new Set(paths).size).toBe(paths.length);
    });
});
