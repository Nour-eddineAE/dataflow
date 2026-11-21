import {Group, type MantineColorScheme, SegmentedControl, useMantineColorScheme} from '@mantine/core';
import {IconDeviceDesktop, IconMoon, IconSun} from '../icons';
import type {ReactNode} from 'react';
import {SettingRow} from './settings/SettingRow';
import {Panel} from './Panel';
import {ICON} from '../lib/icons';

const SCHEMES: { value: MantineColorScheme; label: string; icon: ReactNode }[] = [
    {value: 'light', label: 'Light', icon: <IconSun {...ICON}/>},
    {value: 'dark', label: 'Dark', icon: <IconMoon {...ICON}/>},
    {value: 'auto', label: 'System', icon: <IconDeviceDesktop {...ICON}/>},
];

export function PreferencesPanel() {
    const {colorScheme, setColorScheme} = useMantineColorScheme();
    return (
        <Panel flush title="Appearance">
            <SettingRow label="Theme">
                <SegmentedControl value={colorScheme} onChange={(value) => setColorScheme(value as MantineColorScheme)}
                                  data={SCHEMES.map((scheme) => ({
                                      value: scheme.value,
                                      label: <Group gap="snug" wrap="nowrap">{scheme.icon}<span>{scheme.label}</span></Group>,
                                  }))}/>
            </SettingRow>
        </Panel>
    );
}
