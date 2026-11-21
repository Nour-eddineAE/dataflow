import {
    type Icon,
    IconActivity,
    IconBell,
    IconBucket,
    IconChartBar,
    IconClock,
    IconDatabase,
    IconFileCode,
    IconFolders,
    IconHeartbeat,
    IconHome,
    IconRoute,
    IconSettings,
    IconShieldCheck,
    IconTerminal2,
} from '../icons';
import {PAGE} from './links';

export type Destination = { to: string; label: string; icon: Icon; keywords?: string };
export type NavigationGroup = { title?: string; items: Destination[] };

export const NAVIGATION: NavigationGroup[] = [
    {
        items: [
            {to: PAGE.home, label: 'Home', icon: IconHome},
            {to: PAGE.workspace, label: 'Workspace', icon: IconFolders, keywords: 'files folders git'},
            {to: PAGE.catalog, label: 'Catalog', icon: IconDatabase},
        ],
    },
    {
        title: 'SQL',
        items: [
            {to: PAGE.sql, label: 'SQL editor', icon: IconTerminal2},
            {to: PAGE.queries, label: 'Queries', icon: IconFileCode},
            {to: PAGE.dashboards, label: 'Dashboards', icon: IconChartBar},
            {to: PAGE.alerts, label: 'Alerts', icon: IconBell},
        ],
    },
    {
        title: 'Data Engineering',
        items: [
            {to: PAGE.pipelines, label: 'Pipelines', icon: IconRoute},
            {to: PAGE.runs, label: 'Runs', icon: IconActivity},
            {to: PAGE.automation, label: 'Automation', icon: IconClock, keywords: 'schedules sensors triggers'},
            {to: PAGE.quality, label: 'Quality', icon: IconShieldCheck},
        ],
    },
];

export const PLATFORM: Destination[] = [
    {to: PAGE.services, label: 'Services', icon: IconHeartbeat, keywords: 'health'},
    {to: PAGE.settings, label: 'Settings', icon: IconSettings, keywords: 'theme preferences'},
];

const UNLISTED: Destination[] = [{to: PAGE.storage, label: 'Storage', icon: IconBucket, keywords: 'files buckets object store'}];

export const DESTINATIONS: Destination[] = [...NAVIGATION.flatMap((group) => group.items), ...PLATFORM, ...UNLISTED];

const segment = (path: string) => path.split(/[/?]/).filter(Boolean)[0] ?? '';

export function pageName(path: string): string {
    const first = segment(path);
    return DESTINATIONS.find((destination) => destination.to !== PAGE.home && segment(destination.to) === first)?.label ?? first;
}
