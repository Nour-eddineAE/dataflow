import {Badge, Grid, Group, Stack, Table, Text} from '@mantine/core';
import {useDeltaDetails} from '../../api/hooks';
import {ErrorState, Loading} from '../../components/ErrorState';
import {Fact} from '../../components/Fact';
import {Panel} from '../../components/Panel';
import {formatTime} from '../../lib/format';

export function DetailsTab({dataset, tableId, location}: { dataset: string; tableId: string; location: string }) {
    const {data, error, isLoading} = useDeltaDetails(dataset, tableId);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data) return null;
    const properties = Object.entries(data.properties);
    return (
        <Grid gap="lg">
            <Grid.Col span={{base: 12, lg: 5}}>
                <Panel title="Table">
                    <Stack gap="sm">
                        <Fact label="Format">Delta</Fact>
                        <Fact label="Created">{data.createdAt ? formatTime(data.createdAt) : '—'}</Fact>
                        <Fact label="Clustered by"><Names names={data.clusteringColumns}/></Fact>
                        <Fact label="Partitioned by"><Names names={data.partitionColumns}/></Fact>
                        <Fact label="Protocol">
                            {data.minReaderVersion ? `Reader ${data.minReaderVersion} · Writer ${data.minWriterVersion}` : '—'}
                        </Fact>
                        <Fact label="Features">
                            {data.features.length ? (
                                <Group gap="tight">{data.features.map((feature) => <Badge key={feature} variant="default"
                                                                                      size="sm" radius="sm"
                                                                                      tt="none">{feature}</Badge>)}</Group>
                            ) : '—'}
                        </Fact>
                        <Fact label="Location"><Text size="sm" style={{wordBreak: 'break-all'}}>{location}</Text></Fact>
                    </Stack>
                </Panel>
            </Grid.Col>
            <Grid.Col span={{base: 12, lg: 7}}>
                <Panel title="Properties" flush>
                    {properties.length ? (
                        <Table>
                            <Table.Tbody>
                                {properties.map(([key, value]) => (
                                    <Table.Tr key={key}>
                                        <Table.Td><Text size="sm" className="df-mono" fz="xs">{key}</Text></Table.Td>
                                        <Table.Td maw={0} w="60%"><Text size="sm" className="df-mono" fz="xs" c="dimmed" truncate title={value}>{value}</Text></Table.Td>
                                    </Table.Tr>
                                ))}
                            </Table.Tbody>
                        </Table>
                    ) : <Text size="sm" c="dimmed" p="md">No properties</Text>}
                </Panel>
            </Grid.Col>
        </Grid>
    );
}

function Names({names}: { names: string[] }) {
    return names.length ? <Text size="sm" className="df-mono" fz="xs">{names.join(', ')}</Text> : <>—</>;
}
