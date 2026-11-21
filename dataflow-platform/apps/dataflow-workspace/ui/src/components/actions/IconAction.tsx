import {ActionIcon, Tooltip} from '@mantine/core';
import type {Icon} from '../../icons';
import type {MouseEvent, ReactNode, Ref} from 'react';
import {Link} from 'react-router-dom';
import {ICON, ICON_LARGE, ICON_SMALL} from '../../lib/icons';
import {tooltip} from './shortcut';
import {Count} from '../Count';

export type IconActionSize = 20 | 28 | 32;

const GLYPH: Record<IconActionSize, typeof ICON | typeof ICON_SMALL | typeof ICON_LARGE> = {20: ICON_SMALL, 28: ICON, 32: ICON_LARGE};

export type IconActionProps = {
    label: string;
    icon?: Icon;
    glyph?: ReactNode;
    badge?: ReactNode;
    onClick?: (event: MouseEvent<HTMLButtonElement>) => void;
    shortcut?: string;
    disabled?: boolean;
    active?: boolean;
    loading?: boolean;
    danger?: boolean;
    outlined?: boolean;
    size?: IconActionSize;
    to?: string;
    href?: string;
    external?: boolean;
    ref?: Ref<HTMLButtonElement>;
};

export function IconAction({label, icon: ActionGlyph, glyph, badge, onClick, shortcut, disabled, active, loading, danger, outlined,
                               size = 28, to, href, external, ref, ...rest}: IconActionProps) {
    const target = external ? {target: '_blank', rel: 'noreferrer'} : {};
    const button = (
        <ActionIcon ref={ref} size={size} variant={outlined ? 'default' : active ? 'light' : 'subtle'} color={danger ? 'red' : 'gray'}
                    aria-label={label} aria-pressed={active} disabled={disabled} loading={loading} onClick={onClick}
                    data-danger={danger || undefined}
                    className="df-icon-action"
                    renderRoot={to ? (props) => <Link to={to} {...props}/> : href ? (props) => <a href={href} {...target} {...props}/> : undefined}
                    {...rest}>
            {glyph ?? (ActionGlyph && <ActionGlyph {...GLYPH[size]}/>)}
            {badge !== undefined && badge !== null && <Count tone="primary" floating>{badge}</Count>}
        </ActionIcon>
    );
    return (
        <Tooltip label={tooltip(label, shortcut)} withArrow>
            {disabled ? <span className="df-icon-action-disabled">{button}</span> : button}
        </Tooltip>
    );
}
