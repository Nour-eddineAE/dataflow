import type {Icon} from '../../icons';
import type {MouseEvent, ReactNode, Ref} from 'react';
import {Action} from './Action';

export type ToolbarButtonProps = {
    label: string;
    icon: Icon;
    leading?: ReactNode;
    onClick?: (event: MouseEvent<HTMLButtonElement>) => void;
    shortcut?: string;
    hint?: string;
    count?: number;
    disabled?: boolean;
    loading?: boolean;
    primary?: boolean;
    quiet?: boolean;
    menu?: boolean;
    ref?: Ref<HTMLButtonElement>;
};

export function ToolbarButton({primary, quiet, ...props}: ToolbarButtonProps) {
    return <Action size="sm" tone={primary ? 'primary' : quiet ? 'subtle' : 'default'} foldable {...props}/>;
}
