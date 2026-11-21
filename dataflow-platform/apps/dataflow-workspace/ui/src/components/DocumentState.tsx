import {Text} from '@mantine/core';
import './DocumentState.css';

export type DocumentStatus = 'saved' | 'draft' | 'unsaved';

const LABELS: Record<DocumentStatus, string> = {saved: 'Saved', draft: 'Draft', unsaved: 'Unsaved'};

export function DocumentState({status}: { status: DocumentStatus }) {
    return (
        <span className="df-document-state" data-status={status}>
            <span className="df-document-state-dot"/>
            <Text size="xs" c="dimmed">{LABELS[status]}</Text>
        </span>
    );
}
