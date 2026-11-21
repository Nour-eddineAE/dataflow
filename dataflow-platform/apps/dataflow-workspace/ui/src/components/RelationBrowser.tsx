import {Box} from '@mantine/core';
import {useMemo} from 'react';
import {useSchema} from '../api/hooks';
import {IconColumns, IconTable} from '../icons';
import {relationTables} from '../lib/goldColumns';
import {ICON, ICON_SMALL} from '../lib/icons';
import type {Spec} from '../lib/specModel';
import {ErrorState, Loading} from './ErrorState';
import {BrowserTree, Insert} from './SchemaBrowser';
import type {TreeNode} from './TreeView';

export function RelationBrowser({spec, except, error, onInsert}: {
    spec?: Spec;
    except?: string;
    error?: unknown;
    onInsert: (text: string) => void;
}) {
    const catalog = useSchema();
    const {nodes, inserts} = useMemo(() => {
        const texts = new Map<string, string>();
        const tables = spec ? relationTables(spec, catalog.data, except) : [];
        const tree = tables.map(({name, columns}): TreeNode => {
            texts.set(name, name);
            return {
                id: name,
                label: name,
                icon: <IconTable {...ICON}/>,
                actions: <Insert onInsert={() => onInsert(name)}/>,
                children: columns.map((column) => {
                    const id = `${name}.${column}`;
                    texts.set(id, column);
                    return {
                        id,
                        label: column,
                        icon: <IconColumns {...ICON_SMALL}/>,
                        actions: <Insert onInsert={() => onInsert(column)}/>,
                    };
                }),
            };
        });
        return {nodes: tree, inserts: texts};
    }, [spec, catalog.data, except, onInsert]);

    if (!spec) return error ? <Box p="sm"><ErrorState error={error}/></Box> : <Loading/>;
    return <BrowserTree storageKey="df.spec.tables" nodes={nodes} inserts={inserts} onInsert={onInsert}/>;
}
