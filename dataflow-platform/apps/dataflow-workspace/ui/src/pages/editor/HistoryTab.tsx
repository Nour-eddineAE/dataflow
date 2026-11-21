import {Stack, Text} from '@mantine/core';
import type {SpecCommit} from '../../api/types';
import {Empty, Loading} from '../../components/ErrorState';
import {formatTime, shortCommit} from '../../lib/format';
import {HAIRLINE} from '../../lib/colors';

export function HistoryTab({log}: { log?: SpecCommit[] }) {
    if (!log) return <Loading/>;
    if (!log.length) return <Empty title="Never committed"/>;
    return (
        <Stack gap={0}>
            {log.map((commit) => (
                <Stack key={commit.id} gap={2} py="sm" style={{borderBottom: HAIRLINE}}>
                    <Text size="sm">{commit.message.split('\n')[0]}</Text>
                    <Text size="xs" c="dimmed"><span
                        className="df-mono">{shortCommit(commit.id)}</span> · {commit.author} · {formatTime(commit.time)}
                    </Text>
                </Stack>
            ))}
        </Stack>
    );
}
