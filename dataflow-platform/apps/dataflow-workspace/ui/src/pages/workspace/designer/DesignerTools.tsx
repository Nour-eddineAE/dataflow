import {IconAlertTriangle, IconListDetails} from '../../../icons';
import {type ReactNode, useEffect, useState} from 'react';
import {Pane} from '../../../components/layout/Pane';
import {Count} from '../../../components/Count';
import {type DesignNode, PIPELINE, type Spec} from '../../../lib/specModel';
import {load, save} from '../../../lib/storage';
import {type Issue, ProblemsPane} from './ProblemsPane';

const TOOLS = [
    {id: 'properties', title: 'Properties', icon: IconListDetails},
    {id: 'problems', title: 'Problems', icon: IconAlertTriangle},
] as const;
type Tool = typeof TOOLS[number]['id'];

const isTool = (value: unknown): value is Tool => TOOLS.some((tool) => tool.id === value);

export function DesignerTools({children, issues, spec, checking, selected, onPick}: {
    children: ReactNode;
    issues: Issue[];
    spec: Spec;
    checking: boolean;
    selected: string;
    onPick: (node: DesignNode) => void;
}) {
    const [folded, setFolded] = useState(selected === PIPELINE);
    const [shown, setShown] = useState(selected);
    if (shown !== selected) {
        setShown(selected);
        if (selected !== PIPELINE) setFolded(false);
    }
    const [tool, setTool] = useState<Tool>(() => {
        const stored = load<unknown>('df.designer.tool', 'properties');
        return isTool(stored) ? stored : 'properties';
    });
    useEffect(() => save('df.designer.tool', tool), [tool]);
    const current = TOOLS.find((entry) => entry.id === tool) ?? TOOLS[0];
    const errors = issues.filter((issue) => issue.severity === 'error').length;
    const count = issues.length > 0 && (
        <Count tone={errors ? 'danger' : 'warning'}>{issues.length}</Count>
    );

    return (
        <Pane id="designer.tools" side="right" title={current.title} icon={current.icon}
              tools={TOOLS.map((entry) => (entry.id === 'problems' ? {...entry, badge: count} : entry))}
              tool={tool} onToolChange={(next) => isTool(next) && setTool(next)}
              collapsed={folded} onCollapsedChange={setFolded}
              defaultSize={440} min={300} max={560}>
            {tool === 'properties' ? children : (
                <ProblemsPane issues={issues} spec={spec} checking={checking} onPick={(node) => {
                    onPick(node);
                    setTool('properties');
                }}/>
            )}
        </Pane>
    );
}
