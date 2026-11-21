import {Anchor, Group, Table, Text, Tooltip} from '@mantine/core';
import {Link} from 'react-router-dom';
import type {QualityRule} from '../api/types';
import {formatAgo, formatNumber, shortRunId} from '../lib/format';
import {links} from '../lib/links';
import {FailureTrend} from './FailureTrend';
import {NumberCell} from './NumberCell';
import {StatusBadge} from './StatusBadge';
import {ruleStatus} from '../lib/quality';
import {COLOR} from '../lib/colors';

const ACTIONS: Record<string, string> = {warn: 'Warn', drop: 'Drop row', fail: 'Fail run'};

export const actionLabel = (action: string) => ACTIONS[action] ?? action;

export const failureColor = (action: string) => (action === 'warn' ? COLOR.warning : COLOR.danger);

export const failingColor = (action: string, failing?: number) => (failing ? failureColor(action) : undefined);

export function QualityRules({rules, showDataset = false}: { rules: QualityRule[]; showDataset?: boolean }) {
    return (
        <Table.ScrollContainer minWidth={820}>
            <Table>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th w={130}>Status</Table.Th>
                        <Table.Th>Rule</Table.Th>
                        {showDataset && <Table.Th>Pipeline</Table.Th>}
                        <Table.Th>On failure</Table.Th>
                        <Table.Th ta="right">Failing rows</Table.Th>
                        <Table.Th ta="right">Checked rows</Table.Th>
                        <Table.Th w={120}>Trend</Table.Th>
                        <Table.Th>Last checked</Table.Th>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {rules.map((rule) => (
                        <Table.Tr key={`${rule.dataset}.${rule.name}`}>
                            <Table.Td><StatusBadge status={ruleStatus(rule)}/></Table.Td>
                            <Table.Td maw={440}>
                                <Text size="sm">{rule.name}</Text>
                                <Text size="xs" c="dimmed" className="df-mono" truncate>{rule.check}</Text>
                            </Table.Td>
                            {showDataset && (
                                <Table.Td>
                                    <Anchor component={Link} to={links.pipeline(rule.dataset, 'quality')} size="sm"
                                            className="df-link">{rule.dataset}</Anchor>
                                </Table.Td>
                            )}
                            <Table.Td>
                                <Group gap="snug" wrap="nowrap">
                                    <Text size="sm">{actionLabel(rule.action)}</Text>
                                    {rule.action === 'drop' && (rule.latest?.failing ?? 0) > 0 && (
                                        <Tooltip label="Quarantined rows">
                                            <Anchor component={Link}
                                                    to={`${links.table(rule.dataset, 'quarantine')}?tab=sample`}
                                                    size="xs" className="df-link">View</Anchor>
                                        </Tooltip>
                                    )}
                                </Group>
                            </Table.Td>
                            <Table.Td ta="right">
                                <Text size="sm" className="df-num" c={failingColor(rule.action, rule.latest?.failing)}>
                                    {rule.latest ? formatNumber(rule.latest.failing) : '—'}
                                </Text>
                            </Table.Td>
                            <NumberCell>{rule.latest ? formatNumber(rule.latest.evaluated) : '—'}</NumberCell>
                            <Table.Td><FailureTrend runs={rule.history} color={failureColor(rule.action)}/></Table.Td>
                            <Table.Td>
                                {rule.latest ? (
                                    <Tooltip label={`Run ${shortRunId(rule.latest.runId)}`}>
                                        <Anchor component={Link} to={links.run(rule.latest.runId)} size="sm"
                                                className="df-link">{formatAgo(rule.latest.at)}</Anchor>
                                    </Tooltip>
                                ) : <Text size="sm" c="dimmed">Never</Text>}
                            </Table.Td>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </Table.ScrollContainer>
    );
}
