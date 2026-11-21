import {describe, expect, it} from 'vitest';
import type {Lineage} from '../api/types';
import {focusOn} from './lineageFocus';

const table = (id: string, ...names: string[]) => ({id, layer: id, label: id, columns: names.map((name) => ({name, role: 'column'}))});
const edge = (from: string, to: string) => {
    const [fromTable, fromColumn] = from.split('|');
    const [toTable, toColumn] = to.split('|');
    return {fromTable, fromColumn, toTable, toColumn};
};

const LINEAGE: Lineage = {
    tables: [table('bronze', 'place', 'mag', 'raw'), table('silver', 'region', 'magnitude'), table('gold', 'region', 'events'),
        table('other', 'x')],
    edges: [edge('bronze|place', 'silver|region'), edge('bronze|mag', 'silver|magnitude'), edge('silver|region', 'gold|region'),
        edge('gold|region', 'other|x')],
    warnings: [],
};

describe('focusOn', () => {
    it('keeps the focused table whole and its neighbours to the linked columns', () => {
        const {lineage} = focusOn(LINEAGE, 'silver');

        expect(lineage.tables.map((entry) => [entry.id, entry.columns.map((column) => column.name)])).toEqual([
            ['bronze', ['place', 'mag']], ['silver', ['region', 'magnitude']], ['gold', ['region']],
        ]);
    });

    it('counts the columns it hides per table', () => {
        expect(focusOn(LINEAGE, 'silver').hidden).toEqual({bronze: 1, gold: 1});
    });

    it('drops edges that do not touch the focused table', () => {
        expect(focusOn(LINEAGE, 'silver').lineage.edges).toHaveLength(3);
    });
});
