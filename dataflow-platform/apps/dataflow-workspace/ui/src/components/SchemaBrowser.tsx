import {Box, Stack} from '@mantine/core';
import {IconArrowBarToRight, IconColumns, IconDatabase, IconEye, IconTable} from '../icons';
import {useDeferredValue, useMemo, useState} from 'react';
import type {Schema} from '../api/types';
import {ErrorState, Loading} from './ErrorState';
import {shortType} from '../lib/types';
import {PaneSearch} from './layout/PaneSearch';
import {type TreeNode, TreeView, useTreeState} from './TreeView';
import {ICON_SMALL, ICON_BIG} from '../lib/icons';
import {IconAction} from './actions/IconAction';

const ICON = {size: 15, stroke: 1.6};

export function SchemaBrowser({schema, error, onInsert, onPreview}: {
    schema?: Schema;
    error?: unknown;
    onInsert: (text: string) => void;
    onPreview?: (table: string) => void;
}) {
    const {nodes, inserts} = useMemo(() => {
        const texts = new Map<string, string>();
        const databases = new Map<string, TreeNode>();
        Object.entries(schema ?? {}).sort(([a], [b]) => a.localeCompare(b)).forEach(([qualified, columns]) => {
            const [database, table] = qualified.split('.');
            if (!databases.has(database)) {
                texts.set(database, database);
                databases.set(database, {
                    id: database, label: database, icon: <IconDatabase {...ICON} />, children: [],
                    actions: <Insert onInsert={() => onInsert(database)}/>,
                });
            }
            texts.set(qualified, qualified);
            databases.get(database)!.children!.push({
                id: qualified, label: table, icon: <IconTable {...ICON} />,
                actions: <>{onPreview && <Preview onPreview={() => onPreview(qualified)}/>}<Insert
                    onInsert={() => onInsert(qualified)}/></>,
                children: columns.map((column) => {
                    const id = `${qualified}.${column.name}`;
                    texts.set(id, column.name);
                    return {
                        id,
                        label: column.name,
                        icon: <IconColumns {...ICON_SMALL}/>,
                        badge: shortType(column.type),
                        actions: <Insert onInsert={() => onInsert(column.name)}/>,
                    };
                }),
            });
        });
        return {nodes: [...databases.values()], inserts: texts};
    }, [schema, onInsert, onPreview]);

    if (!schema) return error ? <Box p="sm"><ErrorState error={error}/></Box> : <Loading/>;
    return <BrowserTree storageKey="df.sql.schema" initial={['serving']} nodes={nodes} inserts={inserts} onInsert={onInsert}/>;
}

export function BrowserTree({storageKey, initial, nodes, inserts, onInsert}: {
    storageKey: string;
    initial?: string[];
    nodes: TreeNode[];
    inserts: Map<string, string>;
    onInsert: (text: string) => void;
}) {
    const [filter, setFilter] = useState('');
    const deferred = useDeferredValue(filter);
    const {expanded, toggle} = useTreeState(storageKey, initial);
    return (
        <Stack gap={0} h="100%">
            <PaneSearch value={filter} onChange={setFilter}/>
            <Box style={{flex: 1, minHeight: 0}}>
                <TreeView nodes={nodes} expanded={expanded} onToggle={toggle} filter={deferred} empty="No tables"
                          onOpen={(node) => onInsert(inserts.get(node.id) ?? node.label)}/>
            </Box>
        </Stack>
    );
}

function Preview({onPreview}: { onPreview: () => void }) {
    return (
        <IconAction {...ICON_BIG} label="Sample rows" icon={IconEye} onClick={onPreview}/>
    );
}

export function Insert({onInsert}: { onInsert: () => void }) {
    return (
        <IconAction {...ICON_BIG} label="Insert" icon={IconArrowBarToRight} onClick={onInsert}/>
    );
}
