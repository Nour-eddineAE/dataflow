import {Anchor, Group, Stack, Text} from '@mantine/core';
import {Link} from 'react-router-dom';
import type {Change, DraftCheck} from '../../api/types';
import {Empty, Loading} from '../../components/ErrorState';
import {tableOfNode} from '../../lib/links';
import {COLOR, HAIRLINE} from '../../lib/colors';

const DOT = 7;

const SEVERITY: Record<Change['severity'], { label: string; color: string }> = {
    RISKY: {label: 'Breaking', color: COLOR.danger},
    DATA: {label: 'Changes data', color: COLOR.warning},
    SAFE: {label: 'Safe', color: COLOR.success},
    INFO: {label: 'No effect', color: COLOR.dimmed},
};

export function ImpactTab({dataset, result}: { dataset: string; result?: DraftCheck }) {
    if (!result) return <Loading/>;
    if (!result.valid) return <Empty title="Fix the problems first"/>;
    if (!result.impact.length) return <Empty title="No changes"/>;
    return (
        <Stack gap={0}>
            {result.impact.map((change, index) => {
                const severity = SEVERITY[change.severity];
                return (
                    <Stack key={index} gap="tight" py="sm" style={{borderBottom: HAIRLINE}}>
                        <Group justify="space-between" wrap="nowrap" align="flex-start">
                            <Text size="sm" fw={500}>{change.title}</Text>
                            <Group gap="snug" wrap="nowrap" style={{flex: 'none'}}>
                                <span style={{width: DOT, height: DOT, borderRadius: '50%', background: severity.color}}/>
                                <Text size="xs" c="dimmed">{severity.label}</Text>
                            </Group>
                        </Group>
                        <Text size="sm" c="dimmed">{change.detail}</Text>
                        {change.tables.length > 0 && (
                            <Group gap="xs">
                                {change.tables.map((table) => (
                                    <Anchor key={table} component={Link} to={tableOfNode(dataset, table)} size="xs"
                                            className="df-link">{table}</Anchor>
                                ))}
                            </Group>
                        )}
                    </Stack>
                );
            })}
        </Stack>
    );
}
