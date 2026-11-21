import type {ReactNode} from 'react';
import './Count.css';

export type CountTone = 'primary' | 'accent' | 'danger' | 'warning';

export function Count({tone, floating, children}: { tone: CountTone; floating?: boolean; children: ReactNode }) {
    return <span className="df-count" data-tone={tone} data-floating={floating || undefined}>{children}</span>;
}
