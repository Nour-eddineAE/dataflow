import {describe, expect, it} from 'vitest';
import type {Spec} from './specModel';
import {writeSpec} from './specYaml';
import {warningLines} from './warningLines';

const SPEC: Spec = {
    dataset: 'quakes',
    source: {url: 'https://example.org/feed.csv', format: 'csv'},
    history: {keys: ['eventId']},
    gold: [
        {name: 'by_zone', sql: 'SELECT * FROM current'},
        {name: 'energy', transformation: 'region_energy@1', inputs: {events: 'current'}},
    ],
};
const TEXT = writeSpec(SPEC as Record<string, unknown>).text;
const lineOf = (start: string) => TEXT.split('\n').findIndex((line) => line.includes(start)) + 1;

describe('warningLines', () => {
    it('puts a warning about a gold table on the line where that table starts', () => {
        const [warning] = warningLines(TEXT, SPEC, ['gold energy is computed by region_energy@1; its columns are not traced']);
        expect(warning.line).toBe(lineOf('name: energy'));
    });

    it('leaves a warning without a line when the text is not in canonical form', () => {
        expect(warningLines(`# edited\n${TEXT}`, SPEC, ['gold energy is odd'])[0].line).toBeUndefined();
    });

    it('leaves a warning about the whole pipeline without a line', () => {
        expect(warningLines(TEXT, SPEC, ['schedule is unusual'])[0].line).toBeUndefined();
    });
});
