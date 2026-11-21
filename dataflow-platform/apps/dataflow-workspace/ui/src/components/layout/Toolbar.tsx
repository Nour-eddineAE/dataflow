import type {ReactNode} from 'react';
import './Toolbar.css';

export function Toolbar({children}: { children: ReactNode }) {
    return <div className="df-toolbar" role="toolbar">{children}</div>;
}

export function ToolbarGroup({children, grow}: { children: ReactNode; grow?: boolean }) {
    return <div className="df-toolbar-group" data-grow={grow || undefined}>{children}</div>;
}

export function ToolbarSpacer() {
    return <span className="df-toolbar-spacer" aria-hidden/>;
}

export function ToolbarDivider() {
    return <span className="df-toolbar-divider" aria-hidden/>;
}
