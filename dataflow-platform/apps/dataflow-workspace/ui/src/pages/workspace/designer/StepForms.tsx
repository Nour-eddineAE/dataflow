import {Group, Select, Text, TextInput} from '@mantine/core';
import {IconArrowDown, IconArrowUp} from '../../../icons';
import {useTransformations} from '../../../api/hooks';
import type {TransformationInfo} from '../../../api/types';
import {IconAction} from '../../../components/actions/IconAction';
import {type Column, type Spec, type Step, REFERENCE, ROWS} from '../../../lib/specModel';
import {FormSection, KeyValueEditor, ParamField, RowsEditor} from './Editors';

export function StepForm({spec, step, folderId, position, count, onChange, onMove}: {
    spec: Spec;
    step: Step;
    folderId: string;
    position: number;
    count: number;
    onChange: (step: Step) => void;
    onMove: (delta: number) => void;
}) {
    return (
        <>
            <FormSection title="Step">
                <TextInput label="Name" classNames={{input: 'df-mono'}} value={step.name}
                           onChange={(event) => onChange({...step, name: event.currentTarget.value})}/>
                <Group justify="space-between" wrap="nowrap">
                    <Text size="sm">Runs {ordinal(position + 1)} of {count}</Text>
                    <Group gap={2} wrap="nowrap">
                        <IconAction label="Move earlier" icon={IconArrowUp} disabled={position === 0} onClick={() => onMove(-1)}/>
                        <IconAction label="Move later" icon={IconArrowDown} disabled={position === count - 1} onClick={() => onMove(1)}/>
                    </Group>
                </Group>
            </FormSection>
            <TransformationFields folderId={folderId} value={step} main={ROWS} spec={spec}
                                  onChange={(next) => onChange({...step, ...next, transformation: next.transformation ?? ''})}/>
            <FormSection title="Adds columns">
                <RowsEditor<Column> rows={step.adds ?? []} empty={{name: '', type: 'double'}} add="Add column"
                                    onChange={(adds) => onChange({...step, adds})}
                                    columns={[{key: 'name', label: 'Name'}, {key: 'type', label: 'Type', width: 120, kind: 'type'}]}/>
            </FormSection>
        </>
    );
}

type Wired = { transformation?: string; inputs?: Record<string, string>; params?: Record<string, unknown> };

export function TransformationFields({folderId, value, main, spec, onChange}: {
    folderId: string;
    value: Wired;
    main: string;
    spec: Spec;
    onChange: (value: Wired) => void;
}) {
    const available = useTransformations(folderId);
    const known = available.data ?? [];
    const chosen = known.find((candidate) => candidate.id === value.transformation);
    const sources = [...new Set([main, ...(spec.references ?? []).map((reference) => `${REFERENCE}${reference.name}`),
        ...Object.keys(spec.inputs ?? {}), ...(spec.gold ?? []).map((table) => table.name)])];
    const choices = [...new Set([...known.map((info) => info.id), ...(value.transformation ? [value.transformation] : [])])];
    const choose = (info: TransformationInfo) => onChange({
        transformation: info.id,
        inputs: Object.fromEntries(info.inputs.map((input, position) => [input, value.inputs?.[input] ?? (position === 0 ? main : '')])),
        params: Object.fromEntries(info.params.flatMap((param) => (
            value.params?.[param.key] === undefined ? [] : [[param.key, value.params[param.key]]]))),
    });
    return (
        <>
            <FormSection title="Transformation">
                <Select searchable placeholder={available.isLoading ? 'Loading…' : 'Choose a transformation'} data={choices}
                        value={value.transformation || null} nothingFoundMessage="None in transformations/"
                        onChange={(id) => {
                            const info = known.find((candidate) => candidate.id === id);
                            if (info) choose(info);
                        }}/>
            </FormSection>
            {chosen ? (
                <>
                    {chosen.inputs.length > 0 && (
                        <FormSection title="Inputs">
                            {chosen.inputs.map((input) => (
                                <Select key={input} label={input} data={sources} value={value.inputs?.[input] || null}
                                        onChange={(source) => onChange({...value, inputs: {...value.inputs, [input]: source ?? ''}})}/>
                            ))}
                        </FormSection>
                    )}
                    {chosen.params.length > 0 && (
                        <FormSection title="Parameters">
                            {chosen.params.map((param) => (
                                <ParamField key={param.key} name={param.key} type={param.type} value={value.params?.[param.key]}
                                            onChange={(next) => onChange({...value, params: {...value.params, [param.key]: next}})}/>
                            ))}
                        </FormSection>
                    )}
                </>
            ) : (
                <>
                    <FormSection title="Inputs">
                        <KeyValueEditor values={value.inputs} add="Add input" keyLabel="Input" valueLabel="Reads"
                                        onChange={(inputs) => onChange({...value, inputs})}/>
                    </FormSection>
                    <FormSection title="Parameters">
                        <KeyValueEditor values={value.params} add="Add parameter" onChange={(params) => onChange({...value, params})}/>
                    </FormSection>
                </>
            )}
        </>
    );
}

const TEEN_FIRST = 11;
const TEEN_LAST = 13;
const DECIMAL = 10;
const ORDINAL_SUFFIX: Record<number, string> = {1: 'st', 2: 'nd', 3: 'rd'};

function ordinal(value: number) {
    const lastTwo = value % 100;
    const suffix = lastTwo >= TEEN_FIRST && lastTwo <= TEEN_LAST ? 'th' : ORDINAL_SUFFIX[value % DECIMAL] ?? 'th';
    return `${value}${suffix}`;
}
