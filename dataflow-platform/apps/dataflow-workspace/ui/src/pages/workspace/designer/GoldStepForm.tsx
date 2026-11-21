import {Group, SegmentedControl, Select, Stack, TagsInput, Text, TextInput, UnstyledButton} from '@mantine/core';
import {IconPlus, IconX} from '../../../icons';
import {IconAction} from '../../../components/actions/IconAction';
import {type Columns} from '../../../lib/goldColumns';
import {type Aggregate, type GoldStep, type Join, type JoinType, type Operation, unique} from '../../../lib/specModel';
import {KeyValueEditor} from './Editors';
import {ICON_SMALL} from '../../../lib/icons';

const COLUMN_PREVIEW = 8;

const JOIN_TYPES: { value: JoinType; label: string; hint: string }[] = [
    {value: 'left', label: 'Keep all rows', hint: 'Every row stays; matches add their columns'},
    {value: 'inner', label: 'Only matches', hint: 'Rows without a match are left out'},
    {value: 'full', label: 'Everything', hint: 'Rows of both tables, matched where possible'},
    {value: 'right', label: 'All of the other', hint: 'Every row of the other table stays'},
    {value: 'semi', label: 'Rows with a match', hint: 'Keeps this table’s columns only'},
    {value: 'anti', label: 'Rows without a match', hint: 'Keeps this table’s columns only'},
];

const FUNCTIONS = ['count', 'sum', 'avg', 'min', 'max', 'count distinct'];
const MEASURE = /^(count|sum|avg|min|max)\((distinct\s+)?(\*|[A-Za-z_][A-Za-z0-9_]*)\)$/i;
const CUSTOM = 'custom';

export function StepForm({step, operation, columns, tables, columnsOf, onChange}: {
    step: GoldStep;
    operation: Operation;
    columns: string[];
    tables: string[];
    columnsOf: Columns;
    onChange: (step: GoldStep) => void;
}) {
    switch (operation) {
        case 'join':
            return <JoinForm join={step.join!} columns={columns} tables={tables} columnsOf={columnsOf} onChange={(join) => onChange({join})}/>;
        case 'union':
            return <TagsInput label="Tables" data={tables} value={step.union!} placeholder="Pick tables"
                              description="Columns are matched by name; missing ones are left empty"
                              onChange={(union) => onChange({union})}/>;
        case 'filter':
            return <Expression label="Keep rows where" value={step.filter!} placeholder="magnitude >= 4 AND region IS NOT NULL"
                               columns={columns} onChange={(filter) => onChange({filter})}/>;
        case 'derive':
            return <KeyValueEditor values={step.derive} add="Add column" keyLabel="Column" valueLabel="Expression"
                                   onChange={(derive) => onChange({derive})}/>;
        case 'select':
            return <TagsInput label="Columns" data={columns} value={step.select!} placeholder="Pick or type an expression"
                              description="Write “expression AS name” to compute a column" onChange={(select) => onChange({select})}/>;
        case 'rename':
            return <KeyValueEditor values={step.rename} add="Rename a column" keyLabel="Column" valueLabel="New name"
                                   onChange={(rename) => onChange({rename})}/>;
        case 'drop':
            return <TagsInput label="Columns" data={columns} value={step.drop!} placeholder="Pick columns"
                              onChange={(drop) => onChange({drop})}/>;
        case 'aggregate':
            return <AggregateForm aggregate={step.aggregate!} columns={columns} onChange={(aggregate) => onChange({aggregate})}/>;
        case 'dedupe':
            return (
                <Stack gap="xs">
                    <TagsInput label="One row per" data={columns} value={step.dedupe!.keys ?? []} placeholder="Pick key columns"
                               onChange={(keys) => onChange({dedupe: {...step.dedupe, keys}})}/>
                    <Select label="Keep the row with the latest" data={columns} value={step.dedupe!['latest-by'] ?? null} clearable
                            placeholder="Any row" searchable
                            onChange={(latest) => onChange({dedupe: {...step.dedupe, 'latest-by': latest ?? undefined}})}/>
                </Stack>
            );
    }
}

function JoinForm({join, columns, tables, columnsOf, onChange}: {
    join: Join;
    columns: string[];
    tables: string[];
    columnsOf: Columns;
    onChange: (join: Join) => void;
}) {
    const right = join.with ? columnsOf(join.with) : [];
    const condition = join.condition !== undefined;
    const pairs = Object.entries(join.on ?? {});
    const setPairs = (next: [string, string][]) => onChange({...join, on: Object.fromEntries(next), condition: undefined});
    const kind = JOIN_TYPES.find((type) => type.value === (join.type ?? 'inner'));
    return (
        <Stack gap="xs">
            <Select label="With" data={tables} value={join.with || null} searchable allowDeselect={false} placeholder="Choose a table"
                    onChange={(table) => onChange({...join, with: table ?? ''})}/>
            <Select label="Rows to keep" allowDeselect={false} value={join.type ?? 'inner'} description={kind?.hint}
                    data={JOIN_TYPES.map(({value, label}) => ({value, label}))}
                    onChange={(type) => onChange({...join, type: (type ?? 'inner') as JoinType})}/>
            <SegmentedControl size="xs" fullWidth value={condition ? 'condition' : 'columns'}
                              data={[{value: 'columns', label: 'Matching columns'}, {value: 'condition', label: 'Condition'}]}
                              onChange={(mode) => onChange(mode === 'condition'
                                  ? {...join, on: undefined, condition: join.condition ?? ''}
                                  : {...join, condition: undefined, on: join.on ?? {}})}/>
            {condition ? (
                <Expression label="Match when" value={join.condition ?? ''} placeholder="left.zoneId = right.LocationID"
                            columns={[...columns.map((name) => `left.${name}`), ...right.map((name) => `right.${name}`)]}
                            onChange={(value) => onChange({...join, condition: value})}/>
            ) : (
                <Stack gap="snug">
                    {pairs.map(([leftColumn, rightColumn], index) => (
                        <Group key={index} gap="snug" wrap="nowrap" className="df-pair">
                            <Select size="xs" data={columns} value={leftColumn || null} searchable placeholder="This table"
                                    aria-label="Column of this table"
                                    onChange={(value) => setPairs(pairs.map((pair, at) => (at === index ? [value ?? '', pair[1]] : pair)))}/>
                            <Text size="xs" c="dimmed">=</Text>
                            <Select size="xs" data={right} value={rightColumn || null} searchable placeholder={join.with || 'Other'}
                                    aria-label="Column of the other table"
                                    onChange={(value) => setPairs(pairs.map((pair, at) => (at === index ? [pair[0], value ?? ''] : pair)))}/>
                            <IconAction label="Remove" icon={IconX} onClick={() => setPairs(pairs.filter((_, at) => at !== index))}/>
                        </Group>
                    ))}
                    <UnstyledButton className="df-rows-add" onClick={() => setPairs([...pairs, ['', '']])}>
                        <IconPlus {...ICON_SMALL}/>Match a column
                    </UnstyledButton>
                </Stack>
            )}
            {kind && !['semi', 'anti'].includes(kind.value) && (
                <TextInput label="Prefix for its columns" placeholder="none" classNames={{input: 'df-mono'}} value={join.prefix ?? ''}
                           description="Needed when both tables have a column with the same name"
                           onChange={(event) => onChange({...join, prefix: event.currentTarget.value || undefined})}/>
            )}
        </Stack>
    );
}

function AggregateForm({aggregate, columns, onChange}: { aggregate: Aggregate; columns: string[]; onChange: (aggregate: Aggregate) => void }) {
    const measures = Object.entries(aggregate.measures ?? {});
    const setMeasures = (next: [string, string][]) => onChange({...aggregate, measures: Object.fromEntries(next)});
    return (
        <Stack gap="xs">
            <TagsInput label="Group by" data={columns} value={aggregate.by ?? []} placeholder="Columns, or none for one total row"
                       onChange={(by) => onChange({...aggregate, by})}/>
            <Text size="sm" fw={500}>Measures</Text>
            {measures.map(([name, expression], index) => (
                <MeasureRow key={index} name={name} expression={expression} columns={columns}
                            onChange={(nextName, nextExpression) => setMeasures(measures.map((measure, at) => (
                                at === index ? [nextName, nextExpression] : measure)))}
                            onRemove={() => setMeasures(measures.filter((_, at) => at !== index))}/>
            ))}
            <UnstyledButton className="df-rows-add"
                            onClick={() => setMeasures([...measures, [unique('rows', measures.map(([name]) => name)), 'count(*)']])}>
                <IconPlus {...ICON_SMALL}/>Add measure
            </UnstyledButton>
        </Stack>
    );
}

function MeasureRow({name, expression, columns, onChange, onRemove}: {
    name: string;
    expression: string;
    columns: string[];
    onChange: (name: string, expression: string) => void;
    onRemove: () => void;
}) {
    const parsed = expression.match(MEASURE);
    const fn = parsed ? `${parsed[1].toLowerCase()}${parsed[2] ? ' distinct' : ''}` : CUSTOM;
    const column = parsed?.[3] ?? '';
    const build = (nextFn: string, nextColumn: string) => (nextFn === 'count distinct'
        ? `count(DISTINCT ${nextColumn || '*'})` : `${nextFn}(${nextColumn || '*'})`);
    return (
        <div className="df-measure">
            <Group gap="snug" wrap="nowrap">
                <TextInput size="xs" value={name} placeholder="name" aria-label="Measure name" classNames={{input: 'df-mono'}}
                           style={{flex: 1}} onChange={(event) => onChange(event.currentTarget.value, expression)}/>
                <Select size="xs" data={[...FUNCTIONS, CUSTOM]} value={fn} allowDeselect={false} aria-label="Function" w={124}
                        onChange={(next) => onChange(name, next === CUSTOM ? expression : build(next ?? 'count', column === '*' ? '' : column))}/>
                <IconAction label="Remove" icon={IconX} onClick={onRemove}/>
            </Group>
            {fn === CUSTOM ? (
                <TextInput size="xs" value={expression} aria-label="Expression" classNames={{input: 'df-mono'}}
                           onChange={(event) => onChange(name, event.currentTarget.value)}/>
            ) : (
                <Select size="xs" data={['*', ...columns]} value={column || '*'} aria-label="Column" searchable
                        onChange={(next) => onChange(name, build(fn, next === '*' ? '' : next ?? ''))}/>
            )}
        </div>
    );
}

function Expression({label, value, placeholder, columns, onChange}: {
    label: string;
    value: string;
    placeholder: string;
    columns: string[];
    onChange: (value: string) => void;
}) {
    return (
        <TextInput label={label} value={value} placeholder={placeholder} classNames={{input: 'df-mono'}}
                   description={columns.length ? `Columns: ${columns.slice(0, COLUMN_PREVIEW).join(', ')}${columns.length > COLUMN_PREVIEW ? ', …' : ''}` : undefined}
                   onChange={(event) => onChange(event.currentTarget.value)}/>
    );
}
