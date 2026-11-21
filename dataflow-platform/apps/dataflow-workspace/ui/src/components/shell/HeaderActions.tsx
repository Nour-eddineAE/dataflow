import {Avatar, Kbd, Menu, Text} from '@mantine/core';
import {
    type Icon,
    IconActivity,
    IconBucket,
    IconChartBar,
    IconDatabase,
    IconGridDots,
    IconKeyboard,
    IconListDetails,
    IconLogout,
    IconMail,
    IconSitemap,
} from '../../icons';
import type {CSSProperties} from 'react';
import {useNavigate} from 'react-router-dom';
import {signOut} from '../../api/client';
import {isRunning, useMe, useRuns, useStatus, RUN_LIMIT} from '../../api/hooks';
import type {Status} from '../../api/types';
import {formatAgo} from '../../lib/format';
import {links, PAGE} from '../../lib/links';
import {IconAction} from '../actions/IconAction';
import {StatusIcon} from '../StatusBadge';
import {ICON, ICON_LARGE, ICON_STATUS} from '../../lib/icons';

const INITIALS = 2;
import {TONES} from '../../lib/layers';
import {COLOR} from '../../lib/colors';

const AVATAR = 26;

const LISTED = 6;

const APPS: { key: keyof Status['links']; label: string; hint: string; icon: Icon; tone: string }[] = [
    {key: 'tower', label: 'Dagster', hint: 'Orchestration', icon: IconSitemap, tone: TONES.brand},
    {key: 'dashboards', label: 'Grafana', hint: 'Boards, alerts', icon: IconChartBar, tone: TONES.dashboards},
    {key: 'serving', label: 'ClickHouse', hint: 'Serving', icon: IconDatabase, tone: TONES.gold},
    {key: 'objectStore', label: 'RustFS', hint: 'Object store', icon: IconBucket, tone: TONES.serving},
    {key: 'mail', label: 'Mailpit', hint: 'Mail', icon: IconMail, tone: TONES.history},
];

export function ActiveRuns() {
    const navigate = useNavigate();
    const {data: runs} = useRuns(undefined, RUN_LIMIT.watched);
    const active = (runs ?? []).filter((run) => isRunning(run.status));
    const label = active.length ? `${active.length} ${active.length === 1 ? 'run' : 'runs'} in progress` : 'No runs in progress';
    return (
        <Menu position="bottom-end" width={300}>
            <Menu.Target>
                <IconAction label={label} icon={IconActivity} size={32} active={active.length > 0}
                            badge={active.length || undefined}/>
            </Menu.Target>
            <Menu.Dropdown>
                <Menu.Label>Runs in progress</Menu.Label>
                {active.length ? active.slice(0, LISTED).map((run) => (
                    <Menu.Item key={run.runId} leftSection={<StatusIcon status={run.status} {...ICON_STATUS}/>}
                               rightSection={<Text size="xs" c="dimmed">{formatAgo(run.startedAt ?? run.createdAt)}</Text>}
                               onClick={() => navigate(links.run(run.runId))}>
                        <Text size="sm" truncate>{run.dataset ?? run.job}{run.partition ? ` · ${run.partition}` : ''}</Text>
                    </Menu.Item>
                )) : <Text size="sm" c="dimmed" px="sm" py="snug">Nothing is running</Text>}
                <Menu.Divider/>
                <Menu.Item leftSection={<IconListDetails {...ICON}/>} onClick={() => navigate(PAGE.runs)}>All runs</Menu.Item>
            </Menu.Dropdown>
        </Menu>
    );
}

export function AppsMenu() {
    const {data: status} = useStatus();
    const apps = APPS.filter((app) => status?.links[app.key]);
    return (
        <Menu position="bottom-end" width={312}>
            <Menu.Target>
                <IconAction label="Platform apps" icon={IconGridDots} size={32}/>
            </Menu.Target>
            <Menu.Dropdown>
                <Menu.Label>Platform apps</Menu.Label>
                {apps.length ? (
                    <div className="df-apps">
                        {apps.map((app) => (
                            <Menu.Item key={app.key} component="a" href={status!.links[app.key]} target="_blank" rel="noreferrer"
                                       className="df-app" style={{'--df-app-tone': app.tone} as CSSProperties}>
                                <span className="df-app-icon"><app.icon {...ICON_LARGE}/></span>
                                <span className="df-app-name">{app.label}</span>
                                <span className="df-app-hint">{app.hint}</span>
                            </Menu.Item>
                        ))}
                    </div>
                ) : <Text size="sm" c="dimmed" px="sm" py="snug">No apps linked</Text>}
            </Menu.Dropdown>
        </Menu>
    );
}

export function AccountMenu({onShortcuts}: { onShortcuts: () => void }) {
    const {data: me} = useMe();
    const {data: status} = useStatus();
    const initials = me?.name.split(/\s+/).map((part) => part[0]).join('').slice(0, INITIALS).toUpperCase() ?? '';
    return (
        <Menu position="bottom-end" width={240}>
            <Menu.Target>
                <IconAction label="Account" size={32} glyph={<Avatar size={AVATAR} radius="xl" color="brand">{initials}</Avatar>}/>
            </Menu.Target>
            <Menu.Dropdown>
                <Menu.Label>
                    <Text size="sm" fw={500} c={COLOR.text} truncate>{me?.name}</Text>
                    <Text size="xs" c="dimmed" truncate>{me?.email}</Text>
                    {!!me?.groups.length && <Text size="xs" c="dimmed" truncate>{me.groups.join(', ')}</Text>}
                </Menu.Label>
                <Menu.Divider/>
                <Menu.Item leftSection={<IconKeyboard {...ICON}/>} rightSection={<Kbd>?</Kbd>}
                           onClick={onShortcuts}>Keyboard shortcuts</Menu.Item>
                <Menu.Item leftSection={<IconLogout {...ICON}/>} disabled={!status?.signIn} onClick={signOut}>
                    Sign out
                </Menu.Item>
                {status?.version && (
                    <>
                        <Menu.Divider/>
                        <Menu.Label>DataFlow {status.version}</Menu.Label>
                    </>
                )}
            </Menu.Dropdown>
        </Menu>
    );
}
