import {Anchor, Box, Group, ScrollArea, Table, Text, Tooltip} from '@mantine/core';
import {IconDatabase, IconFolder, IconRefresh} from '../icons';
import {useDeferredValue, useMemo, useState} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {useCatalog, useCatalogUsage, useSyncCatalog} from '../api/hooks';
import type {CatalogTree, LakeTable, ServedTable} from '../api/types';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {IconAction} from '../components/actions/IconAction';
import {Pane, PaneLayout, PaneMain} from '../components/layout/Pane';
import {PaneSearch} from '../components/layout/PaneSearch';
import {type TreeNode, TreeView, useTreeState} from '../components/TreeView';
import {formatAgo, formatNumber, formatTime} from '../lib/format';
import {layer, type LayerStyle, tableStyle} from '../lib/layers';
import {links} from '../lib/links';
import {ServedTableView} from './catalog/ServedTableView';
import {TableView} from './catalog/TableView';
import {ICON, ICON_LARGE} from '../lib/icons';
import {NumberCell} from '../components/NumberCell';
import {COLOR} from '../lib/colors';
import {notifyFailure} from '../components/notify';

const UNWRITTEN_OPACITY = 0.5;

export function CatalogPage() {
    const navigate = useNavigate();
    const {dataset, table, served} = useParams();
    const {data, error, isLoading} = useCatalog();
    const sync = useSyncCatalog();
    const [filter, setFilter] = useState('');
    const deferred = useDeferredValue(filter);
    const {expanded, toggle} = useTreeState('df.catalog.tree', ['lake'],
        dataset ? ['lake', `lake/${dataset}`] : served ? ['serving'] : []);
    const selected = dataset && table ? `lake/${dataset}/${table}` : served ? `serving/${served}` : undefined;
    const nodes = useMemo(() => (data ? catalogNodes(data) : []), [data]);

    const side = (
        <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
            <PaneSearch value={filter} onChange={setFilter}/>
            <Box style={{flex: 1, minHeight: 0}}>
                {isLoading ? <Loading/> : error ? <ErrorState error={error}/>
                    :
                    <TreeView nodes={nodes} expanded={expanded} onToggle={toggle} selected={selected} filter={deferred}
                              empty="No tables"
                              onSelect={(node) => {
                                  const [root, first, second] = node.id.split('/');
                                  if (root === 'lake' && first && second) navigate(links.table(first, second));
                                  if (root === 'serving' && first) navigate(links.served(first));
                              }}/>}
            </Box>
        </div>
    );

    return (
        <Box h="100%">
            <PaneLayout>
                <Pane id="catalog.tree" side="left" title="Catalog" icon={IconDatabase} defaultSize={280} min={200} max={480}
                      actions={<IconAction label="Refresh" icon={IconRefresh} loading={sync.isPending}
                                           onClick={() => sync.mutate(undefined, {
                                               onError: notifyFailure(),
                                           })}/>}>
                    {side}
                </Pane>
                <PaneMain>
                <ScrollArea h="100%">
                    <Box px="xl" py="lg" maw={CONTENT_WIDTH}>
                        {dataset && table ? <TableView key={selected} dataset={dataset} tableId={table}/>
                            : served ? <ServedTableView key={selected} table={served}/>
                                : data ? <Overview tree={data} filter={deferred}/> : null}
                    </Box>
                </ScrollArea>
                </PaneMain>
            </PaneLayout>
        </Box>
    );
}

const CONTENT_WIDTH = 1400;
const TREE_ICON = ICON;

function matches(filter: string, ...names: string[]) {
    const wanted = filter.trim().toLowerCase();
    return !wanted || names.some((name) => name.toLowerCase().includes(wanted));
}

function catalogNodes(tree: CatalogTree): TreeNode[] {
    return [
        {
            id: 'lake', label: 'lake', icon: <IconDatabase {...TREE_ICON} />,
            children: tree.datasets.map((entry) => ({
                id: `lake/${entry.dataset}`, label: entry.dataset, icon: <IconFolder {...TREE_ICON} />,
                children: entry.tables.map((table: LakeTable) => {
                    const Icon = tableStyle(table.id, table.layer).icon;
                    return {
                        id: `lake/${entry.dataset}/${table.id}`,
                        label: table.label,
                        icon: <Icon {...TREE_ICON} />,
                        dimmed: table.delta && !table.exists
                    };
                }),
            })),
        },
        {
            id: 'serving', label: 'serving', icon: <IconDatabase {...TREE_ICON} />,
            children: tree.serving.map((table: ServedTable) => {
                const Icon = servedIcon(table);
                return {
                    id: `serving/${table.table}`,
                    label: table.table,
                    icon: <Icon {...TREE_ICON} />,
                    dimmed: !table.exists
                };
            }),
        },
    ];
}

const servedIcon = (table: ServedTable) => (table.mode === 'view' ? layer('current').icon : layer('serving').icon);

type Row = {
    key: string;
    name: string;
    location: string;
    pipeline: string;
    type: string;
    to: string;
    written: boolean;
    updatedAt?: string | null;
    icon: LayerStyle['icon'];
};

function Overview({tree, filter}: { tree: CatalogTree; filter: string }) {
    const {data: usage} = useCatalogUsage();
    const queries = useMemo(() => new Map((usage ?? []).map((entry) => [entry.table, entry.queries])), [usage]);
    const rows = useMemo<Row[]>(() => [
        ...tree.datasets.flatMap((entry) => entry.tables.map((table) => ({
            key: `${entry.dataset}/${table.id}`,
            name: table.label,
            location: table.sqlName ?? `lake/${table.key}`,
            pipeline: entry.dataset,
            type: !table.delta ? 'Files' : table.id === 'current' ? 'View' : 'Table',
            to: links.table(entry.dataset, table.id),
            written: table.exists || !table.delta,
            updatedAt: table.updatedAt,
            icon: tableStyle(table.id, table.layer).icon,
        }))),
        ...tree.serving.map((table) => ({
            key: `serving/${table.table}`,
            name: table.table,
            location: table.sqlName,
            pipeline: table.dataset,
            type: table.mode === 'copy' ? 'Table' : 'View',
            to: links.served(table.table),
            written: table.exists,
            icon: servedIcon(table),
        })),
    ], [tree]);
    const shown = rows.filter((row) => matches(filter, row.name, row.location, row.pipeline));

    return (
        <>
            <PageHeader title="Catalog"/>
            {!shown.length ? <Empty title="No table matches"/> : (
                <Panel flush>
                    <Table>
                        <Table.Thead>
                            <Table.Tr>
                                <Table.Th>Name</Table.Th>
                                <Table.Th>SQL name</Table.Th>
                                <Table.Th>Pipeline</Table.Th>
                                <Table.Th w={120}>Type</Table.Th>
                                <Table.Th w={130} ta="right">Queries (30 d)</Table.Th>
                                <Table.Th w={160}>Updated</Table.Th>
                            </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {shown.map((row) => (
                                <Table.Tr key={row.key} style={{opacity: row.written ? 1 : UNWRITTEN_OPACITY}}>
                                    <Table.Td>
                                        <Group gap="sm" wrap="nowrap">
                                            <row.icon {...ICON_LARGE} color={COLOR.dimmed}
                                                      style={{flex: 'none'}}/>
                                            <Anchor component={Link} to={row.to} size="sm"
                                                    className="df-link">{row.name}</Anchor>
                                        </Group>
                                    </Table.Td>
                                    <Table.Td><Text size="xs" c="dimmed" className="df-mono">{row.location}</Text></Table.Td>
                                    <Table.Td><Text size="sm">{row.pipeline}</Text></Table.Td>
                                    <Table.Td><Text size="sm">{row.type}</Text></Table.Td>
                                    <NumberCell color={queries.get(row.location) ? undefined : 'dimmed'}>{queries.get(row.location) ? formatNumber(queries.get(row.location)!) : '—'}</NumberCell>
                                    <Table.Td>
                                        {row.updatedAt ? (
                                            <Tooltip label={formatTime(row.updatedAt)}><Text size="sm" c="dimmed"
                                                                                             w="fit-content">{formatAgo(row.updatedAt)}</Text></Tooltip>
                                        ) : <Text size="sm" c="dimmed">—</Text>}
                                    </Table.Td>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                </Panel>
            )}
        </>
    );
}
