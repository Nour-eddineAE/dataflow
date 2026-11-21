import {Anchor, Box, Breadcrumbs, Group, Menu, ScrollArea, Table, Text} from '@mantine/core';
import {IconDots, IconFolders, IconTrash} from '../icons';
import {useDeferredValue, useMemo, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useDeleteFolder, useFolders, useFolderTrees, useMe} from '../api/hooks';
import type {FolderTree} from '../api/types';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {Pane, PaneLayout, PaneMain} from '../components/layout/Pane';
import {PaneSearch} from '../components/layout/PaneSearch';
import {Toolbar, ToolbarGroup} from '../components/layout/Toolbar';
import {type TreeNode, TreeView, useTreeState} from '../components/TreeView';
import {ancestors, crumbsOf, findItem, type Item, listingColumns, owningFolder, parentOf, workspaceItems} from '../lib/workspace';
import {type CreateKind, CreateMenu} from './workspace/CreateMenu';
import {FileEditor} from './workspace/FileEditor';
import {GitActions} from './workspace/GitActions';
import {itemIcon, itemType, StateMark} from './workspace/icons';
import {ICON_SMALL} from '../lib/icons';
import {PIPELINE_FILE} from '../lib/names';
import {IconAction} from '../components/actions/IconAction';
import {PAGE} from '../lib/links';
import {pageName} from '../lib/navigation';
import {COLOR} from '../lib/colors';
import {plural} from '../lib/format';
import {notifyDone, notifyFailure} from '../components/notify';
import {FormModal} from '../components/FormModal';

export function WorkspacePage() {
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const me = useMe();
    const folders = useFolders();
    const [filter, setFilter] = useState('');
    const deferred = useDeferredValue(filter);
    const home = me.data ? `Users/${me.data.id}` : undefined;
    const at = params.get('at') ?? home ?? '';
    const create = (params.get('create') as CreateKind | null) ?? undefined;
    const {expanded, toggle} = useTreeState('df.workspace.tree', [], at ? ancestors(at) : []);

    const owner = owningFolder(folders.data ?? [], at);
    const loadIds = useMemo(() => (folders.data ?? [])
        .filter((folder) => expanded.has(folder.path) || folder.id === owner?.id || !!deferred.trim())
        .map((folder) => folder.id), [folders.data, expanded, owner?.id, deferred]);
    const treeQueries = useFolderTrees(loadIds);
    const trees = useMemo(() => {
        const loaded: Record<string, FolderTree | undefined> = {};
        loadIds.forEach((id, index) => {
            loaded[id] = treeQueries[index]?.data;
        });
        return loaded;
    }, [loadIds, treeQueries]);

    const items = useMemo(() => (me.data && folders.data ? workspaceItems(me.data.id, folders.data, trees, expanded) : []), [me.data, folders.data, trees, expanded]);
    const nodes = useMemo(() => items.map(toNode), [items]);
    const current: Item | undefined = at ? findItem(items, at) : {
        path: '',
        name: 'Workspace',
        kind: 'dir',
        children: items
    };

    const [folding, setFolding] = useState<{ at: string; collapsed: boolean }>();
    const treeFolded = folding?.at === at ? folding.collapsed : undefined;

    const go = (path: string, replace = false) => setParams({at: path}, {replace});
    const designing = current?.kind === 'file' && !!current.file?.endsWith(PIPELINE_FILE);
    const treeFolding = designing ? {collapsed: treeFolded ?? true, onCollapsedChange: (collapsed: boolean) => setFolding({at, collapsed})} : {};
    const ownerTree = owner ? trees[owner.id] : undefined;

    if (me.error || folders.error) return <Box p="xl"><ErrorState error={me.error ?? folders.error}/></Box>;
    if (!me.data || !folders.data) return <Loading/>;

    const side = (
        <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
            <PaneSearch value={filter} onChange={setFilter}/>
            <Box style={{flex: 1, minHeight: 0}}>
                <TreeView nodes={nodes} expanded={expanded} onToggle={toggle} selected={at} filter={deferred}
                          onSelect={(node) => go(node.id)} empty="Nothing found"/>
            </Box>
        </div>
    );

    return (
        <Box h="100%">
            <PaneLayout>
                <Pane id="workspace.tree" side="left" title="Workspace" icon={IconFolders} defaultSize={300} min={220} max={520}
                      {...treeFolding}>
                    {side}
                </Pane>
                <PaneMain title={pageName(PAGE.workspace)}>
                    <Toolbar>
                        <ToolbarGroup grow><PathCrumbs path={at} me={me.data?.id ?? ''} onGo={go}/></ToolbarGroup>
                        <ToolbarGroup>
                            {ownerTree && <GitActions tree={ownerTree}/>}
                            <CreateMenu folder={owner} primary={current?.kind !== 'file'}
                                        within={current?.kind === 'dir' ? current.file : current?.kind === 'file' ? parentOf(current.file ?? '') || undefined : undefined}
                                        shared={at.startsWith('Shared')} request={create}
                                        onDone={() => params.has('create') && setParams({at}, {replace: true})}
                                        onCreated={(path) => go(path)}/>
                        </ToolbarGroup>
                    </Toolbar>
                    <Box style={{flex: 1, minHeight: 0}}>
                        {current?.kind === 'file' && current.folder && current.file
                            ? <FileEditor key={`${current.folder.id}/${current.folder.branch}/${current.file}`} folderId={current.folder.id}
                                          file={current.file}
                                          onClosed={() => go(parentOf(at), true)}/>
                            : current ? <Listing item={current} onOpen={(item) => go(item.path)}
                                                 onRemoved={() => go(parentOf(at) || home!, true)}/>
                                : owner && !ownerTree ? <Loading/>
                                    : <Empty title="Not found" action={<Anchor size="sm"
                                                                               onClick={() => navigate(PAGE.workspace)}>Home</Anchor>}/>}
                    </Box>
                </PaneMain>
            </PaneLayout>
        </Box>
    );
}

function toNode(item: Item): TreeNode {
    return {
        id: item.path,
        label: item.name,
        icon: itemIcon(item),
        children: item.kind === 'file' ? undefined : (item.children ?? []).map(toNode),
        loading: item.loading,
        strike: item.state === 'DELETED',
        badge: item.kind === 'file' ? <StateMark state={item.state}/>
            : item.kind === 'git' && item.folder?.drafts ?
                <Text span size="xs" c={COLOR.warning}>{item.folder.drafts}</Text> : undefined,
    };
}

const CRUMBS_SHOWN = 4;

function PathCrumbs({path, me, onGo}: { path: string; me: string; onGo: (path: string) => void }) {
    const crumbs = crumbsOf(path, me);
    const hidden = Math.max(0, crumbs.length - CRUMBS_SHOWN);
    return (
        <Breadcrumbs separator="/" separatorMargin="snug" title={path} style={{flexWrap: 'nowrap', whiteSpace: 'nowrap', minWidth: 0}}>
            <Anchor size="sm" c="dimmed" onClick={() => onGo('')}>Workspace</Anchor>
            {hidden > 0 && <Anchor size="sm" c="dimmed" aria-label={crumbs.slice(0, hidden).map((crumb) => crumb.label).join(' / ')}
                                   onClick={() => onGo(crumbs[hidden - 1].path)}>…</Anchor>}
            {crumbs.slice(hidden).map((crumb, offset) => (hidden + offset === crumbs.length - 1
                ? <Text key={crumb.path} size="sm" fw={600} truncate>{crumb.label}</Text>
                : <Anchor key={crumb.path} size="sm" c="dimmed" onClick={() => onGo(crumb.path)}>{crumb.label}</Anchor>))}
        </Breadcrumbs>
    );
}

function Listing({item, onOpen, onRemoved}: { item: Item; onOpen: (item: Item) => void; onRemoved: () => void }) {
    const remove = useDeleteFolder();
    const [removing, setRemoving] = useState<Item>();
    const children = item.children ?? [];
    const confirmRemove = () => {
        const target = removing;
        if (!target?.folder) return;
        remove.mutate(target.folder.id, {
            onSuccess: () => {
                setRemoving(undefined);
                notifyDone({message: `${target.name} removed`});
                if (item.kind === 'git') onRemoved();
            },
            onError: notifyFailure('Not removed'),
        });
    };
    const columns = listingColumns(children);
    if (item.loading) return <Loading/>;
    if (!children.length) return <Empty title="Empty"/>;
    return (
        <ScrollArea h="100%">
            <Table className="df-listing" stickyHeader>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th>Name</Table.Th>
                        <Table.Th w={140}>Type</Table.Th>
                        {columns.branch && <Table.Th w={160}>Branch</Table.Th>}
                        {columns.state && <Table.Th w={120}>State</Table.Th>}
                        <Table.Th w={44}/>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {children.map((child) => (
                        <Table.Tr key={child.path} className="df-row-link" onClick={() => onOpen(child)}>
                            <Table.Td>
                                <Group gap="cozy" wrap="nowrap">
                                    <Box c="dimmed" style={{display: 'flex'}}>{itemIcon(child)}</Box>
                                    <Text size="sm" truncate
                                          td={child.state === 'DELETED' ? 'line-through' : undefined}>{child.name}</Text>
                                </Group>
                            </Table.Td>
                            <Table.Td><Text size="sm" c="dimmed">{itemType(child)}</Text></Table.Td>
                            {columns.branch && (
                                <Table.Td><Text size="sm" c="dimmed">{child.kind === 'git' ? child.folder?.branch : ''}</Text></Table.Td>
                            )}
                            {columns.state && (
                                <Table.Td>
                                    {child.kind === 'file' ? <StateMark state={child.state} word/>
                                        : child.kind === 'git' && child.folder?.drafts
                                            ? <Text size="sm" c={COLOR.warning}>{plural(child.folder.drafts, 'draft')}</Text> : null}
                                </Table.Td>
                            )}
                            <Table.Td onClick={(event) => event.stopPropagation()}>
                                {child.kind === 'git' && child.folder && (
                                    <Menu position="bottom-end" width={160}>
                                        <Menu.Target><IconAction label="Actions" icon={IconDots}/></Menu.Target>
                                        <Menu.Dropdown>
                                            <Menu.Item color={COLOR.danger} leftSection={<IconTrash {...ICON_SMALL}/>}
                                                       onClick={() => setRemoving(child)}>Remove</Menu.Item>
                                        </Menu.Dropdown>
                                    </Menu>
                                )}
                            </Table.Td>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
            <FormModal opened={!!removing} onClose={() => setRemoving(undefined)} title={`Remove ${removing?.name ?? ''}?`}
                       submit="Remove" danger loading={remove.isPending} onSubmit={confirmRemove}>
                <Text size="sm">
                    {removing?.folder?.drafts
                        ? `Its ${plural(removing.folder.drafts, 'draft')} and any unpushed commits will be lost.`
                        : 'Any unpushed commits will be lost.'}
                </Text>
            </FormModal>
        </ScrollArea>
    );
}
