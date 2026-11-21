import {Tooltip, UnstyledButton} from '@mantine/core';
import {IconComponents, IconTable} from '../../../icons';
import {ReactFlowProvider} from '@xyflow/react';
import {ICON} from '../../../lib/icons';
import '@xyflow/react/dist/style.css';
import {useMemo, useState} from 'react';
import {useSpecTree, useCatalog} from '../../../api/hooks';
import type {Problem} from '../../../api/types';
import {ErrorState, Loading} from '../../../components/ErrorState';
import {Pane, PaneLayout, PaneMain} from '../../../components/layout/Pane';
import {
    follow,
    type Positions,
    renamedIds,
    withoutNode,
} from '../../../lib/designerPositions';
import {
    addBlock,
    type Block,
    designGraph,
    type DesignNode,
    duplicateNode,
    moveStep,
    nodeOfProblem,
    PIPELINE,
    removeNode,
    type Spec, addInputTable} from '../../../lib/specModel';
import {writeSpec} from '../../../lib/specYaml';
import {palette} from './blocks';
import {Canvas, DRAG_TABLE, DRAG_TYPE} from './Canvas';
import {DesignerTools} from './DesignerTools';
import {Inspector} from './Inspector';
import type {Issue} from './ProblemsPane';
import './Designer.css';

const READABLE = /^(current|gold\.|reference\.)/;

export type DesignDocument = { text: string; spec?: Spec; positions: Positions };

const ROOMY = 1500;

type Props = {
    dataset: string;
    folderId: string;
    document: DesignDocument;
    problems: Problem[];
    warnings: string[];
    checking: boolean;
    onChange: (document: DesignDocument) => void;
};

export function PipelineDesigner(props: Props) {
    return (
        <ReactFlowProvider>
            <Designer {...props}/>
        </ReactFlowProvider>
    );
}

function Designer({dataset, folderId, document, problems, warnings, checking, onChange}: Props) {
    const parsed = useSpecTree(document.text, document.spec === undefined);
    const spec = useMemo(() => {
        const tree = document.spec ?? parsed.data;
        return tree ? {...tree, dataset: tree.dataset ?? dataset} : undefined;
    }, [document.spec, parsed.data, dataset]);
    const [selected, setSelected] = useState(PIPELINE);

    const byNode = useMemo(() => {
        const found = new Map<string, string[]>();
        if (!spec) return found;
        const written = writeSpec(spec as Record<string, unknown>);
        const canonical = written.text === document.text;
        for (const problem of problems) {
            const path = canonical && problem.line ? written.paths[problem.line - 1] : undefined;
            const id = nodeOfProblem(spec, problem.message, path);
            found.set(id, [...(found.get(id) ?? []), problem.message]);
        }
        return found;
    }, [problems, spec, document.text]);

    const issues = useMemo<Issue[]>(() => {
        if (!spec) return [];
        const nodes = new Map(designGraph(spec).nodes.map((node) => [node.id, node]));
        const pipeline: DesignNode = {id: PIPELINE, kind: 'pipeline', label: spec.dataset ?? dataset, detail: ''};
        const nodeOf = (id: string) => nodes.get(id) ?? pipeline;
        return [
            ...[...byNode.entries()].flatMap(([id, messages]) => messages.map((message) => ({severity: 'error' as const, message, node: nodeOf(id)}))),
            ...warnings.map((message) => ({severity: 'warning' as const, message, node: nodeOf(nodeOfProblem(spec, message))})),
        ];
    }, [spec, byNode, warnings, dataset]);

    if (parsed.error && !document.spec) return <ErrorState error={parsed.error}/>;
    if (!spec) return <Loading/>;

    const graph = designGraph(spec);
    const edit = (next: Spec, positions = document.positions) => {
        const renamed = renamedIds(graph.nodes, designGraph(next).nodes);
        if (renamed.has(selected)) setSelected(renamed.get(selected)!);
        onChange({text: writeSpec(next as Record<string, unknown>).text, spec: next, positions: follow(positions, renamed)});
    };
    const pipeline: DesignNode = {id: PIPELINE, kind: 'pipeline', label: spec.dataset ?? dataset, detail: ''};
    const current = graph.nodes.find((node) => node.id === selected) ?? pipeline;
    const remove = (node: DesignNode) => {
        edit(removeNode(spec, node), withoutNode(document.positions, node));
        setSelected(PIPELINE);
    };
    const duplicate = (node: DesignNode) => {
        const copied = duplicateNode(spec, node);
        if (!copied) return;
        edit(copied.spec);
        setSelected(copied.id);
    };

    return (
        <PaneLayout>
            <Pane id="designer.blocks" side="left" title="Blocks" icon={IconComponents} defaultSize={196} min={160} max={280}
                  defaultCollapsed={window.innerWidth < ROOMY}>
                <Palette spec={spec} onAdd={(block) => {
                    const added = addBlock(spec, block, current);
                    edit(added.spec);
                    setSelected(added.id);
                }} onAddTable={(table) => {
                    const added = addInputTable(spec, table);
                    edit(added.spec);
                    setSelected(added.id);
                }}/>
            </Pane>
            <PaneMain>
                <Canvas dataset={dataset} spec={spec} positions={document.positions} problems={byNode} selected={current.id}
                        onSelect={setSelected} onEdit={edit} onRemove={remove} onDuplicate={duplicate}/>
            </PaneMain>
            <DesignerTools issues={issues} spec={spec} checking={checking} selected={current.id} onPick={(node) => setSelected(node.id)}>
                <Inspector key={current.id} spec={spec} node={current} folderId={folderId}
                           problems={current.id === PIPELINE ? [...byNode.values()].flat() : byNode.get(current.id) ?? []}
                           onChange={(next) => edit(next)} onRemove={() => remove(current)}
                           onMove={(delta) => edit(moveStep(spec, current.index!, delta))}/>
            </DesignerTools>
        </PaneLayout>
    );
}

function Palette({spec, onAdd, onAddTable}: {
    spec: Spec;
    onAdd: (block: Block) => void;
    onAddTable: (table: string) => void
}) {
    const {data: catalog} = useCatalog();
    const tables = (catalog?.datasets ?? [])
        .filter((entry) => entry.dataset !== spec.dataset)
        .flatMap((entry) => entry.tables
            .filter((table) => READABLE.test(table.id))
            .map((table) => `${entry.dataset}.${table.id}`));
    return (
        <div className="df-palette">
            {palette(spec).map((group) => (
                <section key={group.title} className="df-palette-group">
                    <h4 className="df-palette-title">{group.title}</h4>
                    {group.items.map((item) => (
                        <Tooltip key={item.block} label={item.hint} position="right">
                            <UnstyledButton className="df-palette-item" draggable onClick={() => onAdd(item.block)}
                                            onDragStart={(event) => {
                                                event.dataTransfer.setData(DRAG_TYPE, item.block);
                                                event.dataTransfer.effectAllowed = 'copy';
                                            }}>
                                <item.icon {...ICON}/>
                                <span>{item.label}</span>
                            </UnstyledButton>
                        </Tooltip>
                    ))}
                </section>
            ))}
            {tables.length > 0 && (
                <section className="df-palette-group">
                    <h4 className="df-palette-title">Tables</h4>
                    {tables.map((table) => (
                        <Tooltip key={table} label={`Read ${table} in this pipeline`} position="right">
                            <UnstyledButton className="df-palette-item" draggable onClick={() => onAddTable(table)}
                                            onDragStart={(event) => {
                                                event.dataTransfer.setData(DRAG_TABLE, table);
                                                event.dataTransfer.effectAllowed = 'copy';
                                            }}>
                                <IconTable {...ICON}/>
                                <span>{table}</span>
                            </UnstyledButton>
                        </Tooltip>
                    ))}
                </section>
            )}
        </div>
    );
}
