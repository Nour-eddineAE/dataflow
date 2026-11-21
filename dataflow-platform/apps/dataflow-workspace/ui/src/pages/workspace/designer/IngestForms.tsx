import {Autocomplete, Group, MultiSelect, NumberInput, SegmentedControl, Select, Text, Textarea, TextInput} from '@mantine/core';
import {useComputePolicy} from '../../../api/hooks';
import {ComputeFields} from '../../../components/ComputeFields';
import {formProblems, fromForm, fromSpec, toForm, toSpec} from '../../../lib/compute';
import {type Column, type Expectation, type Spec} from '../../../lib/specModel';
import {FORMATS, FormSection, KeyValueEditor, RowsEditor} from './Editors';

type SpecForm = { spec: Spec; onChange: (spec: Spec) => void };
type Partitions = NonNullable<NonNullable<Spec['source']>['partitions']>;

export function PipelineForm({spec, onChange}: SpecForm) {
    return (
        <>
            <GeneralForm spec={spec} onChange={onChange}/>
            <ComputeForm spec={spec} onChange={onChange}/>
        </>
    );
}

function ComputeForm({spec, onChange}: SpecForm) {
    const {data: policy = {}} = useComputePolicy();
    const compute = fromSpec(spec.compute);
    const form = toForm(compute);
    return (
        <FormSection title="Compute">
            <Text size="xs" c="dimmed">Empty fields use the project's size for the target, then the install default.</Text>
            <ComputeFields form={form} problems={formProblems(form, policy)}
                           onChange={(next) => onChange({...spec, compute: toSpec(fromForm(next, compute))})}/>
        </FormSection>
    );
}

function GeneralForm({spec, onChange}: SpecForm) {
    return (
        <FormSection title="General">
            <TextInput label="Owner" value={spec.owner ?? ''} onChange={(event) => onChange({...spec, owner: event.currentTarget.value})}/>
            <Textarea label="Description" autosize minRows={2} maxRows={6} value={spec.description ?? ''}
                      onChange={(event) => onChange({...spec, description: event.currentTarget.value})}/>
            <TextInput label="Schedule" placeholder="0 */6 * * *" classNames={{input: 'df-mono'}} value={spec.schedule ?? ''}
                       onChange={(event) => onChange({...spec, schedule: event.currentTarget.value})}/>
        </FormSection>
    );
}

export function SourceForm({spec, onChange}: SpecForm) {
    const source = spec.source ?? {};
    const where = source.path !== undefined && source.url === undefined ? 'path' : 'url';
    const partitioning = source.partitions?.monthly ? 'monthly' : source.partitions?.daily ? 'daily' : 'none';
    const start = source.partitions?.monthly?.start ?? source.partitions?.daily?.start ?? '';
    const set = (next: Spec['source']) => onChange({...spec, source: next});
    const partitions = (kind: string, value: string): Partitions | undefined => (kind === 'none' ? undefined : {[kind]: {start: value}});
    return (
        <>
            <FormSection title="Location">
                <SegmentedControl fullWidth size="xs" value={where}
                                  data={[{value: 'url', label: 'Web address'}, {value: 'path', label: 'Lake path'}]}
                                  onChange={(next) => set({...source, url: next === 'url' ? source.path ?? '' : undefined,
                                      path: next === 'path' ? source.url ?? '' : undefined})}/>
                <TextInput label={where === 'url' ? 'URL' : 'Path'} classNames={{input: 'df-mono'}}
                           value={(where === 'url' ? source.url : source.path) ?? ''}
                           onChange={(event) => set({...source, [where]: event.currentTarget.value})}/>
                <Autocomplete label="Format" data={FORMATS} value={source.format ?? ''} onChange={(format) => set({...source, format})}/>
            </FormSection>
            <FormSection title="Reader options">
                <KeyValueEditor values={source.options} add="Add option" keyLabel="Option"
                                onChange={(options) => set({...source, options})}/>
            </FormSection>
            <FormSection title="Partitions">
                <Group grow>
                    <Select label="Split by" allowDeselect={false} value={partitioning}
                            data={[{value: 'none', label: 'None'}, {value: 'monthly', label: 'Month'}, {value: 'daily', label: 'Day'}]}
                            onChange={(kind) => set({...source, partitions: partitions(kind ?? 'none', start)})}/>
                    <TextInput label="From" placeholder={partitioning === 'daily' ? '2024-01-01' : '2024-01'}
                               disabled={partitioning === 'none'} value={start}
                               onChange={(event) => set({...source, partitions: partitions(partitioning, event.currentTarget.value)})}/>
                </Group>
            </FormSection>
        </>
    );
}

export function BronzeForm({spec, onChange}: SpecForm) {
    const bronze = spec.bronze ?? {};
    return (
        <FormSection title="Ingestion">
            <Select label="New columns" allowDeselect={false} value={bronze['schema-evolution'] ?? 'rescue'}
                    data={[{value: 'rescue', label: 'Keep in _rescued_data'}, {value: 'add-new-columns', label: 'Add to the table'}]}
                    onChange={(value) => onChange({...spec, bronze: {...bronze, 'schema-evolution': value as 'rescue' | 'add-new-columns'}})}/>
            <NumberInput label="Files per batch" min={1} placeholder="10" value={bronze['max-files-per-trigger'] ?? ''}
                         onChange={(value) => onChange({...spec, bronze: {...bronze, 'max-files-per-trigger': typeof value === 'number' ? value : undefined}})}/>
        </FormSection>
    );
}

export function SilverForm({spec, onChange}: SpecForm) {
    const silver = spec.silver ?? {};
    const set = (key: 'columns' | 'derive' | 'expectations', rows: unknown[]) => onChange({...spec, silver: {...silver, [key]: rows}});
    return (
        <>
            <FormSection title="Columns">
                <RowsEditor<Column> rows={silver.columns ?? []} empty={{name: '', type: 'string'}} add="Add column"
                                    onChange={(rows) => set('columns', rows)}
                                    columns={[{key: 'name', label: 'Name'}, {key: 'type', label: 'Type', width: 104, kind: 'type'},
                                        {key: 'required', label: 'Required', width: 64, kind: 'check'},
                                        {key: 'from', label: 'From', placeholder: 'raw column or SQL'}]}/>
            </FormSection>
            <FormSection title="Derived">
                <RowsEditor<Column> rows={silver.derive ?? []} empty={{name: '', type: 'string', from: ''}} add="Add derived column"
                                    onChange={(rows) => set('derive', rows)}
                                    columns={[{key: 'name', label: 'Name'}, {key: 'type', label: 'Type', width: 104, kind: 'type'},
                                        {key: 'from', label: 'Expression', placeholder: 'SQL over columns'}]}/>
            </FormSection>
            <FormSection title="Quality rules">
                <RowsEditor<Expectation> rows={silver.expectations ?? []} empty={{name: '', check: '', 'on-failure': 'warn'}}
                                         add="Add rule" onChange={(rows) => set('expectations', rows)}
                                         columns={[{key: 'name', label: 'Name'}, {key: 'check', label: 'Check', placeholder: 'SQL condition'},
                                             {key: 'on-failure', label: 'If not', width: 84, kind: 'select', options: ['warn', 'drop', 'fail']}]}/>
            </FormSection>
        </>
    );
}

export function HistoryForm({spec, onChange}: SpecForm) {
    const history = spec.history ?? {};
    const columns = (spec.silver?.columns ?? []).map((column) => column.name).filter(Boolean);
    const set = (next: Spec['history']) => onChange({...spec, history: next});
    return (
        <FormSection title="Versions">
            <MultiSelect label="Keys" data={columns} value={history.keys ?? []} searchable onChange={(keys) => set({...history, keys})}/>
            <Select label="Order versions by" placeholder="Arrival time" data={columns} value={history['sequence-by'] ?? null}
                    clearable onChange={(value) => set({...history, 'sequence-by': value ?? undefined})}/>
            <MultiSelect label="Ignore changes to" data={columns} value={history['track-history-except'] ?? []} searchable
                         onChange={(names) => set({...history, 'track-history-except': names})}/>
        </FormSection>
    );
}
