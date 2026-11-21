import {describe, expect, it} from 'vitest';
import {writeSpec} from './specYaml';

describe('writeSpec', () => {
    it('writes the house style: canonical key order, a blank line around each block, aligned rows', () => {
        const {text} = writeSpec({
            silver: {
                columns: [
                    {name: 'eventId', type: 'string', required: true, from: 'id'},
                    {name: 'updatedAt', type: 'timestamp', from: 'to_timestamp(updated)'},
                ],
            },
            schedule: '0 */6 * * *',
            version: 3,
            dataset: 'earthquakes',
        });

        expect(text).toBe([
            'dataset: earthquakes',
            'version: 3',
            'schedule: "0 */6 * * *"',
            '',
            'silver:',
            '  columns:',
            '    - { name: eventId,   type: string,    required: true, from: id }',
            '    - { name: updatedAt, type: timestamp,                 from: "to_timestamp(updated)" }',
            '',
        ].join('\n'));
    });

    it('writes multi-line SQL as a literal block inside a list item', () => {
        const {text} = writeSpec({gold: [{name: 'regions', sql: 'SELECT region\nFROM current\n'}]});

        expect(text).toBe('gold:\n  - name: regions\n    sql: |\n      SELECT region\n      FROM current\n');
    });

    it('quotes what YAML would read as another type, and keeps small maps on one line', () => {
        const {text} = writeSpec({source: {format: 'csv', options: {header: 'true', sep: ';'}}});

        expect(text).toBe('source:\n  format: csv\n  options: { header: "true", sep: ";" }\n');
    });

    it('drops empty values and records the spec path of every line', () => {
        const written = writeSpec({dataset: 'x', owner: '', serve: [{from: 'current', table: 'x', mode: 'view'}]});

        expect(written.text).not.toContain('owner');
        expect(written.paths).toEqual(['dataset', 'serve', 'serve', 'serve.0']);
    });

    it('keeps a step whose settings are still empty, and orders gold keys', () => {
        const {text} = writeSpec({
            dataset: 'x',
            inputs: {trips: 'taxi_trips.current'},
            gold: [{steps: [{filter: 'a > 1'}, {aggregate: {by: [], measures: {}}}], from: 'trips', name: 'daily'}],
        });

        expect(text).toBe('dataset: x\n\ninputs:\n  trips: taxi_trips.current\n\ngold:\n  - name: daily\n    from: trips\n'
            + '    steps:\n      - filter: a > 1\n      - { aggregate: { by: [], measures: {} } }\n');
    });
});
