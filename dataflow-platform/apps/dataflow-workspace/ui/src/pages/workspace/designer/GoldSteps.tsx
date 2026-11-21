import {Accordion, Group, Menu, Select, Text, UnstyledButton} from '@mantine/core';
import {type Icon, IconArrowDown, IconArrowUp, IconArrowsJoin, IconColumnInsertRight, IconColumnRemove, IconColumns, IconCopyOff, IconCursorText, IconEye, IconFilter, IconLayersUnion, IconPlus, IconSum, IconTrash} from '../../../icons';
import {useState} from 'react';
import {IconAction} from '../../../components/actions/IconAction';
import {type Columns, columnsAfter} from '../../../lib/goldColumns';
import {type Gold, type GoldStep, moveGoldStep, type Operation, OPERATION_LABELS, OPERATIONS, operationOf} from '../../../lib/specModel';
import {FormSection} from './Editors';
import {ICON, ICON_SMALL} from '../../../lib/icons';
import {StepForm} from './GoldStepForm';

const ICONS: Record<Operation, Icon> = {
    join: IconArrowsJoin,
    union: IconLayersUnion,
    filter: IconFilter,
    derive: IconColumnInsertRight,
    select: IconColumns,
    rename: IconCursorText,
    drop: IconColumnRemove,
    aggregate: IconSum,
    dedupe: IconCopyOff,
};

const HINTS: Record<Operation, string> = {
    join: 'Match rows with another table',
    union: 'Add the rows of other tables below',
    filter: 'Keep rows that meet a condition',
    derive: 'Compute new columns',
    select: 'Keep only some columns, in order',
    rename: 'Give columns new names',
    drop: 'Leave columns out',
    aggregate: 'Summarize rows by group',
    dedupe: 'Keep one row per key',
};

const EMPTY: Record<Operation, GoldStep> = {
    join: {join: {with: '', type: 'left', on: {}}},
    union: {union: []},
    filter: {filter: ''},
    derive: {derive: {}},
    select: {select: []},
    rename: {rename: {}},
    drop: {drop: []},
    aggregate: {aggregate: {by: [], measures: {}}},
    dedupe: {dedupe: {keys: []}},
};

type Props = {
    gold: Gold;
    sources: string[];
    columns: Columns;
    onChange: (gold: Gold) => void;
    onPreview?: (index: number) => void;
    previewing?: boolean;
};

export function GoldSteps({gold, sources, columns, onChange, onPreview, previewing}: Props) {
    const steps = gold.steps ?? [];
    const [open, setOpen] = useState<string | null>(steps.length ? String(steps.length - 1) : null);
    const others = sources.filter((name) => name !== gold.name);
    const setStep = (index: number, step: GoldStep) => onChange({...gold, steps: steps.map((current, at) => (at === index ? step : current))});
    const add = (operation: Operation) => {
        onChange({...gold, steps: [...steps, structuredClone(EMPTY[operation])]});
        setOpen(String(steps.length));
    };
    return (
        <>
            <FormSection title="Start from">
                <Select searchable allowDeselect={false} placeholder="Choose a table" data={others} value={gold.from || null}
                        nothingFoundMessage="Add an input or a reference first"
                        description={gold.from ? `${columns(gold.from).length} columns` : undefined}
                        onChange={(from) => onChange({...gold, from: from ?? ''})}/>
            </FormSection>
            <FormSection title="Steps">
                {steps.length === 0 && <Text size="sm" c="dimmed">Rows go through each step in order. Add the first one.</Text>}
                <Accordion value={open} onChange={setOpen} variant="separated" className="df-steps">
                    {steps.map((step, index) => {
                        const operation = operationOf(step);
                        if (!operation) return null;
                        const Glyph = ICONS[operation];
                        const before = columnsAfter(gold, index, columns);
                        return (
                            <Accordion.Item key={index} value={String(index)}>
                                <div className="df-step-head">
                                    <Accordion.Control icon={<Glyph {...ICON}/>}>
                                        <span className="df-step-title">{OPERATION_LABELS[operation]}</span>
                                        <span className="df-step-summary">{summary(step, operation)}</span>
                                    </Accordion.Control>
                                    <Group gap={0} wrap="nowrap" className="df-step-actions">
                                        {onPreview && (
                                            <IconAction label="Preview rows after this step" icon={IconEye} disabled={previewing}
                                                        onClick={() => onPreview(index)}/>
                                        )}
                                        <IconAction label="Move earlier" icon={IconArrowUp} disabled={index === 0}
                                                    onClick={() => onChange(moveGoldStep(gold, index, -1))}/>
                                        <IconAction label="Move later" icon={IconArrowDown} disabled={index === steps.length - 1}
                                                    onClick={() => onChange(moveGoldStep(gold, index, 1))}/>
                                        <IconAction label="Remove step" icon={IconTrash} danger
                                                    onClick={() => onChange({...gold, steps: steps.filter((_, at) => at !== index)})}/>
                                    </Group>
                                </div>
                                <Accordion.Panel>
                                    <StepForm step={step} operation={operation} columns={before} tables={others} columnsOf={columns}
                                              onChange={(next) => setStep(index, next)}/>
                                </Accordion.Panel>
                            </Accordion.Item>
                        );
                    })}
                </Accordion>
                <Menu position="bottom-start" width={260} shadow="md">
                    <Menu.Target>
                        <UnstyledButton className="df-rows-add"><IconPlus {...ICON_SMALL}/>Add step</UnstyledButton>
                    </Menu.Target>
                    <Menu.Dropdown>
                        {OPERATIONS.map((operation) => {
                            const Glyph = ICONS[operation];
                            return (
                                <Menu.Item key={operation} leftSection={<Glyph {...ICON}/>} onClick={() => add(operation)}>
                                    <Text size="sm">{OPERATION_LABELS[operation]}</Text>
                                    <Text size="xs" c="dimmed">{HINTS[operation]}</Text>
                                </Menu.Item>
                            );
                        })}
                    </Menu.Dropdown>
                </Menu>
            </FormSection>
        </>
    );
}

function summary(step: GoldStep, operation: Operation) {
    switch (operation) {
        case 'join': {
            const join = step.join!;
            const on = join.condition ?? Object.entries(join.on ?? {}).map(([a, b]) => (a === b ? a : `${a} = ${b}`)).join(', ');
            return [join.with || '…', on && `on ${on}`].filter(Boolean).join(' ');
        }
        case 'union':
            return step.union!.join(', ') || '…';
        case 'filter':
            return step.filter || '…';
        case 'derive':
            return Object.keys(step.derive!).join(', ') || '…';
        case 'select':
            return `${step.select!.length} columns`;
        case 'rename':
            return Object.entries(step.rename!).map(([a, b]) => `${a} → ${b}`).join(', ') || '…';
        case 'drop':
            return step.drop!.join(', ') || '…';
        case 'aggregate': {
            const by = step.aggregate!.by ?? [];
            const measures = Object.keys(step.aggregate!.measures ?? {});
            return `${by.length ? `by ${by.join(', ')}: ` : ''}${measures.join(', ') || '…'}`;
        }
        case 'dedupe': {
            const keys = step.dedupe!.keys ?? [];
            return `${keys.join(', ') || '…'}${step.dedupe!['latest-by'] ? `, latest ${step.dedupe!['latest-by']}` : ''}`;
        }
    }
}
