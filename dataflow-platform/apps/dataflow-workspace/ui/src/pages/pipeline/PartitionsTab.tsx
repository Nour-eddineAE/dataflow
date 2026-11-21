import {Group, SimpleGrid, Text, Tooltip, UnstyledButton} from '@mantine/core';
import {useState} from 'react';
import {useBackfill, usePartitions} from '../../api/hooks';
import {ErrorState, Loading} from '../../components/ErrorState';
import {Panel} from '../../components/Panel';
import {countStatuses, statusTone} from '../../lib/status';
import {Action} from '../../components/actions/Action';
import {COLOR, RADIUS} from '../../lib/colors';
import {notifyDone, notifyFailure} from '../../components/notify';
import {StatusIcon} from '../../components/StatusBadge';
import {notRun} from '../../lib/partitions';
import {ICON_SMALL, ICON_TINY} from '../../lib/icons';

export function PartitionsTab({dataset}: { dataset: string }) {
    const {data, error, isLoading} = usePartitions(dataset);
    const backfill = useBackfill();
    const [chosen, setChosen] = useState<string[]>([]);

    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data?.partitioned) return null;

    const toggle = (key: string) => setChosen((current) => (current.includes(key) ? current.filter((entry) => entry !== key) : [...current, key]));
    const missing = notRun(data.partitions);
    const submit = () =>
        backfill.mutate(
            {dataset, partitions: chosen},
            {
                onSuccess: () => {
                    notifyDone({message: `Backfill of ${chosen.length} partitions queued`});
                    setChosen([]);
                },
                onError: notifyFailure('Backfill not queued'),
            },
        );

    return (
        <Panel
            title={`${data.partitions.length} partitions`}
            actions={
                <>
                    <Action size="sm" label={chosen.length ? 'Clear' : 'Select missing'} disabled={!missing.length && !chosen.length}
                            onClick={() => setChosen(chosen.length ? [] : missing)}/>
                    <Action size="sm" tone="primary" label={`Backfill${chosen.length ? ` ${chosen.length}` : ''}`}
                            disabled={!chosen.length} loading={backfill.isPending} onClick={submit}/>
                </>
            }
        >
            <Group gap="lg" mb="sm">
                {countStatuses(data.partitions.map((partition) => partition.status)).map((count) => (
                    <Group key={count.label} gap="snug" wrap="nowrap">
                        <StatusIcon status={count.status} size={ICON_SMALL.size}/>
                        <Text size="sm"><span className="df-num">{count.count}</span> {count.label.toLowerCase()}</Text>
                    </Group>
                ))}
            </Group>
            <SimpleGrid cols={{base: 4, sm: 8, lg: 12}} spacing="snug" verticalSpacing="snug">
                {data.partitions.map((partition) => {
                    const picked = chosen.includes(partition.key);
                    const tone = statusTone(partition.status);
                    return (
                        <Tooltip key={partition.key} label={tone.label}>
                            <UnstyledButton onClick={() => toggle(partition.key)} h={34} aria-pressed={picked}
                                            aria-label={`${partition.key}: ${tone.label}`}
                                            style={{
                                                border: `1px solid ${picked ? COLOR.primary : COLOR.border}`,
                                                background: picked ? COLOR.active : undefined,
                                                borderRadius: RADIUS.sm,
                                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--mantine-spacing-snug)',
                                            }}>
                                <StatusIcon status={partition.status} size={ICON_TINY.size}/>
                                <Text fz="xs" className="df-num">{partition.key}</Text>
                            </UnstyledButton>
                        </Tooltip>
                    );
                })}
            </SimpleGrid>
        </Panel>
    );
}
