import {Text} from '@mantine/core';
import {IconAlertCircle, IconTrash} from '../../../icons';
import {IconAction} from '../../../components/actions/IconAction';
import {type DesignNode, isRemovable, renameGold, renameReference, type Spec} from '../../../lib/specModel';
import {nodeStyle} from './blocks';
import {BronzeForm, HistoryForm, PipelineForm, SilverForm, SourceForm} from './IngestForms';
import {StepForm} from './StepForms';
import {GoldForm, InputForm, ReferenceForm, ServeForm} from './TableForms';
import {ICON_LARGE, ICON_SMALL} from '../../../lib/icons';

type Props = {
    spec: Spec;
    node: DesignNode;
    folderId: string;
    problems: string[];
    onChange: (spec: Spec) => void;
    onRemove: () => void;
    onMove: (delta: number) => void;
};

export function Inspector({spec, node, folderId, problems, onChange, onRemove, onMove}: Props) {
    const style = nodeStyle(node, spec);
    return (
        <div className="df-inspector">
            <header className="df-inspector-head">
                <span className="df-inspector-icon"><style.icon {...ICON_LARGE}/></span>
                <div className="df-inspector-name">
                    <Text fw={600} truncate>{node.label}</Text>
                    <Text size="xs" c="dimmed" truncate>{style.hint}</Text>
                </div>
                <IconAction label="Delete" icon={IconTrash} danger disabled={!isRemovable(node)} onClick={onRemove}/>
            </header>
            {problems.length > 0 && (
                <ul className="df-inspector-problems">
                    {problems.map((problem, index) => (
                        <li key={index}><IconAlertCircle {...ICON_SMALL}/><span>{problem}</span></li>
                    ))}
                </ul>
            )}
            <Form spec={spec} node={node} folderId={folderId} onChange={onChange} onMove={onMove}/>
        </div>
    );
}

function Form({spec, node, folderId, onChange, onMove}: Omit<Props, 'onRemove' | 'problems'>) {
    const index = node.index!;
    switch (node.kind) {
        case 'pipeline':
            return <PipelineForm spec={spec} onChange={onChange}/>;
        case 'source':
            return <SourceForm spec={spec} onChange={onChange}/>;
        case 'bronze':
            return <BronzeForm spec={spec} onChange={onChange}/>;
        case 'silver':
            return <SilverForm spec={spec} onChange={onChange}/>;
        case 'history':
            return <HistoryForm spec={spec} onChange={onChange}/>;
        case 'step':
            return <StepForm spec={spec} step={spec.silver!.steps![index]} folderId={folderId} position={index}
                             count={spec.silver!.steps!.length} onMove={onMove}
                             onChange={(step) => onChange({...spec, silver: {...spec.silver, steps: at(spec.silver!.steps!, index, step)}})}/>;
        case 'input':
            return <InputForm spec={spec} alias={Object.keys(spec.inputs ?? {})[index]} onChange={onChange}/>;
        case 'reference':
            return <ReferenceForm reference={spec.references![index]} onRename={(name) => onChange(renameReference(spec, index, name))}
                                  onChange={(reference) => onChange({...spec, references: at(spec.references!, index, reference)})}/>;
        case 'gold':
            return <GoldForm spec={spec} gold={spec.gold![index]} folderId={folderId}
                             onRename={(name) => onChange(renameGold(spec, index, name))}
                             onChange={(gold) => onChange({...spec, gold: at(spec.gold!, index, gold)})}/>;
        case 'serve':
            return <ServeForm spec={spec} serve={spec.serve![index]}
                              onChange={(serve) => onChange({...spec, serve: at(spec.serve!, index, serve)})}/>;
    }
}

function at<T>(items: T[], index: number, item: T): T[] {
    return items.map((current, position) => (position === index ? item : current));
}
