import {Button, Tooltip} from '@mantine/core';
import {type Icon, IconChevronDown} from '../../icons';
import type {MouseEvent, ReactNode, Ref} from 'react';
import {Link} from 'react-router-dom';
import {ICON, ICON_SMALL} from '../../lib/icons';
import {tooltip} from './shortcut';
import {Count} from '../Count';

export type ActionTone = 'primary' | 'default' | 'subtle';
export type ActionSize = 'sm' | 'md';

export const ACTION_MIN_WIDTH = 96;

const HEIGHT: Record<ActionSize, number> = {sm: 28, md: 32};
const PADDING: Record<ActionSize, number> = {sm: 10, md: 12};
const VARIANT: Record<ActionTone, 'filled' | 'default' | 'subtle'> = {primary: 'filled', default: 'default', subtle: 'subtle'};

export type ActionProps = {
    label: ReactNode;
    icon?: Icon;
    leading?: ReactNode;
    tone?: ActionTone;
    size?: ActionSize;
    danger?: boolean;
    hint?: string;
    shortcut?: string;
    foldable?: boolean;
    count?: number;
    menu?: boolean;
    wide?: boolean;
    disabled?: boolean;
    loading?: boolean;
    type?: 'button' | 'submit';
    to?: string;
    href?: string;
    external?: boolean;
    onClick?: (event: MouseEvent<HTMLButtonElement>) => void;
    ref?: Ref<HTMLButtonElement>;
};

export function Action({label, icon: Glyph, leading, tone = 'default', size = 'md', danger, hint, shortcut, foldable, count, menu, wide,
                           disabled, loading, type = 'button', to, href, external, onClick, ref, ...rest}: ActionProps) {
    const text = typeof label === 'string' ? label : undefined;
    const tip = hint ?? (foldable || shortcut ? text : undefined);
    const target = external ? {target: '_blank', rel: 'noreferrer'} : {};
    const explained = !!disabled && !!hint;
    return (
        <Tooltip label={tooltip(tip ?? '', shortcut)} withArrow disabled={!tip}>
            <Button ref={ref} type={type} size={size === 'sm' ? 'xs' : 'sm'} h={HEIGHT[size]} px={PADDING[size]}
                    miw={wide ? ACTION_MIN_WIDTH : undefined} variant={VARIANT[tone]} color={danger ? 'red' : tone === 'subtle' ? 'gray' : undefined}
                    disabled={disabled && !explained} data-disabled={explained || undefined} aria-disabled={explained || undefined}
                    loading={loading} onClick={explained ? (event) => event.preventDefault() : onClick} aria-label={text}
                    data-danger={danger || undefined}
                    leftSection={leading ?? (Glyph ? <Glyph {...ICON}/> : undefined)}
                    rightSection={menu ? <IconChevronDown {...ICON_SMALL}/> : undefined} className="df-action"
                    renderRoot={to ? (props) => <Link to={to} {...props}/> : href ? (props) => <a href={href} {...target} {...props}/> : undefined}
                    {...rest}>
                <span className="df-action-label">{label}</span>
                {count ? <Count tone="accent">{count}</Count> : null}
            </Button>
        </Tooltip>
    );
}
