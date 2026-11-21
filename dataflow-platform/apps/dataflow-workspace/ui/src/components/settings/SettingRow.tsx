import {Skeleton} from '@mantine/core';
import type {ReactNode} from 'react';
import {Hint} from '../Hint';
import './Settings.css';

export function SettingRow({label, hint, children}: { label: ReactNode; hint?: string | null; children?: ReactNode }) {
    return (
        <div className="df-setting-row">
            <Hint label={hint}>
                <div className="df-setting-label">{label}</div>
            </Hint>
            {children && <div className="df-setting-control">{children}</div>}
        </div>
    );
}

export function SettingValue({children, muted}: { children: ReactNode; muted?: boolean }) {
    return <span className="df-setting-value" data-muted={muted || undefined}>{children}</span>;
}

export function SettingsEmpty({children}: { children: ReactNode }) {
    return <div className="df-setting-row df-settings-empty">{children}</div>;
}

const SKELETON_WIDTHS = ['28%', '36%', '22%', '32%'];
const SKELETON_VALUE_WIDTH = '18%';
const SKELETON_HEIGHT = 12;

export function SettingsLoading({rows = 3}: { rows?: number }) {
    return Array.from({length: rows}, (_, index) => (
        <div key={index} className="df-setting-row">
            <Skeleton height={SKELETON_HEIGHT} width={SKELETON_WIDTHS[index % SKELETON_WIDTHS.length]} radius="sm"/>
            <Skeleton height={SKELETON_HEIGHT} width={SKELETON_VALUE_WIDTH} radius="sm"/>
        </div>
    ));
}
