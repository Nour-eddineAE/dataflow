import {Autocomplete, Box, Group, Popover, SegmentedControl, Select, Stack, Switch, Text, TextInput} from '@mantine/core';
import {lazy, Suspense, useRef} from 'react';
import {IconAction} from '../../../components/actions/IconAction';
import type {EditorHandle} from '../../../components/CodeEditor';
import {RelationBrowser} from '../../../components/RelationBrowser';
import {IconTable} from '../../../icons';
import {insertAtCursor} from '../../../lib/editorInsert';
import {bodyOf, CURRENT, type Gold, type GoldBody, ingests, type Reference, relations, renameInput, type Serve, type Spec} from '../../../lib/specModel';
import {relationColumns} from '../../../lib/goldColumns';
import {useCatalog, useSchema} from '../../../api/hooks';
import {GoldSteps} from './GoldSteps';
import {useStepPreview} from './StepPreview';
import {FORMATS, FormSection, KeyValueEditor} from './Editors';
import {TransformationFields} from './StepForms';

const TABLES_WIDTH = 300;
const TABLES_HEIGHT = 360;

const CodeEditor = lazy(() => import('../../../components/CodeEditor').then((module) => ({default: module.CodeEditor})));

export function ReferenceForm({reference, onChange, onRename}: {
    reference: Reference;
    onChange: (reference: Reference) => void;
    onRename: (name: string) => void;
}) {
    return (
        <>
            <FormSection title="Reference">
                <TextInput label="Name" classNames={{input: 'df-mono'}} value={reference.name}
                           onChange={(event) => onRename(event.currentTarget.value)}/>
                <TextInput label="URL or lake path" classNames={{input: 'df-mono'}} value={reference.url ?? reference.path ?? ''}
                           onChange={(event) => {
                               const location = event.currentTarget.value;
                               const web = location === '' || /^https?:\/\//.test(location);
                               onChange({...reference, url: web ? location : undefined, path: web ? undefined : location});
                           }}/>
                <Autocomplete label="Format" data={FORMATS} value={reference.format} onChange={(format) => onChange({...reference, format})}/>
            </FormSection>
            <FormSection title="Reader options">
                <KeyValueEditor values={reference.options} add="Add option" keyLabel="Option"
                                onChange={(options) => onChange({...reference, options})}/>
            </FormSection>
        </>
    );
}

export function InputForm({spec, alias, onChange}: { spec: Spec; alias: string; onChange: (spec: Spec) => void }) {
    const catalog = useCatalog();
    const schema = useSchema();
    const table = spec.inputs?.[alias] ?? '';
    const [dataset, ...rest] = table.split('.');
    const datasets = (catalog.data?.datasets ?? []).map((entry) => entry.dataset).filter((name) => name !== spec.dataset);
    const tables = (catalog.data?.datasets ?? []).find((entry) => entry.dataset === dataset)?.tables
        .filter((candidate) => candidate.delta)
        .map((candidate) => ({value: candidate.id, label: candidate.label + (candidate.exists ? '' : ' (no data yet)')})) ?? [];
    const columns = relationColumns(spec, schema.data)(alias);
    const set = (next: string) => onChange({...spec, inputs: {...spec.inputs, [alias]: next}});
    return (
        <>
            <FormSection title="Input">
                <TextInput label="Name in this pipeline" classNames={{input: 'df-mono'}} value={alias}
                           description="Tables and steps read it under this name"
                           onChange={(event) => onChange(renameInput(spec, alias, event.currentTarget.value))}/>
                <Select label="Pipeline" searchable data={datasets} value={dataset || null} placeholder="Choose a pipeline"
                        nothingFoundMessage="No other pipeline in the catalog"
                        onChange={(next) => set(next ? `${next}.current` : '')}/>
                <Select label="Table" data={tables} value={rest.join('.') || null} disabled={!dataset} allowDeselect={false}
                        onChange={(next) => set(next ? `${dataset}.${next}` : dataset)}/>
            </FormSection>
            {columns.length > 0 && (
                <FormSection title={`Columns (${columns.length})`}>
                    <Text size="xs" className="df-mono" c="dimmed">{columns.join(', ')}</Text>
                </FormSection>
            )}
        </>
    );
}

const BODIES: { value: GoldBody; label: string }[] = [
    {value: 'steps', label: 'Steps'},
    {value: 'sql', label: 'SQL'},
    {value: 'transformation', label: 'Java'},
];

function GoldQuery({spec, gold, sources, onChange}: {
    spec: Spec;
    gold: Gold;
    sources: string[];
    onChange: (gold: Gold) => void;
}) {
    const editor = useRef<EditorHandle | null>(null);
    return (
        <FormSection title="Query">
            <div className="df-inspector-code">
                <Suspense fallback={null}>
                    <CodeEditor language="sql" value={gold.sql ?? ''} onChange={(sql) => onChange({...gold, sql})}
                                onEditor={(instance) => {
                                    editor.current = instance;
                                }}/>
                </Suspense>
            </div>
            <Group justify="space-between" wrap="nowrap" gap="tight">
                <Text size="xs" c="dimmed">Reads by name: {sources.filter((name) => name !== gold.name).join(', ') || 'nothing yet'}</Text>
                <Popover width={TABLES_WIDTH} position="left-start" shadow="md" withinPortal>
                    <Popover.Target>
                        <IconAction label="Tables" icon={IconTable}/>
                    </Popover.Target>
                    <Popover.Dropdown p={0}>
                        <Box h={TABLES_HEIGHT}>
                            <RelationBrowser spec={spec} except={gold.name} onInsert={(text) => insertAtCursor(editor.current, text)}/>
                        </Box>
                    </Popover.Dropdown>
                </Popover>
            </Group>
        </FormSection>
    );
}

export function GoldForm({spec, gold, folderId, onChange, onRename}: {
    spec: Spec;
    gold: Gold;
    folderId: string;
    onChange: (gold: Gold) => void;
    onRename: (name: string) => void;
}) {
    const schema = useSchema();
    const steps = useStepPreview(spec, folderId);
    const body = bodyOf(gold);
    const sources = relations(spec);
    const reshape = (next: GoldBody) => {
        const start = gold.from || sources.find((name) => name !== gold.name) || CURRENT;
        onChange({
            name: gold.name,
            materialize: gold.materialize,
            ...(next === 'steps' ? {from: start, steps: gold.steps ?? []}
                : next === 'sql' ? {sql: gold.sql ?? `SELECT *\nFROM ${start}\n`}
                    : {transformation: gold.transformation ?? '', inputs: gold.inputs ?? {rows: start}}),
        });
    };
    return (
        <>
            <FormSection title="Table">
                <TextInput label="Name" classNames={{input: 'df-mono'}} value={gold.name} onChange={(event) => onRename(event.currentTarget.value)}/>
                <Switch label="Store as a table" checked={gold.materialize !== false}
                        description={gold.materialize === false ? 'Computed inside the tables that read it; never written' : 'Written to the lake every run'}
                        onChange={(event) => onChange({...gold, materialize: event.currentTarget.checked ? undefined : false})}/>
                <Stack gap="tight">
                    <Text size="sm" fw={500}>Built with</Text>
                    <SegmentedControl fullWidth size="xs" value={body} data={BODIES} onChange={(next) => reshape(next as GoldBody)}/>
                </Stack>
            </FormSection>
            {body === 'steps' && (
                <GoldSteps gold={gold} sources={sources} columns={relationColumns(spec, schema.data)} onChange={onChange}
                           onPreview={(index) => steps.preview(gold.name, index)} previewing={steps.pending}/>
            )}
            {body === 'sql' && (
                <GoldQuery spec={spec} gold={gold} sources={sources} onChange={onChange}/>
            )}
            {body === 'transformation' && (
                <>
                    <TransformationFields folderId={folderId} value={gold} main={ingests(spec) ? CURRENT : sources[0] ?? CURRENT} spec={spec}
                                          onChange={(next) => onChange({...gold, ...next, transformation: next.transformation ?? ''})}/>
                    <FormSection title="Lineage">
                        <Text size="xs" c="dimmed">Column lineage stops here: Java code is not traced</Text>
                    </FormSection>
                </>
            )}
        </>
    );
}

export function ServeForm({spec, serve, onChange}: { spec: Spec; serve: Serve; onChange: (serve: Serve) => void }) {
    const copy = serve.mode === 'copy';
    return (
        <FormSection title="Serving">
            <TextInput label="Table" classNames={{input: 'df-mono'}} value={serve.table}
                       onChange={(event) => onChange({...serve, table: event.currentTarget.value})}/>
            <Select label="From" allowDeselect={false} value={serve.from}
                    data={[...(ingests(spec) ? [CURRENT] : []),
                        ...(spec.gold ?? []).filter((table) => table.materialize !== false).map((table) => `gold.${table.name}`)]}
                    onChange={(from) => onChange({...serve, from: from ?? CURRENT})}/>
            <Stack gap="tight">
                <Text size="sm" fw={500}>Kept as</Text>
                <SegmentedControl fullWidth size="xs" value={serve.mode ?? 'view'}
                                  data={[{value: 'view', label: 'View'}, {value: 'copy', label: 'Copy'}]}
                                  onChange={(mode) => onChange({...serve, mode: mode as 'view' | 'copy'})}/>
            </Stack>
            <TextInput label="Order by" classNames={{input: 'df-mono'}} disabled={!copy} placeholder={copy ? 'sort key' : 'copies only'}
                       value={serve['order-by'] ?? ''} onChange={(event) => onChange({...serve, 'order-by': event.currentTarget.value})}/>
        </FormSection>
    );
}
