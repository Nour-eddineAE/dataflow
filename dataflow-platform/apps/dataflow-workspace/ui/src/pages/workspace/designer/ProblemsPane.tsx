import {Loader} from '@mantine/core';
import {IconCircleCheck} from '../../../icons';
import {useState} from 'react';
import {TabStrip} from '../../../components/layout/Tabs';
import {IssueRow, type Severity} from '../../../components/IssueRow';
import type {DesignNode, Spec} from '../../../lib/specModel';
import {nodeStyle} from './blocks';
import {ICON, ICON_HERO, ICON_SMALL} from '../../../lib/icons';

export type Issue = { severity: Severity; message: string; node: DesignNode };
type Filter = 'all' | 'error' | 'warning';

export function ProblemsPane({issues, spec, checking, onPick}: {
    issues: Issue[];
    spec: Spec;
    checking: boolean;
    onPick: (node: DesignNode) => void;
}) {
    const [filter, setFilter] = useState<Filter>('all');
    const errors = issues.filter((issue) => issue.severity === 'error').length;
    const warnings = issues.length - errors;
    const shown = filter === 'all' ? issues : issues.filter((issue) => issue.severity === filter);
    return (
        <>
            <div className="df-problems-filters">
                <TabStrip value={filter} onChange={setFilter} items={[
                    {value: 'all', label: `All ${issues.length}`},
                    {value: 'error', label: `Errors ${errors}`},
                    {value: 'warning', label: `Warnings ${warnings}`},
                ]}/>
            </div>
            <div className="df-issues" aria-busy={checking}>
                {shown.length ? shown.map((issue) => {
                    const style = nodeStyle(issue.node, spec);
                    return (
                        <IssueRow key={`${issue.severity}:${issue.node.id}:${issue.message}`} severity={issue.severity}
                                  message={issue.message} detail={<><style.icon {...ICON_SMALL}/>{issue.node.label}</>}
                                  onPick={() => onPick(issue.node)}/>
                    );
                }) : (
                    <div className="df-issues-empty">
                        {checking ? <Loader {...ICON}/> : <IconCircleCheck {...ICON_HERO}/>}
                        <span>{checking ? 'Checking' : filter === 'all' ? 'No problems' : `No ${filter}s`}</span>
                    </div>
                )}
            </div>
        </>
    );
}
