import {Group, TextInput, Tooltip} from '@mantine/core';
import {IconLock, IconSearch} from '../icons';
import {type ReactNode, useState} from 'react';
import {useEffectiveSettings, useStatus} from '../api/hooks';
import type {EffectiveSetting} from '../api/types';
import {ICON_SMALL} from '../lib/icons';
import {humanize, isSet, matchingSettings, sectionTitle, settingHint, settingLabel} from '../lib/settings';
import {Action} from './actions/Action';
import {CopyValue} from './CopyValue';
import {ErrorState} from './ErrorState';
import {SettingRow, SettingsEmpty, SettingsLoading, SettingValue} from './settings/SettingRow';
import {Panel} from './Panel';

const FOLDED_ROWS = 6;

export function ConfigurationPanel() {
    const {data, error, isLoading} = useEffectiveSettings();
    const {data: status} = useStatus();
    const [query, setQuery] = useState('');
    if (isLoading) return <Panel flush title="About"><SettingsLoading/></Panel>;
    if (error || !data) return <ErrorState error={error}/>;
    const searching = query.trim().length > 0;
    const reported = [{title: 'Engine', sections: data.engine}, {title: 'Tower', sections: data.tower}];
    const sections = [
        ...data.workspace.map((section) => ({section, title: sectionTitle(section.name)})),
        ...data.serving.map((section) => ({section, title: `ClickHouse ${sectionTitle(section.name).toLowerCase()}`})),
        ...reported.flatMap(({title, sections}) => sections.map((section) =>
            ({section, title: `${title} ${sectionTitle(section.name).toLowerCase()}`}))),
    ]
        .map((entry) => ({...entry, settings: matchingSettings(entry.section, query)}))
        .filter((entry) => entry.settings.length > 0);

    return (
        <>
            <Panel flush title="About">
                <SettingRow label="Version"><SettingValue>{status?.version ?? '—'}</SettingValue></SettingRow>
            </Panel>
            <TextInput placeholder="Search settings" leftSection={<IconSearch {...ICON_SMALL}/>} value={query} mt="xl" mb="xs"
                       aria-label="Search settings" onChange={(event) => setQuery(event.currentTarget.value)}/>
            {sections.length ? sections.map(({section, title, settings}) => (
                <Folded key={section.name} title={title} rows={settings.map((setting) =>
                    <PlatformRow key={setting.name} setting={setting} section={section.name}/>)} open={searching}/>
            )) : <SettingsEmpty>No match</SettingsEmpty>}
            {!searching && reported.flatMap(({title, sections}) => sections
                .filter((section) => !section.settings.length)
                .map((section) => (
                    <Panel key={section.name} flush title={title}><SettingsEmpty>{section.description}</SettingsEmpty></Panel>
                )))}
        </>
    );
}

function Folded({title, rows, open}: { title: string; rows: ReactNode[]; open: boolean }) {
    const [all, setAll] = useState(false);
    const shown = open || all ? rows : rows.slice(0, FOLDED_ROWS);
    return (
        <Panel flush title={title} actions={!open && rows.length > FOLDED_ROWS &&
            <Action size="sm" tone="subtle" label={all ? 'Show fewer' : `Show all ${rows.length}`} onClick={() => setAll(!all)}/>}>
            {shown}
        </Panel>
    );
}

function PlatformRow({setting, section}: { setting: EffectiveSetting; section: string }) {
    return (
        <SettingRow hint={settingHint(setting)}
                    label={<>
                        {settingLabel(setting.name, section)}
                        <span className="df-row-tool"><CopyValue value={setting.name} label={`Copy ${setting.name}`} size={20}/></span>
                    </>}>
            <Value setting={setting}/>
        </SettingRow>
    );
}

function Value({setting}: { setting: EffectiveSetting }) {
    if (!isSet(setting.value)) return <SettingValue muted>Not set</SettingValue>;
    switch (setting.kind) {
        case 'SECRET':
            return <SettingValue muted><span className="df-principal"><IconLock {...ICON_SMALL}/>Hidden</span></SettingValue>;
        case 'FLAG':
            return <SettingValue>{setting.value === 'true' ? 'On' : 'Off'}</SettingValue>;
        case 'LIST':
            return (
                <Group className="df-chip-list" justify="flex-end">
                    {setting.value.map((item) => <span key={item} className="df-chip">{item}</span>)}
                </Group>
            );
        case 'ENTRIES':
            return <Entries entries={setting.value}/>;
        default:
            return (
                <Tooltip label={`Default ${setting.defaultValue || '""'}`}
                         disabled={setting.defaultValue == null || setting.defaultValue === setting.value}>
                    <SettingValue>{setting.value}</SettingValue>
                </Tooltip>
            );
    }
}

function Entries({entries}: { entries: Record<string, string>[] }) {
    return (
        <Group className="df-chip-list" justify="flex-end">
            {entries.map((entry, index) => {
                const values = Object.values(entry);
                return (
                    <Tooltip key={index} label={Object.entries(entry).map(([name, value]) => `${humanize(name)}: ${value}`).join(' · ')}>
                        <span className="df-chip">{entry.title ?? entry.name ?? values[0]}</span>
                    </Tooltip>
                );
            })}
        </Group>
    );
}
