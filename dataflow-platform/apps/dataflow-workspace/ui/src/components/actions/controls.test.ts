import {describe, expect, it} from 'vitest';

const SOURCES = import.meta.glob(['../../**/*.{ts,tsx}', '!../../**/*.test.{ts,tsx}', '!../../components/actions/**'],
    {query: '?raw', import: 'default', eager: true}) as Record<string, string>;
const MANTINE_IMPORT = /import\s*\{([^}]*)\}\s*from\s*'@mantine\/core'/g;
const RAW = new Set(['Button', 'ActionIcon']);

describe('controls', () => {
    it('buttons come from components/actions, never raw Mantine buttons', () => {
        const offenders = Object.entries(SOURCES)
            .filter(([, text]) => [...text.matchAll(MANTINE_IMPORT)]
                .some((match) => match[1].split(',').map((name) => name.trim().split(' ')[0]).some((name) => RAW.has(name))))
            .map(([path]) => path.replace('../../', ''));

        expect(Object.keys(SOURCES).length).toBeGreaterThan(50);
        expect(offenders).toEqual([]);
    });
});
