import type {Icon} from '../../icons';
import type {CSSProperties, ReactNode} from 'react';
import {ICON} from '../../lib/icons';
import './Graph.css';

export type NodeCardProps = {
    icon: Icon;
    tone?: string;
    label: string;
    detail?: string;
    trailing?: ReactNode;
    selected?: boolean;
    dimmed?: boolean;
    invalid?: boolean;
    dashed?: boolean;
};

export function NodeCard({icon: Glyph, tone, label, detail, trailing, selected, dimmed, invalid, dashed}: NodeCardProps) {
    return (
        <div className="df-node" title={label} data-selected={selected || undefined} data-dimmed={dimmed || undefined}
             data-invalid={invalid || undefined} data-dashed={dashed || undefined} style={tone ? {'--df-node-tone': tone} as CSSProperties : undefined}>
            <span className="df-node-icon"><Glyph {...ICON}/></span>
            <span className="df-node-text">
                <span className="df-node-label">{label}</span>
                {detail !== undefined && <span className="df-node-detail">{detail}</span>}
            </span>
            {trailing}
        </div>
    );
}
