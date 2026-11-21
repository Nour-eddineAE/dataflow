import {describe, expect, it} from 'vitest';
import {
    addBlock,
    addInputTable,
    canConnect,
    catalogTable,
    connect,
    designGraph,
    disconnect,
    duplicateNode,
    goldReads,
    moveStep,
    nodeOfPath,
    nodeOfProblem,
    removeNode,
    renameGold,
    throughStep,
    renameInput,
    renameReference,
    type Spec,
} from './specModel';

const SPEC: Spec = {
    dataset: 'quakes',
    source: {url: 'https://example.org/feed/all_month.csv', format: 'csv'},
    silver: {
        columns: [{name: 'eventId', type: 'string', required: true, from: 'id'}],
        steps: [{name: 'energy', transformation: 'earthquake_energy@1', inputs: {zones: 'reference.zones'}}],
        expectations: [{name: 'has_id', check: 'eventId IS NOT NULL', 'on-failure': 'drop'}],
    },
    history: {keys: ['eventId']},
    references: [{name: 'zones', url: 'https://example.org/zones.csv', format: 'csv'}],
    gold: [
        {name: 'by_zone', sql: 'SELECT * FROM current JOIN zones USING (zone)'},
        {name: 'energy', transformation: 'region_energy@1', inputs: {events: 'current'}},
    ],
    serve: [
        {from: 'current', table: 'quakes', mode: 'view'},
        {from: 'gold.by_zone', table: 'quakes_by_zone', mode: 'view'},
    ],
};

const node = (spec: Spec, id: string) => designGraph(spec).nodes.find((candidate) => candidate.id === id)!;
const edges = (spec: Spec) => designGraph(spec).edges.map((edge) => `${edge.from}>${edge.to}`);

describe('designGraph', () => {
    it('draws one edge when several transformation inputs read the same source', () => {
        const spec: Spec = {
            ...SPEC,
            silver: {...SPEC.silver, steps: [{name: 'energy', transformation: 'energy@1',
                inputs: {primary: 'reference.zones', secondary: 'reference.zones'}}]},
            gold: [{name: 'energy', transformation: 'energy@1', inputs: {primary: 'current', secondary: 'current'}}],
        };
        const graph = designGraph(spec);

        expect(graph.edges.filter((edge) => edge.from === 'reference:zones' && edge.to === 'step:energy')).toHaveLength(1);
        expect(graph.edges.filter((edge) => edge.from === 'history' && edge.to === 'gold:energy')).toHaveLength(1);
        expect(new Set(graph.edges.map((edge) => `${edge.from}->${edge.to}`)).size).toBe(graph.edges.length);
    });

    it('draws the spine with steps between silver and history, and wires references, gold and serving', () => {
        expect(edges(SPEC)).toEqual([
            'source>bronze',
            'bronze>silver',
            'silver>step:energy',
            'step:energy>history',
            'reference:zones>step:energy',
            'history>gold:by_zone',
            'reference:zones>gold:by_zone',
            'history>gold:energy',
            'history>serve:quakes',
            'gold:by_zone>serve:quakes_by_zone',
        ]);
        expect(node(SPEC, 'source').detail).toBe('csv · all_month.csv');
        expect(node(SPEC, 'silver').detail).toBe('1 column · 1 rule');
    });
});

describe('editing on the canvas', () => {
    it('adds blocks with free names and sensible defaults', () => {
        const {spec, id} = addBlock(SPEC, 'serve-copy');

        expect(id).toBe('serve:quakes_2');
        expect(spec.serve?.at(-1)).toEqual({from: 'current', table: 'quakes_2', mode: 'copy', 'order-by': 'eventId'});
    });

    it('wires serving and transformation inputs by connecting nodes, never a SQL table', () => {
        expect(connect(SPEC, node(SPEC, 'gold:energy'), node(SPEC, 'serve:quakes')).serve?.[0].from).toBe('gold.energy');
        expect(connect(SPEC, node(SPEC, 'reference:zones'), node(SPEC, 'gold:energy')).gold?.[1].inputs)
            .toEqual({events: 'current', zones: 'reference.zones'});
        expect(connect(SPEC, node(SPEC, 'reference:zones'), node(SPEC, 'gold:by_zone'))).toBe(SPEC);
    });

    it('renames carry over to what reads the renamed table', () => {
        expect(renameGold(SPEC, 0, 'zones_summary').serve?.[1].from).toBe('gold.zones_summary');
        expect(renameReference(SPEC, 0, 'areas').silver?.steps?.[0].inputs).toEqual({zones: 'reference.areas'});
    });

    it('removing a gold table points its serving back at current', () => {
        const spec = removeNode(SPEC, node(SPEC, 'gold:by_zone'));

        expect(spec.gold?.map((table) => table.name)).toEqual(['energy']);
        expect(spec.serve?.[1].from).toBe('current');
    });
});

describe('problems on the canvas', () => {
    it('finds the node from the spec path of the line', () => {
        expect(nodeOfPath('silver.steps.0.transformation', SPEC)).toBe('step:energy');
        expect(nodeOfPath('silver.columns.0', SPEC)).toBe('silver');
        expect(nodeOfPath('serve.1', SPEC)).toBe('serve:quakes_by_zone');
        expect(nodeOfPath('schedule', SPEC)).toBe('pipeline');
    });

    it('falls back on the names a message starts with', () => {
        expect(nodeOfProblem(SPEC, "gold name 'by_zone' must match [a-z][a-z0-9_]{0,62}")).toBe('gold:by_zone');
        expect(nodeOfProblem(SPEC, 'serve quakes_by_zone reads gold.nothing, which is not declared')).toBe('serve:quakes_by_zone');
        expect(nodeOfProblem(SPEC, 'schedule is not a cron expression')).toBe('pipeline');
    });
});

const DERIVED: Spec = {
    dataset: 'zone_hours',
    inputs: {trips: 'taxi_trips.current', zones: 'taxi_trips.reference.taxi_zones'},
    gold: [
        {name: 'zoned', from: 'trips', materialize: false, steps: [{join: {with: 'zones', type: 'left', on: {pickupLocationId: 'LocationID'}}}]},
        {name: 'hours', from: 'zoned', steps: [{aggregate: {by: ['Borough'], measures: {trips: 'count(*)'}}}]},
        {name: 'top', sql: 'SELECT * FROM hours ORDER BY trips DESC LIMIT 10'},
    ],
    serve: [{from: 'gold.hours', table: 'zone_hours'}],
};

describe('pipelines that read other pipelines', () => {
    it('draws inputs and tables without the ingestion spine', () => {
        expect(edges(DERIVED)).toEqual([
            'input:trips>gold:zoned',
            'input:zones>gold:zoned',
            'gold:zoned>gold:hours',
            'gold:hours>gold:top',
            'gold:hours>serve:zone_hours',
        ]);
        expect(node(DERIVED, 'gold:zoned')).toMatchObject({stored: false, detail: 'join'});
        expect(node(DERIVED, 'gold:hours').detail).toBe('group');
        expect(designGraph(DERIVED).nodes.map((candidate) => candidate.id)).not.toContain('history');
    });

    it('connecting a second table to a transform adds a join to fill in; disconnecting removes it', () => {
        const added = addBlock(DERIVED, 'input');
        const spec = {...added.spec, inputs: {...added.spec.inputs, input: 'earthquakes.gold.regions'}};
        const joined = connect(spec, node(spec, 'input:input'), node(spec, 'gold:hours'));

        expect(joined.gold?.[1].steps?.at(-1)).toEqual({join: {with: 'input', type: 'left', on: {}}});
        expect(disconnect(joined, node(joined, 'input:input'), node(joined, 'gold:hours')).gold?.[1].steps)
            .toEqual(DERIVED.gold?.[1].steps);
    });

    it('never connects a table to one that already reads it', () => {
        expect(canConnect(DERIVED, node(DERIVED, 'gold:hours'), node(DERIVED, 'gold:zoned'))).toBe(false);
        expect(canConnect(DERIVED, node(DERIVED, 'gold:zoned'), node(DERIVED, 'serve:zone_hours'))).toBe(false);
    });

    it('renaming an input rewires steps and SQL that read it', () => {
        const spec = renameInput({...DERIVED, gold: [...DERIVED.gold!, {name: 'raw', sql: 'SELECT * FROM trips t JOIN zones z ON 1=1'}]},
            'trips', 'rides');

        expect(Object.keys(spec.inputs!)).toEqual(['rides', 'zones']);
        expect(spec.gold?.[0].from).toBe('rides');
        expect(spec.gold?.[3].sql).toBe('SELECT * FROM rides t JOIN zones z ON 1=1');
    });

    it('a block added while a table is selected reads that table', () => {
        expect(addBlock(DERIVED, 'gold-steps', node(DERIVED, 'input:zones')).spec.gold?.at(-1)?.from).toBe('zones');
        expect(addBlock(DERIVED, 'gold-sql', node(DERIVED, 'gold:hours')).spec.gold?.at(-1)?.sql).toBe('SELECT *\nFROM hours\n');
        expect(addBlock(DERIVED, 'serve-view', node(DERIVED, 'gold:hours')).spec.serve?.at(-1)?.from).toBe('gold.hours');
        expect(addBlock(DERIVED, 'serve-view', node(DERIVED, 'gold:zoned')).spec.serve?.at(-1)?.from).toBe('gold.hours');
    });

    it('a new transform starts from the first input when there is no current table', () => {
        const {spec, id} = addBlock(DERIVED, 'gold-steps');

        expect(id).toBe('gold:summary');
        expect(spec.gold?.at(-1)).toEqual({name: 'summary', from: 'trips', steps: []});
        expect(goldReads({name: 'x', sql: 'select a from Hours h left join zones on true'})).toEqual(['hours', 'zones']);
    });
});

describe('step order', () => {
    it('moves a step earlier or later, never past the ends', () => {
        const spec: Spec = {silver: {steps: [{name: 'a', transformation: 'x@1'}, {name: 'b', transformation: 'y@1'}]}};

        expect(moveStep(spec, 1, -1).silver?.steps?.map((step) => step.name)).toEqual(['b', 'a']);
        expect(moveStep(spec, 1, 1)).toBe(spec);
    });
});

describe('node actions', () => {
    it('duplicates a table or a reference next to it under a free name', () => {
        const table = duplicateNode(SPEC, node(SPEC, 'gold:by_zone'))!;
        expect(table.id).toBe('gold:by_zone_copy');
        expect(table.spec.gold?.map((gold) => gold.name)).toEqual(['by_zone', 'by_zone_copy', 'energy']);
        expect(table.spec.gold?.[1].sql).toBe(SPEC.gold?.[0].sql);

        const again = duplicateNode(table.spec, node(table.spec, 'gold:by_zone'))!;
        expect(again.id).toBe('gold:by_zone_copy_2');

        expect(duplicateNode(SPEC, node(SPEC, 'reference:zones'))?.spec.references?.map((reference) => reference.name))
            .toEqual(['zones', 'zones_copy']);
        expect(duplicateNode(SPEC, node(SPEC, 'silver'))).toBeUndefined();
    });

    it('names the catalog table behind a node, if it is stored', () => {
        expect(catalogTable(SPEC, node(SPEC, 'history'))).toBe('current');
        expect(catalogTable(SPEC, node(SPEC, 'gold:by_zone'))).toBe('gold.by_zone');
        expect(catalogTable(SPEC, node(SPEC, 'serve:quakes_by_zone'))).toBe('serving.quakes_by_zone');
        expect(catalogTable(DERIVED, node(DERIVED, 'gold:zoned'))).toBeUndefined();
        expect(catalogTable(SPEC, node(SPEC, 'source'))).toBeUndefined();
    });
});

describe('rows after a step', () => {
    it('keeps the table up to that step and only the tables it reads', () => {
        const trimmed = throughStep(DERIVED, 'hours', 0);

        expect(trimmed.gold?.map((gold) => gold.name)).toEqual(['zoned', 'hours']);
        expect(trimmed.gold?.[1].steps).toHaveLength(1);
        expect(trimmed.serve).toEqual([]);
    });

    it('drops tables only later steps read', () => {
        const spec: Spec = {
            ...DERIVED,
            gold: [...DERIVED.gold!, {name: 'mix', from: 'trips', steps: [{filter: 'fare > 0'}, {union: ['zoned']}]}],
        };

        expect(throughStep(spec, 'mix', 0).gold?.map((gold) => gold.name)).toEqual(['mix']);
        expect(throughStep(spec, 'mix', 1).gold?.map((gold) => gold.name)).toEqual(['zoned', 'mix']);
    });

    it('reads a dropped catalog table as a new input', () => {
        const {spec, id} = addInputTable(DERIVED, 'taxi_trips.gold.daily');

        expect(spec.inputs?.taxi_trips_daily).toBe('taxi_trips.gold.daily');
        expect(id).toBe('input:taxi_trips_daily');
    });

    it('never reuses a name when the same table is dropped twice', () => {
        const once = addInputTable(DERIVED, 'taxi_trips.current');
        const twice = addInputTable(once.spec, 'taxi_trips.current');

        expect(Object.keys(twice.spec.inputs ?? {})).toContain('taxi_trips_current_2');
        expect(twice.id).toBe('input:taxi_trips_current_2');
    });
});
