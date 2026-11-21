import {Group} from '@mantine/core';
import {useMe} from '../api/hooks';
import {ErrorState} from './ErrorState';
import {SettingRow, SettingsLoading, SettingValue} from './settings/SettingRow';
import {Panel} from './Panel';

export function ProfilePanel() {
    const {data: me, error, isLoading} = useMe();
    if (isLoading) return <Panel flush title="You"><SettingsLoading/></Panel>;
    if (error || !me) return <ErrorState error={error}/>;
    return (
        <Panel flush title="You">
            <SettingRow label="Name"><SettingValue>{me.name}</SettingValue></SettingRow>
            <SettingRow label="Email"><SettingValue>{me.email}</SettingValue></SettingRow>
            <SettingRow label="Username"><SettingValue>{me.id}</SettingValue></SettingRow>
            <SettingRow label="Role"><SettingValue>{me.admin ? 'Administrator' : 'Member'}</SettingValue></SettingRow>
            <SettingRow label="Groups">
                {me.groups.length
                    ? <Group className="df-chip-list" justify="flex-end">{me.groups.map((group) => <span key={group} className="df-chip">{group}</span>)}</Group>
                    : <SettingValue muted>None</SettingValue>}
            </SettingRow>
        </Panel>
    );
}
