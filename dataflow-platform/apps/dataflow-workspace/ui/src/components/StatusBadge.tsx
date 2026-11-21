import {Group, Loader, Text} from '@mantine/core';
import {
    IconCircleCheckFilled,
    IconCircleDashed,
    IconAlertTriangle,
    IconCircleXFilled,
    IconClock,
    IconMinus
} from '../icons';
import {statusTone, type Tone, toneColor} from '../lib/status';
import {ICON, ICON_SMALL, ICON_STATUS} from '../lib/icons';

const LOADER_INSET = 3;

export function StatusIcon({status, size = ICON_STATUS.size}: { status?: string; size?: number }) {
    const {tone} = statusTone(status);
    return <ToneIcon tone={tone} size={size} known={!!status}/>;
}

function ToneIcon({tone, size, known}: { tone: Tone; size: number; known: boolean }) {
    const color = toneColor[tone];
    switch (tone) {
        case 'success':
            return <IconCircleCheckFilled size={size} color={color} style={{flex: 'none'}}/>;
        case 'danger':
            return <IconCircleXFilled size={size} color={color} style={{flex: 'none'}}/>;
        case 'running':
            return <Loader size={size - LOADER_INSET} color={color} style={{flex: 'none'}}/>;
        case 'queued':
            return <IconClock size={size} color={color} style={{flex: 'none'}}/>;
        case 'warning':
            return <IconAlertTriangle size={size} color={color} style={{flex: 'none'}}/>;
        default:
            return known ? <IconMinus size={size} color={color} style={{flex: 'none'}}/> :
                <IconCircleDashed size={size} color={color} style={{flex: 'none'}}/>;
    }
}

export function StatusBadge({status, size = 'sm'}: { status?: string; size?: 'xs' | 'sm' | 'md' }) {
    const {label} = statusTone(status);
    return (
        <Group gap="snug" wrap="nowrap" display="inline-flex">
            <StatusIcon status={status} size={size === 'md' ? ICON.size : ICON_SMALL.size}/>
            <Text size={size === 'md' ? 'sm' : size} c={status ? undefined : 'dimmed'}>{label}</Text>
        </Group>
    );
}
