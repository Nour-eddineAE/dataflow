import {Checkbox, Text} from '@mantine/core';
import {IconTrash} from '../icons';
import {useState} from 'react';
import {useCleanUp, useHousekeeping} from '../api/hooks';
import type {CleanupItem, CleanupKind} from '../api/types';
import {formatBytes, formatNumber, plural} from '../lib/format';
import {ErrorState} from './ErrorState';
import {FormModal} from './FormModal';
import {SettingRow, SettingsEmpty, SettingsLoading, SettingValue} from './settings/SettingRow';
import {Panel} from './Panel';

import {Action} from './actions/Action';
import {notifyDone, notifyFailure} from './notify';

const LABELS: Record<CleanupKind, string> = {
    PREVIEWS: 'Old preview results',
    EVENT_LOGS: 'Old Spark event logs',
    CHECKPOINTS: 'Checkpoints of removed datasets',
    UPLOADS: 'Old uploaded files',
    RELEASES: 'Old releases',
    CATALOGS: 'Catalogs of removed datasets',
};

function amount(item: CleanupItem) {
    return item.kind === 'CATALOGS'
        ? plural(item.files, 'view')
        : `${formatNumber(item.files)} files · ${formatBytes(item.bytes)}`;
}

export function CleanupPanel() {
    const {data, error, isLoading} = useHousekeeping();
    const clean = useCleanUp();
    const [chosen, setChosen] = useState<CleanupKind[]>([]);
    const [confirming, setConfirming] = useState(false);
    const items = data ?? [];
    const selected = items.filter((item) => chosen.includes(item.kind));
    const toggle = (kind: CleanupKind) => setChosen(chosen.includes(kind) ? chosen.filter((value) => value !== kind) : [...chosen, kind]);
    const submit = () =>
        clean.mutate(chosen, {
            onSuccess: (result) => {
                setConfirming(false);
                setChosen([]);
                notifyDone({message: `${result.removed} removed, ${result.lakeLocations} lake locations sent to the engine`});
            },
            onError: notifyFailure('Not cleaned'),
        });
    return (
        <Panel flush title="Unused files" actions={
            <Action size="sm" danger icon={IconTrash} label="Delete" disabled={!chosen.length} onClick={() => setConfirming(true)}/>
        }>
            {isLoading ? <SettingsLoading/> : error ? <ErrorState error={error}/> : !items.length ?
                <SettingsEmpty>Nothing to clean</SettingsEmpty> :
                items.map((item) => (
                    <SettingRow key={`${item.kind}:${item.location}`} label={LABELS[item.kind]} hint={item.location}>
                        <SettingValue muted>{amount(item)}</SettingValue>
                        <Checkbox size="sm" checked={chosen.includes(item.kind)} onChange={() => toggle(item.kind)}
                                  aria-label={LABELS[item.kind]}/>
                    </SettingRow>
                ))}
            <FormModal opened={confirming} onClose={() => setConfirming(false)} title="Delete unused files?" submit="Delete" danger
                       loading={clean.isPending} onSubmit={submit}>
                <Text size="sm">{selected.map((item) => `${LABELS[item.kind]} (${amount(item)})`).join(', ')}. This cannot be undone.</Text>
            </FormModal>
        </Panel>
    );
}
