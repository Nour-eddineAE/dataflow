import {Anchor, Box, Group, Select, Stack, Table, Text, useComputedColorScheme} from '@mantine/core';
import {IconArrowLeft, IconChartBar, IconExternalLink} from '../icons';
import {useState} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {useDashboards, useDatasets, useStatus} from '../api/hooks';
import type {Dashboard} from '../api/types';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {ICON_LARGE} from '../lib/icons';
import {Hint} from '../components/Hint';
import {IconAction} from '../components/actions/IconAction';
import {PAGE, links} from '../lib/links';
import {COLOR, HAIRLINE} from '../lib/colors';
import {pageName} from '../lib/navigation';

const TITLE = pageName(PAGE.dashboards);

export function DashboardsPage() {
    const {uid} = useParams();
    const {data, error, isLoading} = useDashboards();
    const {data: datasets, isLoading: datasetsLoading} = useDatasets();
    const {data: status, isLoading: statusLoading} = useStatus();
    const base = status?.links.dashboards;

    if (isLoading || datasetsLoading || (uid && statusLoading)) return <Loading/>;
    if (error) return <Page><PageHeader title={TITLE}/><ErrorState error={error}/></Page>;
    const chosen = data?.find((dashboard) => dashboard.uid === uid);
    if (chosen && base) return <Board dashboard={chosen} base={base}/>;
    return (
        <Page>
            <PageHeader title={TITLE}/>
            <Stack gap="lg">
                <Panel title="Pipelines" flush>
                    {!datasets?.length ? <Empty title="No pipelines"/> : (
                        <Table>
                            <Table.Thead>
                                <Table.Tr>
                                    <Table.Th>Name</Table.Th>
                                </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                                {datasets.map((dataset) => (
                                    <Table.Tr key={dataset.dataset}>
                                        <Table.Td>
                                            <Group gap="xs" wrap="nowrap">
                                                <IconChartBar {...ICON_LARGE}
                                                              color={COLOR.dimmed}/>
                                                <Hint label={dataset.description}>
                                                    <Anchor component={Link} to={links.board(dataset.dataset)} size="sm"
                                                            className="df-link">{dataset.dataset}</Anchor>
                                                </Hint>
                                            </Group>
                                        </Table.Td>
                                    </Table.Tr>
                                ))}
                            </Table.Tbody>
                        </Table>
                    )}
                </Panel>
                {!!data?.length && (
                    <Panel title="Grafana" flush>
                        <Table>
                            <Table.Thead>
                                <Table.Tr>
                                    <Table.Th>Name</Table.Th>
                                    <Table.Th>Group</Table.Th>
                                </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                                {data.map((dashboard) => (
                                    <Table.Tr key={dashboard.uid}>
                                        <Table.Td>
                                            <Group gap="xs" wrap="nowrap">
                                                <IconChartBar {...ICON_LARGE}
                                                              color={COLOR.dimmed}/>
                                                <Hint label={dashboard.description}>
                                                    {base ?
                                                        <Anchor component={Link} to={links.grafanaBoard(dashboard.uid)}
                                                                size="sm" className="df-link">{dashboard.title}</Anchor>
                                                        : <Text size="sm">{dashboard.title}</Text>}
                                                </Hint>
                                            </Group>
                                        </Table.Td>
                                        <Table.Td><Text size="sm">{dashboard.group}</Text></Table.Td>
                                    </Table.Tr>
                                ))}
                            </Table.Tbody>
                        </Table>
                    </Panel>
                )}
            </Stack>
        </Page>
    );
}

function Board({dashboard, base}: { dashboard: Dashboard; base: string }) {
    const navigate = useNavigate();
    const scheme = useComputedColorScheme('light');
    const {data: datasets} = useDatasets();
    const [dataset, setDataset] = useState<string | null>(null);
    const variable = dashboard.datasetVariable && dataset ? `&var-${dashboard.datasetVariable}=${encodeURIComponent(dataset)}` : '';
    const root = `${base.replace(/\/$/, '')}/d/${dashboard.uid}`;
    const url = `${root}?kiosk&theme=${scheme}${variable}`;
    return (
        <Stack gap={0} h="100%">
            <Group justify="space-between" px="md" h={52} wrap="nowrap"
                   style={{borderBottom: HAIRLINE, flex: 'none'}}>
                <Group gap="xs" wrap="nowrap">
                    <IconAction label="Back" icon={IconArrowLeft} onClick={() => navigate(PAGE.dashboards)}/>
                    <Text fw={600}>{dashboard.title}</Text>
                </Group>
                <Group gap="xs" wrap="nowrap">
                    {dashboard.datasetVariable && (
                        <Select size="xs" placeholder="All pipelines"
                                data={(datasets ?? []).map((entry) => entry.dataset)} value={dataset}
                                onChange={setDataset} clearable w={200} aria-label="Pipeline"/>
                    )}
                    <IconAction label="Open in Grafana" icon={IconExternalLink} outlined href={`${root}?theme=${scheme}${variable}`}
                                external/>
                </Group>
            </Group>
            <Box style={{flex: 1}}>
                <iframe key={url} title={dashboard.title} src={url} style={{
                    border: 0,
                    width: '100%',
                    height: '100%',
                    display: 'block',
                    background: COLOR.surface
                }}/>
            </Box>
        </Stack>
    );
}

