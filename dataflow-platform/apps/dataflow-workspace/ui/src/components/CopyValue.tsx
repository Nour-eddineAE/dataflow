import {CopyButton} from '@mantine/core';
import {IconCheck, IconCopy} from '../icons';

import {IconAction, type IconActionSize} from './actions/IconAction';

export function CopyValue({value, label, size}: { value: string; label: string; size?: IconActionSize }) {
    return (
        <CopyButton value={value}>
            {({copied, copy}) => (
                <IconAction label={copied ? 'Copied' : label} icon={copied ? IconCheck : IconCopy} size={size} onClick={copy}/>
            )}
        </CopyButton>
    );
}
