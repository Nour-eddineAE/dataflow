import {describe, expect, it} from 'vitest';
import {searchItems} from './search';

describe('searchItems', () => {
    it('ranks exact names ahead of prefixes, word matches, substrings and metadata', () => {
        const items = [
            {label: 'Pipelines', keywords: 'taxi'},
            {label: 'pretaxi'},
            {label: 'Daily taxi trips'},
            {label: 'taxi.current'},
            {label: 'taxi'},
        ];
        expect(searchItems(items, 'taxi').map((item) => item.label))
            .toEqual(['taxi', 'taxi.current', 'Daily taxi trips', 'pretaxi', 'Pipelines']);
    });

    it('matches terms across a column name, table description and keyword aliases', () => {
        const column = {label: 'tip_amount', description: 'taxi.current · Decimal', keywords: ['fare', 'payment']};
        expect(searchItems([column], 'TAXI tip payment')).toEqual([column]);
        expect(searchItems([column], 'taxi missing')).toEqual([]);
    });

    it('normalizes accents and whitespace while retaining punctuation in names', () => {
        const items = [{label: 'Café.transactions'}, {label: 'raw_cafe.transactions'}];
        expect(searchItems(items, '  CAFE.TRANSACTIONS  ')).toEqual(items);
        expect(searchItems([{label: 'Café daily trips'}], '\t cafe   trips\n')).toHaveLength(1);
    });

    it('preserves input order for equally ranked matches and returns original objects', () => {
        const items = [{label: 'taxi.bronze'}, {label: 'taxi.silver'}, {label: 'taxi.current'}];
        const result = searchItems(items, 'taxi');
        expect(result).toEqual(items);
        expect(result[0]).toBe(items[0]);
        expect(items.map((item) => item.label)).toEqual(['taxi.bronze', 'taxi.silver', 'taxi.current']);
    });

    it('uses the limit after ranking rather than truncating before an exact match', () => {
        const items = [{label: 'taxi.bronze'}, {label: 'taxi.current'}, {label: 'taxi'}];
        expect(searchItems(items, 'taxi', 1)).toEqual([items[2]]);
        expect(searchItems(items, 'taxi', 0)).toEqual([]);
    });

    it('keeps the suggested order for an empty query', () => {
        const items = [{label: 'Recent table'}, {label: 'Workspace'}, {label: 'Catalog'}];
        expect(searchItems(items, ' \n ', 2)).toEqual(items.slice(0, 2));
    });

    it('supports optional labels and string keyword aliases', () => {
        const items = [{description: 'Run details', keywords: 'execution history'}, {label: 'Run history'}];
        expect(searchItems(items, 'history')).toEqual([items[1], items[0]]);
    });
});
