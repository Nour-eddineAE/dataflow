import {Anchor, Box, Group, SimpleGrid, Stack, Text, Title} from '@mantine/core';
import {
    IconActivity,
    IconAlertTriangle,
    IconGitBranch,
    IconPlus,
    IconRoute,
    IconServer,
    IconShieldCheck
} from '../icons';
import {type ReactNode, useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {useAlerts, useCatalog, useDatasets, useFavorites, useQuality, useRuns, useSavedQueries, useServices, RUN_LIMIT} from '../api/hooks';
import {AlertsTable} from '../components/AlertsTable';
import {Empty, ErrorState} from '../components/ErrorState';
import {TableSkeleton} from '../components/TableSkeleton';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {PipelinesTable} from '../components/PipelinesTable';
import {RecentsTable} from '../components/RecentsTable';
import {FavoritesTable} from '../components/FavoritesTable';
import {favoriteEntry, type FavoriteEntry} from '../lib/favorites';
import {RunsTable} from '../components/RunsTable';
import {StatCard} from '../components/StatCard';
import {parseTime, plural} from '../lib/format';
import {StatusIcon} from '../components/StatusBadge';
import {recents} from '../lib/recents';
import {ICON_LARGE} from '../lib/icons';
import {Action} from '../components/actions/Action';
import {PAGE} from '../lib/links';
import {COLOR} from '../lib/colors';
import {Notice} from '../components/Notice';

const RECENTS = 6;
const STAT_ICON = {...ICON_LARGE, color: COLOR.dimmed};

const HOME_RUNS = 8;

export function HomePage() {
    const navigate = useNavigate();
    const datasets = useDatasets();
    const runs = useRuns(undefined, RUN_LIMIT.all);
    const quality = useQuality();
    const alerts = useAlerts();
    const services = useServices();
    const broken = (services.data ?? []).filter((service) => service.health === 'FAILED');
    const catalog = useCatalog();
    const [recent] = useState(() => recents().slice(0, RECENTS));
    const favorites = useFavorites();
    const saved = useSavedQueries();
    const starred = (favorites.data ?? [])
        .map((favorite) => favoriteEntry(favorite, saved.data ?? []))
        .filter((entry): entry is FavoriteEntry => !!entry);

    const midnight = new Date().setHours(0, 0, 0, 0);
    const today = (runs.data ?? []).filter((run) => (parseTime(run.createdAt)?.getTime() ?? 0) >= midnight);
    const failed = today.filter((run) => run.status === 'FAILURE').length;
    const failing = (quality.data ?? []).filter((rule) => (rule.latest?.failing ?? 0) > 0).length;

    return (
        <Page>
            <PageHeader title="Home"/>
            <SimpleGrid cols={{base: 2, md: 5}} mb="block">
                <StatCard label="Pipelines" value={datasets.data?.length ?? '—'} icon={<IconRoute {...STAT_ICON} />}
                          to={PAGE.pipelines}/>
                <StatCard label="Runs today" value={runs.data ? today.length : '—'} icon={<IconActivity {...STAT_ICON} />}
                          to={PAGE.runs}/>
                <StatCard label="Failed today" value={runs.data ? failed : '—'} to={PAGE.failedRuns}
                          icon={failed > 0 ? <StatusIcon status="FAILED" {...ICON_LARGE}/> : <IconAlertTriangle {...STAT_ICON} />}/>
                <StatCard label="Rules failing" value={quality.data ? failing : '—'} to={PAGE.quality}
                          icon={failing > 0 ? <StatusIcon status="FAILED" {...ICON_LARGE}/> : <IconShieldCheck {...STAT_ICON} />}/>
                <StatCard label="Served tables" value={catalog.data?.serving.length ?? '—'}
                          icon={<IconServer {...STAT_ICON} />} to={PAGE.catalog}/>
            </SimpleGrid>
            <Stack gap="block">
                {broken.length > 0 && (
                    <Notice title={`${plural(broken.length, 'service')} not healthy`}>
                        <Stack gap="tight">
                            {broken.map((service) => (
                                <Text key={service.id} size="sm"><b>{service.name}</b>: {service.detail}</Text>
                            ))}
                            <Anchor component={Link} to={PAGE.services} size="sm" className="df-link">Open services</Anchor>
                        </Stack>
                    </Notice>
                )}
                {!!alerts.data?.alerts.length && (
                    <Section title="Alerts">
                        <Panel flush><AlertsTable alerts={alerts.data.alerts}/></Panel>
                    </Section>
                )}
                {starred.length > 0 && (
                    <Section title="Favorites">
                        <Panel flush><FavoritesTable items={starred}/></Panel>
                    </Section>
                )}
                {recent.length > 0 && (
                    <Section title="Recents">
                        <Panel flush><RecentsTable items={recent}/></Panel>
                    </Section>
                )}
                <Section title="Pipelines" to={PAGE.pipelines}>
                    {datasets.isLoading ? <TableSkeleton/> : datasets.error ? <ErrorState error={datasets.error}/>
                        : !datasets.data?.length ? (
                            <Empty title="No pipelines yet" action={
                                <Group gap="xs">
                                    <Action icon={IconGitBranch} label="Connect a repository"
                                            onClick={() => navigate(PAGE.newGitFolder)}/>
                                    <Action tone="primary" icon={IconPlus} label="New pipeline" onClick={() => navigate(PAGE.newPipeline)}/>
                                </Group>
                            }/>
                        ) : <Panel flush><PipelinesTable datasets={datasets.data}/></Panel>}
                </Section>
                <Section title="Recent runs" to={PAGE.runs}>
                    {runs.isLoading ? <TableSkeleton/> : runs.error ? <ErrorState error={runs.error}/>
                        : !runs.data?.length ? <Empty title="No runs"/> :
                            <Panel flush><RunsTable runs={runs.data.slice(0, HOME_RUNS)}/></Panel>}
                </Section>
            </Stack>
        </Page>
    );
}

function Section({title, to, children}: { title: string; to?: string; children: ReactNode }) {
    return (
        <Box>
            <Group justify="space-between" mb="sm">
                <Title order={2} fz="lg">{title}</Title>
                {to && <Anchor component={Link} to={to} size="sm" className="df-link">View all</Anchor>}
            </Group>
            {children}
        </Box>
    );
}
