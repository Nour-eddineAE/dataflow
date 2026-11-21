import {Anchor, Box, Grid, Group, Progress, Stack, Table, Tabs, Text, Tooltip} from '@mantine/core';
import {IconDownload, IconRoute} from '../../icons';
import {IconAction} from '../../components/actions/IconAction';
import {useMemo, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {
    useLanding,
    useLineage,
    useProfile,
    useQuarantineReasons,
    useSample,
    useTable
} from '../../api/hooks';
import type {TableDetail} from '../../api/types';
import {DataGrid} from '../../components/DataGrid';
import {Fact} from '../../components/Fact';
import {Empty, ErrorState, Loading} from '../../components/ErrorState';
import {LineageGraph} from '../../components/LineageGraph';
import {ProfileTable, BAR_HEIGHT} from '../../components/ProfileTable';
import {Panel} from '../../components/Panel';
import {WhyDrawer} from '../../components/WhyDrawer';
import {downloadCsv} from '../../lib/csv';
import {formatAgo, formatBytes, formatNumber} from '../../lib/format';
import {layer, tableStyle} from '../../lib/layers';
import {links, PAGE} from '../../lib/links';
import {useRemember} from '../../lib/recents';
import {shortType} from '../../lib/types';
import {DetailsTab} from './DetailsTab';
import {TableHeader} from './TableHeader';
import {HistoryTab} from './HistoryTab';
import {RescuedKeys} from './RescuedKeys';

import {Action} from '../../components/actions/Action';
import {COLOR} from '../../lib/colors';
import {ICON_PAGE} from '../../lib/icons';
import {useRemainingHeight} from '../../components/layout/useRemainingHeight';
import {focusOn} from '../../lib/lineageFocus';

const TRACEABLE = ['silver', 'history', 'current'];
const LINEAGE_ID: Record<string, string> = {history: 'current'};

export function TableView({dataset, tableId}: { dataset: string; tableId: string }) {
    const [params, setParams] = useSearchParams();
    const tab = params.get('tab') ?? 'overview';
    const {data: detail, error, isLoading} = useTable(dataset, tableId);
    useRemember(detail && {
        to: links.table(dataset, tableId),
        label: detail.table.sqlName ?? `${dataset}.${detail.table.label}`,
        kind: 'table'
    });

    const crumbs = (label: string) => [{label: 'Catalog', to: PAGE.catalog}, {label: dataset, to: links.pipeline(dataset)}, {label}];
    const favorite = {kind: 'TABLE', reference: `${dataset}.${tableId}`} as const;

    if (isLoading) return <Loading/>;
    if (error || !detail) {
        const Unknown = layer().icon;
        return (
            <>
                <TableHeader crumbs={crumbs(tableId)} icon={<Unknown {...ICON_PAGE}/>} name={`${dataset}.${tableId}`} exists={false}
                             favorite={favorite}/>
                <ErrorState error={error ?? 'Table not found'}/>
            </>
        );
    }

    const table = detail.table;
    const Icon = tableStyle(table.id, table.layer).icon;
    const name = table.sqlName ?? `${dataset}.${table.label}`;

    return (
        <>
            <TableHeader crumbs={crumbs(table.label)} icon={<Icon {...ICON_PAGE}/>} name={name} sqlName={table.sqlName}
                         exists={table.exists} favorite={favorite}/>
            {!table.delta ? <LandingFiles dataset={dataset}/>
                : !table.exists ? <Empty title="Not written yet"/>
                    : (
                        <Tabs value={tab} keepMounted={false}
                              onChange={(value) => setParams({tab: value ?? 'overview'}, {replace: true})}>
                            <Tabs.List mb="lg">
                                <Tabs.Tab value="overview">Overview</Tabs.Tab>
                                <Tabs.Tab value="sample">Sample data</Tabs.Tab>
                                <Tabs.Tab value="profile">Profile</Tabs.Tab>
                                <Tabs.Tab value="details">Details</Tabs.Tab>
                                <Tabs.Tab value="history">History</Tabs.Tab>
                                <Tabs.Tab value="lineage">Lineage</Tabs.Tab>
                            </Tabs.List>
                            <Tabs.Panel value="overview"><Overview dataset={dataset} detail={detail}/></Tabs.Panel>
                            <Tabs.Panel value="sample"><Sample dataset={dataset} tableId={tableId}/></Tabs.Panel>
                            <Tabs.Panel value="profile"><Profile dataset={dataset} tableId={tableId} sqlName={table.sqlName}/></Tabs.Panel>
                            <Tabs.Panel value="details"><DetailsTab dataset={dataset} tableId={tableId}
                                                                    location={`s3a://lake/${table.key}`}/></Tabs.Panel>
                            <Tabs.Panel value="history"><HistoryTab dataset={dataset} tableId={tableId}
                                                                    sqlName={table.sqlName}
                                                                    changeFeed={table.layer === 'history'}/></Tabs.Panel>
                            <Tabs.Panel value="lineage"><TableLineage dataset={dataset}
                                                                      tableId={LINEAGE_ID[tableId] ?? tableId}/></Tabs.Panel>
                        </Tabs>
                    )}
        </>
    );
}

function Overview({dataset, detail}: { dataset: string; detail: TableDetail }) {
    return (
        <Grid gap="lg">
            <Grid.Col span={{base: 12, lg: 8}}>
                <Panel title="Columns" flush>
                    <ColumnsTable detail={detail}/>
                </Panel>
            </Grid.Col>
            <Grid.Col span={{base: 12, lg: 4}}>
                <Panel title="About">
                    <Stack gap="sm">
                        <Fact label="Pipeline"><Anchor component={Link} to={links.pipeline(dataset)} size="sm"
                                                       className="df-link">{dataset}</Anchor></Fact>
                        <Fact label="Layer">{layer(detail.table.layer).title}</Fact>
                        <Fact label="Format">Delta</Fact>
                        <Fact label="Rows">{formatNumber(detail.rows)}</Fact>
                        <Fact label="Size">{formatBytes(detail.bytes)}</Fact>
                        <Fact label="Files">{formatNumber(detail.files)}</Fact>
                        <Fact label="Version">{detail.lastCommit ? formatNumber(detail.lastCommit.version) : '—'}</Fact>
                        <Fact
                            label="Last updated">{detail.lastCommit ? formatAgo(detail.lastCommit.timestamp) : '—'}</Fact>
                        <Fact label="Location"><Text size="sm"
                                                     style={{wordBreak: 'break-all'}}>s3a://lake/{detail.table.key}</Text></Fact>
                    </Stack>
                </Panel>
                {detail.table.id === 'quarantine' && <BrokenRules dataset={dataset} rows={detail.rows ?? 0}/>}
                {detail.table.id === 'bronze' && <RescuedKeys dataset={dataset}/>}
            </Grid.Col>
        </Grid>
    );
}

function BrokenRules({dataset, rows}: { dataset: string; rows: number }) {
    const {data} = useQuarantineReasons(dataset, rows > 0);
    if (!data?.length) return null;
    return (
        <Box mt="lg">
            <Panel title="Rules broken">
                <Stack gap="sm">
                    {data.map((reason) => (
                        <Stack key={reason.rule} gap="tight">
                            <Group justify="space-between" wrap="nowrap">
                                <Text size="sm" truncate>{reason.rule}</Text>
                                <Text size="sm" c="dimmed" className="df-num">{formatNumber(reason.rows)}</Text>
                            </Group>
                            <Progress value={(reason.rows / rows) * 100} size={BAR_HEIGHT} color={COLOR.danger}
                                      aria-label={reason.rule}/>
                        </Stack>
                    ))}
                </Stack>
            </Panel>
        </Box>
    );
}

function ColumnsTable({detail}: { detail: TableDetail }) {
    return (
        <Table.ScrollContainer minWidth={560}>
            <Table>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th>Name</Table.Th>
                        <Table.Th>Type</Table.Th>
                        <Table.Th>Source</Table.Th>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {detail.columns.map((column) => (
                        <Table.Tr key={column.name}>
                            <Table.Td><Text size="sm">{column.name}</Text></Table.Td>
                            <Table.Td><Text size="sm"
                                            c="dimmed">{column.type ? shortType(column.type) : '—'}</Text></Table.Td>
                            <Table.Td maw={360}>
                                {column.expression ? (
                                    <Tooltip label={column.expression} multiline maw={420}>
                                        <Text size="sm" c="dimmed" truncate className="df-mono"
                                              fz="xs">{column.expression}</Text>
                                    </Tooltip>
                                ) : <Text size="sm" c="dimmed">{column.description ?? ''}</Text>}
                            </Table.Td>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </Table.ScrollContainer>
    );
}

function Sample({dataset, tableId}: { dataset: string; tableId: string }) {
    const {data, error, isLoading} = useSample(dataset, tableId, true);
    const [fact, setFact] = useState<string>();
    const factIndex = data?.columns.findIndex((column) => column.name === 'factUniqueId') ?? -1;
    const traceable = TRACEABLE.includes(tableId) && factIndex >= 0;

    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data) return null;
    return (
        <>
            <Group justify="flex-end" mb="snug" mt={-6}>
                <Action size="sm" tone="subtle" icon={IconDownload} label="Download"
                        onClick={() => downloadCsv(`${dataset}_${tableId}_sample`, data)}/>
            </Group>
            <DataGrid result={data} height="calc(100vh - 320px)"
                      onRowClick={traceable ? (row) => setFact(String(row[factIndex])) : undefined}
                      rowAction={traceable ? (row) => (
                          <IconAction label="Trace this row" icon={IconRoute} onClick={() => setFact(String(row[factIndex]))}/>
                      ) : undefined}/>
            {traceable && <WhyDrawer dataset={dataset} id={fact} onClose={() => setFact(undefined)}/>}
        </>
    );
}

function Profile({dataset, tableId, sqlName}: { dataset: string; tableId: string; sqlName?: string | null }) {
    const {data, error, isLoading} = useProfile(dataset, tableId);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    return data ? <ProfileTable profile={data} sqlName={sqlName}/> : null;
}

function LandingFiles({dataset}: { dataset: string }) {
    const {data, error, isLoading} = useLanding(dataset, true);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data?.rows.length) return <Empty title="No files"/>;
    return <DataGrid result={data} height="calc(100vh - 240px)"/>;
}

function TableLineage({dataset, tableId}: { dataset: string; tableId: string }) {
    const [fill, height] = useRemainingHeight();
    const {data, error, isLoading} = useLineage(dataset);
    const focused = useMemo(() => (data ? focusOn(data, tableId) : undefined), [data, tableId]);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!focused?.lineage.tables.length) return <Empty title="No lineage"/>;
    return (
        <div ref={fill}>
            <Panel flush framed>
                <LineageGraph lineage={focused.lineage} hidden={focused.hidden} focusTable={tableId} height={height}/>
            </Panel>
        </div>
    );
}

