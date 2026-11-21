import {Autocomplete, NumberInput, SegmentedControl, SimpleGrid, Stack, Text} from '@mantine/core';
import type {ReactNode} from 'react';
import {type ComputeForm, type ComputeProblems, SIZES, type Switch} from '../lib/compute';

const DEFAULT = 'Default';

type Props = {
    form: ComputeForm;
    problems: ComputeProblems;
    onChange: (form: ComputeForm) => void;
};

export function ComputeFields({form, problems, onChange}: Props) {
    const set = <K extends keyof ComputeForm>(key: K) => (value: ComputeForm[K]) => onChange({...form, [key]: value});
    const whole = (value: string | number) => (typeof value === 'number' ? value : '');
    return (
        <Stack gap="lg">
            <FieldGroup title="Driver">
                <Autocomplete label="Memory" placeholder={DEFAULT} data={SIZES} value={form.driverMemory}
                              onChange={set('driverMemory')} error={problems.driverMemory}/>
                <NumberInput label="Cores" placeholder={DEFAULT} min={1} allowDecimal={false}
                             value={form.driverCores} error={problems.driverCores}
                             onChange={(value) => set('driverCores')(whole(value))}/>
            </FieldGroup>
            <FieldGroup title="Executors" hint="Kubernetes only; a laptop run is one container">
                <NumberInput label="Min" placeholder={DEFAULT} min={0} allowDecimal={false}
                             value={form.minExecutors} error={problems.minExecutors}
                             onChange={(value) => set('minExecutors')(whole(value))}/>
                <NumberInput label="Max" placeholder={DEFAULT} min={1} allowDecimal={false}
                             value={form.maxExecutors} error={problems.maxExecutors}
                             onChange={(value) => set('maxExecutors')(whole(value))}/>
                <Autocomplete label="Memory each" placeholder={DEFAULT} data={SIZES} value={form.executorMemory}
                              onChange={set('executorMemory')} error={problems.executorMemory}/>
                <NumberInput label="Cores each" placeholder={DEFAULT} min={1} allowDecimal={false}
                             value={form.executorCores} error={problems.executorCores}
                             onChange={(value) => set('executorCores')(whole(value))}/>
            </FieldGroup>
            <FieldGroup title="Spark">
                <NumberInput label="Shuffle partitions" placeholder={DEFAULT} min={1} allowDecimal={false}
                             value={form.shufflePartitions} onChange={(value) => set('shufflePartitions')(whole(value))}/>
                <SwitchField label="Adaptive execution" value={form.adaptive} onChange={set('adaptive')}/>
                <SwitchField label="Event log" value={form.eventLog} onChange={set('eventLog')}/>
            </FieldGroup>
        </Stack>
    );
}

const SWITCH = [{value: '', label: DEFAULT}, {value: 'true', label: 'On'}, {value: 'false', label: 'Off'}];

function SwitchField({label, value, onChange}: { label: string; value: Switch; onChange: (value: Switch) => void }) {
    return (
        <Stack gap="tight">
            <Text size="sm" fw={500}>{label}</Text>
            <SegmentedControl fullWidth aria-label={label} value={value} onChange={(choice) => onChange(choice as Switch)}
                              data={SWITCH}/>
        </Stack>
    );
}

function FieldGroup({title, hint, children}: { title: string; hint?: string; children: ReactNode }) {
    return (
        <div>
            <Text size="sm" fw={600}>{title}</Text>
            {hint && <Text size="xs" c="dimmed">{hint}</Text>}
            <SimpleGrid cols={2} spacing="sm" mt="cozy">{children}</SimpleGrid>
        </div>
    );
}
