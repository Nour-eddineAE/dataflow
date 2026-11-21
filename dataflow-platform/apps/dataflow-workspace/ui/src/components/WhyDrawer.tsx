import {Anchor, Drawer, Group, Select, Stack, Table, Text, Title} from '@mantine/core';
import {type ReactNode, useState} from 'react';
import {Link} from 'react-router-dom';
import {useLineage, useWhy} from '../api/hooks';
import type {Lineage, QueryResult} from '../api/types';
import {cellText, formatTime, shortRunId} from '../lib/format';
import {links} from '../lib/links';
import {DataGrid, fittedHeight} from './DataGrid';
import {Fact} from './Fact';
import {ErrorState, Loading} from './ErrorState';
import {DIALOG_WIDTH} from './Page';

const CHANGED_WEIGHT = 600;
const QUARANTINED_HEIGHT = 220;
const RAW_HEIGHT = 200;

export function WhyDrawer({dataset, id, column, onClose}: {
    dataset: string;
    id?: string;
    column?: string;
    onClose: () => void
}) {
    const [picked, setPicked] = useState<{ id?: string; asked?: string; column?: string }>();
    const chosen = picked?.id === id && picked?.asked === column ? picked?.column : column;
    const {data: lineage} = useLineage(dataset);
    const {data: why, error} = useWhy(dataset, id, chosen);

    const columns = lineage?.tables.find((table) => table.id === 'silver')?.columns
        .filter((entry) => entry.role === 'declared' || entry.role === 'derived')
        .map((entry) => entry.name) ?? [];

    return (
        <Drawer opened={!!id} onClose={onClose} position="right" size={DIALOG_WIDTH} title={<Text fw={600}>Trace</Text>}>
            <Stack gap="lg">
                <Select label="Column" placeholder="All columns" data={columns} value={chosen ?? null}
                        onChange={(value) => setPicked({id, asked: column, column: value ?? undefined})}
                        clearable searchable comboboxProps={{withinPortal: true, zIndex: 400}}/>
                {error ? <ErrorState error={error}/> : !why ? <Loading/> : (
                    <>
                        <Stack gap="xs">
                            <Fact label="Fact"><Text size="sm" className="df-mono" fz="xs"
                                                     style={{wordBreak: 'break-all'}}>{why.factUniqueId}</Text></Fact>
                            {why.column && <Fact label="Value">{cellText(why.current[why.column])}</Fact>}
                            {why.expression && <Fact label="Expression"><Text size="sm" className="df-mono"
                                                                              fz="xs">{why.expression}</Text></Fact>}
                        </Stack>
                        <Section title="Versions"><Versions versions={why.versions} column={why.column}/></Section>
                        {why.arrivals && <Section title="Arrivals"><Arrivals arrivals={why.arrivals}/></Section>}
                        {why.quarantined && why.quarantined.rows.length > 0 && (
                            <Section title="Quarantined"><DataGrid result={why.quarantined}
                                                                   height={fittedHeight(why.quarantined.rows.length, QUARANTINED_HEIGHT)}/></Section>
                        )}
                        <Section title="Raw row">
                            {why.raw?.rows.length ?
                                <DataGrid result={why.raw} height={fittedHeight(why.raw.rows.length, RAW_HEIGHT)}/>
                                : <Text size="sm" c="dimmed">{why.rawNote}</Text>}
                        </Section>
                        {why.column && (
                            <Section title="Lineage">
                                <Stack gap="snug">
                                    <Fact label="Upstream"><Edges edges={why.upstream} side="from"/></Fact>
                                    <Fact label="Downstream"><Edges edges={why.downstream} side="to"/></Fact>
                                </Stack>
                            </Section>
                        )}
                    </>
                )}
            </Stack>
        </Drawer>
    );
}

function Section({title, children}: { title: string; children: ReactNode }) {
    return (
        <Stack gap="cozy">
            <Title order={4} fz="md">{title}</Title>
            {children}
        </Stack>
    );
}

function index(result: QueryResult, name: string) {
    return result.columns.findIndex((column) => column.name === name);
}

function Versions({versions, column}: { versions: QueryResult; column?: string }) {
    const start = index(versions, '__START_AT');
    const end = index(versions, '__END_AT');
    const value = column ? index(versions, column) : -1;
    return (
        <Table withTableBorder>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>Valid from</Table.Th>
                    <Table.Th>Valid to</Table.Th>
                    {value >= 0 && <Table.Th>{column}</Table.Th>}
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {versions.rows.map((row, position) => {
                    const open = row[end] === null || row[end] === undefined;
                    const changed = value >= 0 && position > 0 && cellText(versions.rows[position - 1][value]) !== cellText(row[value]);
                    return (
                        <Table.Tr key={position}>
                            <Table.Td><Text size="sm"
                                            className="df-num">{formatTime(cellText(row[start]))}</Text></Table.Td>
                            <Table.Td><Text size="sm" className="df-num"
                                            c={open ? 'dimmed' : undefined}>{open ? 'Current' : formatTime(cellText(row[end]))}</Text></Table.Td>
                            {value >= 0 && <Table.Td><Text size="sm"
                                                           fw={changed ? CHANGED_WEIGHT : undefined}>{cellText(row[value])}</Text></Table.Td>}
                        </Table.Tr>
                    );
                })}
            </Table.Tbody>
        </Table>
    );
}

function Arrivals({arrivals}: { arrivals: QueryResult }) {
    const at = index(arrivals, 'processedAt');
    const run = index(arrivals, '_run_id');
    const file = index(arrivals, '_source_file');
    return (
        <Table withTableBorder>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>Processed</Table.Th>
                    <Table.Th>Run</Table.Th>
                    <Table.Th>File</Table.Th>
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {arrivals.rows.map((row, position) => (
                    <Table.Tr key={position}>
                        <Table.Td><Text size="sm" className="df-num">{formatTime(cellText(row[at]))}</Text></Table.Td>
                        <Table.Td>
                            <Anchor component={Link} to={links.run(cellText(row[run]))} size="sm"
                                    className="df-link df-mono" fz="xs">{shortRunId(cellText(row[run]))}</Anchor>
                        </Table.Td>
                        <Table.Td maw={260}><Text size="sm"
                                                  truncate>{cellText(row[file]).replace(/^.*\//, '')}</Text></Table.Td>
                    </Table.Tr>
                ))}
            </Table.Tbody>
        </Table>
    );
}

function Edges({edges, side}: { edges: Lineage['edges']; side: 'from' | 'to' }) {
    if (!edges.length) return <Text size="sm" c="dimmed">None</Text>;
    return (
        <Group gap="tight">
            {edges.map((edge, position) => (
                <Text key={position} size="sm" className="df-mono" fz="xs">
                    {side === 'from' ? `${edge.fromTable}.${edge.fromColumn}` : `${edge.toTable}.${edge.toColumn}`}{position < edges.length - 1 ? ',' : ''}
                </Text>
            ))}
        </Group>
    );
}
