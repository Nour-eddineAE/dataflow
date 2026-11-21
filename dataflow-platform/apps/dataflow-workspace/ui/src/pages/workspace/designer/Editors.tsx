import {Autocomplete, Checkbox, NumberInput, Select, Switch, TextInput, UnstyledButton} from '@mantine/core';
import {IconPlus, IconX} from '../../../icons';
import {type ReactNode, useState} from 'react';
import {IconAction} from '../../../components/actions/IconAction';
import {ICON_SMALL} from '../../../lib/icons';

export const FORMATS = ['csv', 'json', 'parquet', 'orc', 'avro', 'text', 'delta', 'xml'];
export const SPARK_TYPES = ['string', 'int', 'bigint', 'double', 'float', 'boolean', 'date', 'timestamp', 'timestamp_ntz',
    'decimal(18,2)', 'array<string>', 'map<string,string>'];

export function FormSection({title, children}: { title: string; children: ReactNode }) {
    return (
        <section className="df-form-section">
            <h3 className="df-form-section-title">{title}</h3>
            <div className="df-form-section-body">{children}</div>
        </section>
    );
}

export type Column<T> = {
    key: keyof T & string;
    label: string;
    width?: number | string;
    kind?: 'text' | 'type' | 'check' | 'select';
    options?: string[];
    placeholder?: string;
};

export function RowsEditor<T extends Record<string, unknown>>({rows, columns, empty, add, onChange}: {
    rows: T[];
    columns: Column<T>[];
    empty: T;
    add: string;
    onChange: (rows: T[]) => void;
}) {
    const update = (index: number, key: string, value: unknown) =>
        onChange(rows.map((row, position) => (position === index ? {...row, [key]: value} : row)));
    const template = `${columns.map((column) => width(column.width)).join(' ')} 28px`;
    return (
        <div className="df-rows">
            {rows.length > 0 && (
                <div className="df-rows-grid" style={{gridTemplateColumns: template}}>
                    {columns.map((column) => <span key={column.key} className="df-rows-head">{column.label}</span>)}
                    <span/>
                    {rows.map((row, index) => (
                        <Row key={index} row={row} columns={columns} onChange={(key, value) => update(index, key, value)}
                             onRemove={() => onChange(rows.filter((_, position) => position !== index))}/>
                    ))}
                </div>
            )}
            <UnstyledButton className="df-rows-add" onClick={() => onChange([...rows, {...empty}])}>
                <IconPlus {...ICON_SMALL}/>{add}
            </UnstyledButton>
        </div>
    );
}

function Row<T extends Record<string, unknown>>({row, columns, onChange, onRemove}: {
    row: T;
    columns: Column<T>[];
    onChange: (key: string, value: unknown) => void;
    onRemove: () => void;
}) {
    return (
        <>
            {columns.map((column) => <Cell key={column.key} column={column} value={row[column.key]}
                                           onChange={(value) => onChange(column.key, value)}/>)}
            <span className="df-rows-remove"><IconAction label="Remove" icon={IconX} onClick={onRemove}/></span>
        </>
    );
}

function Cell<T>({column, value, onChange}: { column: Column<T>; value: unknown; onChange: (value: unknown) => void }) {
    const text = value === undefined || value === null ? '' : String(value);
    switch (column.kind) {
        case 'check':
            return <Checkbox size="xs" checked={value === true} aria-label={column.label} className="df-rows-check"
                             onChange={(event) => onChange(event.currentTarget.checked || undefined)}/>;
        case 'select':
            return <Select size="xs" data={column.options ?? []} value={text || null} aria-label={column.label}
                           allowDeselect={false} onChange={(next) => onChange(next ?? undefined)}/>;
        case 'type':
            return <Autocomplete size="xs" data={SPARK_TYPES} value={text} aria-label={column.label}
                                 classNames={{input: 'df-mono'}} onChange={onChange}/>;
        default:
            return <TextInput size="xs" value={text} aria-label={column.label} placeholder={column.placeholder}
                              classNames={{input: 'df-mono'}} onChange={(event) => onChange(event.currentTarget.value)}/>;
    }
}

function width(value: number | string | undefined) {
    return value === undefined ? 'minmax(0, 1fr)' : typeof value === 'number' ? `${value}px` : value;
}

type Entry = { key: string; value: string };

export function KeyValueEditor({values, onChange, add, keyLabel = 'Name', valueLabel = 'Value'}: {
    values: Record<string, unknown> | undefined;
    onChange: (values: Record<string, string>) => void;
    add: string;
    keyLabel?: string;
    valueLabel?: string;
}) {
    const [rows, setRows] = useState(() => entries(values));
    const [shown, setShown] = useState(values);
    if (shown !== values) {
        setShown(values);
        if (!same(rows, values)) setRows(entries(values));
    }
    const write = (next: Entry[]) => {
        setRows(next);
        onChange(Object.fromEntries(next.filter((row) => row.key.trim()).map((row) => [row.key.trim(), row.value])));
    };
    return (
        <RowsEditor rows={rows} empty={{key: '', value: ''}} add={add} onChange={write}
                    columns={[{key: 'key', label: keyLabel, width: '40%'}, {key: 'value', label: valueLabel}]}/>
    );
}

function entries(values: Record<string, unknown> | undefined): Entry[] {
    return Object.entries(values ?? {}).map(([key, value]) => ({key, value: value === undefined ? '' : String(value)}));
}

function same(rows: Entry[], values: Record<string, unknown> | undefined) {
    const named = rows.filter((row) => row.key.trim());
    const given = entries(values);
    return named.length === given.length
        && named.every((row, index) => row.key.trim() === given[index].key && row.value === given[index].value);
}

const DECIMALS = new Set(['double', 'float', 'Double', 'Float', 'BigDecimal']);
const INTEGERS = new Set(['int', 'long', 'short', 'Integer', 'Long', 'Short']);

export function ParamField({name, type, value, onChange}: {
    name: string;
    type: string;
    value: unknown;
    onChange: (value: unknown) => void;
}) {
    if (type === 'boolean' || type === 'Boolean') {
        return <Switch label={name} checked={value === true} onChange={(event) => onChange(event.currentTarget.checked)}/>;
    }
    if (DECIMALS.has(type) || INTEGERS.has(type)) {
        return <NumberInput label={name} description={type} value={typeof value === 'number' ? value : ''}
                            allowDecimal={DECIMALS.has(type)} onChange={(next) => onChange(typeof next === 'number' ? next : undefined)}/>;
    }
    return <TextInput label={name} description={type} value={value === undefined ? '' : String(value)}
                      onChange={(event) => onChange(event.currentTarget.value)}/>;
}
