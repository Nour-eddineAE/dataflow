import {UnstyledButton} from '@mantine/core';
import type {ReactNode} from 'react';
import {IconAlertTriangle, IconCircleX} from '../icons';
import {ICON} from '../lib/icons';
import './Issues.css';

export type Severity = 'error' | 'warning';

export function IssueRow({severity, message, detail, onPick}: {
    severity: Severity;
    message: string;
    detail?: ReactNode;
    onPick?: () => void;
}) {
    const content = (
        <>
            {severity === 'error'
                ? <IconCircleX {...ICON} className="df-issue-icon"/>
                : <IconAlertTriangle {...ICON} className="df-issue-icon"/>}
            <span className="df-issue-body">
                <span className="df-issue-message">{message}</span>
                {detail && <span className="df-issue-node">{detail}</span>}
            </span>
        </>
    );
    return onPick
        ? <UnstyledButton className="df-issue" data-severity={severity} onClick={onPick}>{content}</UnstyledButton>
        : <div className="df-issue" data-severity={severity}>{content}</div>;
}
