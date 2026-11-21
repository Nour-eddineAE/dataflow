import {Autocomplete, Group, Input, SegmentedControl, Select, Text} from '@mantine/core';
import {type Icon, IconDownload, IconFolder, IconHistory, IconRoute, IconServer, IconShieldPlus, IconTable, IconTrash, IconUser, IconUsers} from '../icons';
import {useMemo, useState} from 'react';
import {useAccessAudit, useCatalog, useDatasets, useFolders, useGrant, useGrants, useMe, useRevoke} from '../api/hooks';
import type {AccessAudit, AccessGrant} from '../api/types';
import {downloadCsv} from '../lib/csv';
import {formatAgo} from '../lib/format';
import {knownPrincipals, type PrincipalKind, privilegeLabel} from '../lib/access';
import {ErrorState} from './ErrorState';
import {FormModal, ViewModal} from './FormModal';
import {SettingRow, SettingsEmpty, SettingsLoading, SettingValue} from './settings/SettingRow';
import {Panel} from './Panel';
import {ICON_SMALL} from '../lib/icons';

import {Action} from './actions/Action';
import {IconAction} from './actions/IconAction';
import {COLOR} from '../lib/colors';
import {notifyFailure} from './notify';

type Kind = 'dataset' | 'table' | 'folder' | 'service';

const SERVICES = ['tower', 'serving', 'lake', 'sign-in', 'alerts', 'mail'];

const PRIVILEGES: Record<Kind, string[]> = {
    dataset: ['SELECT', 'RUN', 'MANAGE'],
    table: ['SELECT'],
    folder: ['READ', 'RUN', 'EDIT', 'MANAGE'],
    service: ['MONITOR', 'MANAGE'],
};

export function AccessPanel() {
    const {data, error, isLoading} = useGrants();
    const {data: me} = useMe();
    const revoke = useRevoke();
    const [granting, setGranting] = useState(false);
    const [history, setHistory] = useState(false);
    const [revoking, setRevoking] = useState<AccessGrant>();
    return (
        <>
            <Panel flush title="Grants" actions={<>
                {me?.admin && <Action size="sm" icon={IconHistory} label="History" onClick={() => setHistory(true)}/>}
                <Action size="sm" icon={IconShieldPlus} label="Grant" onClick={() => setGranting(true)}/>
            </>}>
                {isLoading ? <SettingsLoading/> : error ? <ErrorState error={error}/> : !data?.length ?
                    <SettingsEmpty>No grants</SettingsEmpty> :
                    data.map((grant) => (
                        <SettingRow key={key(grant)} label={<Securable value={grant.securable}/>} hint={`Granted by ${grant.grantedBy}`}>
                            <Principal value={grant.principal}/>
                            <span className="df-chip">{privilegeLabel(grant.privilege)}</span>
                            <IconAction label="Revoke" icon={IconTrash} danger onClick={() => setRevoking(grant)}/>
                        </SettingRow>
                    ))}
            </Panel>
            <ViewModal opened={history} onClose={() => setHistory(false)} title="History">
                {history && <History/>}
            </ViewModal>
            <GrantModal opened={granting} onClose={() => setGranting(false)}/>
            <FormModal opened={!!revoking} onClose={() => setRevoking(undefined)} title="Revoke access?" submit="Revoke" danger
                       loading={revoke.isPending}
                       onSubmit={() => revoking && revoke.mutate(revoking, {
                           onSuccess: () => setRevoking(undefined),
                           onError: notifyFailure('Not revoked'),
                       })}>
                <Text size="sm">{revoking && `${privilegeLabel(revoking.privilege)} on ${revoking.securable} for ${revoking.principal}`}</Text>
            </FormModal>
        </>
    );
}

function History() {
    const audit = useAccessAudit(true);
    if (audit.isLoading) return <SettingsLoading/>;
    if (!audit.data?.length) return <SettingsEmpty>No changes</SettingsEmpty>;
    const entries = audit.data;
    return <>
        <Group justify="flex-end">
            <IconAction label="Download CSV" icon={IconDownload} onClick={() => downloadCsv('access-audit', auditTable(entries))}/>
        </Group>
        {entries.map((entry, index) => (
        <SettingRow key={index} hint={`${entry.actor}, ${formatAgo(entry.at)}`}
                    label={<>{ACTIONS[entry.action] ?? entry.action} {entry.privilege && <span className="df-chip">{privilegeLabel(entry.privilege)}</span>}</>}>
            <Securable value={entry.securable}/>
            {entry.principal && <Principal value={entry.principal}/>}
            <SettingValue muted>{formatAgo(entry.at)}</SettingValue>
        </SettingRow>
        ))}
    </>;
}

const ACTIONS: Record<string, string> = {
    GRANT: 'Granted',
    REVOKE: 'Revoked',
    RUN: 'Ran',
    BACKFILL: 'Backfilled',
    TERMINATE: 'Stopped',
    PAUSE: 'Paused',
    RESUME: 'Resumed',
    RELEASE: 'Released',
};

const AUDIT_COLUMNS = ['at', 'actor', 'action', 'securable', 'principal', 'privilege'] as const;

const auditTable = (entries: AccessAudit[]) => ({
    columns: AUDIT_COLUMNS.map((name) => ({name, type: 'String'})),
    rows: entries.map((entry) => AUDIT_COLUMNS.map((name) => entry[name])),
});

function Securable({value}: { value: string }) {
    const [kind, name] = split(value);
    const Glyph = KIND_ICONS[kind] ?? IconFolder;
    return <span className="df-principal"><Glyph {...ICON_SMALL} color={COLOR.dimmed}/>{name}</span>;
}

function Principal({value}: { value: string }) {
    const [kind, name] = split(value);
    const Glyph = kind === 'group' ? IconUsers : IconUser;
    return <span className="df-principal df-setting-value"><Glyph {...ICON_SMALL} color={COLOR.dimmed}/>{name}</span>;
}

const KIND_ICONS: Record<string, Icon> = {dataset: IconRoute, table: IconTable, folder: IconFolder, service: IconServer};

function split(value: string): [string, string] {
    const colon = value.indexOf(':');
    return colon < 0 ? ['', value] : [value.slice(0, colon), value.slice(colon + 1)];
}

function GrantModal({opened, onClose}: { opened: boolean; onClose: () => void }) {
    const grant = useGrant();
    const {data: datasets} = useDatasets();
    const {data: catalog} = useCatalog();
    const {data: folders} = useFolders();
    const {data: grants} = useGrants();
    const {data: me} = useMe();
    const [kind, setKind] = useState<Kind>('dataset');
    const [name, setName] = useState<string | null>(null);
    const [who, setWho] = useState<PrincipalKind>('group');
    const [principal, setPrincipal] = useState('');
    const [privilege, setPrivilege] = useState<string | null>('SELECT');
    const names = useMemo(() => {
        if (kind === 'dataset') return (datasets ?? []).map((dataset) => dataset.dataset);
        if (kind === 'table') return (catalog?.datasets ?? []).flatMap((dataset) => dataset.tables.map((table) => `${dataset.dataset}.${table.id}`));
        if (kind === 'service') return SERVICES;
        return (folders ?? []).filter((folder) => folder.path.startsWith('Shared/')).map((folder) => folder.path);
    }, [kind, datasets, catalog, folders]);
    const known = knownPrincipals(who, (grants ?? []).map((entry) => entry.principal), me?.groups ?? []);
    const submit = () =>
        grant.mutate({securable: `${kind}:${name}`, principal: `${who}:${principal.trim()}`, privilege: privilege!}, {
            onSuccess: () => {
                onClose();
                setName(null);
                setPrincipal('');
            },
            onError: notifyFailure('Access update failed'),
        });
    return (
        <FormModal opened={opened} onClose={onClose} title="Grant" size="md" submit="Grant"
                   submitDisabled={!name || !principal.trim() || !privilege} loading={grant.isPending}
                   onSubmit={submit}>
            <Input.Wrapper label="On">
                <SegmentedControl fullWidth value={kind} data={[{value: 'dataset', label: 'Dataset'}, {
                    value: 'table',
                    label: 'Table'
                }, {value: 'folder', label: 'Folder'}, {value: 'service', label: 'Service'}]}
                                  onChange={(value) => {
                                      setKind(value as Kind);
                                      setName(null);
                                      setPrivilege(PRIVILEGES[value as Kind][0]);
                                  }}/>
            </Input.Wrapper>
            <Select
                label={kind === 'dataset' ? 'Dataset' : kind === 'table' ? 'Table' : kind === 'folder' ? 'Folder' : 'Service'}
                searchable data={names} value={name} onChange={setName} data-autofocus/>
            <Input.Wrapper label="To">
                <SegmentedControl fullWidth value={who} onChange={(value) => setWho(value as PrincipalKind)}
                                  data={[{value: 'group', label: 'Group'}, {value: 'user', label: 'Person'}]}/>
            </Input.Wrapper>
            <Autocomplete label={who === 'group' ? 'Group' : 'Person'} data={known} value={principal} onChange={setPrincipal}/>
            <Select label="Privilege" data={PRIVILEGES[kind].map((code) => ({value: code, label: privilegeLabel(code)}))}
                    value={privilege} onChange={setPrivilege}
                    allowDeselect={false}/>
        </FormModal>
    );
}

const key = (grant: Pick<AccessGrant, 'securable' | 'principal' | 'privilege'>) => `${grant.securable}|${grant.principal}|${grant.privilege}`;
