import {Table, Text} from '@mantine/core';
import {ViewModal} from '../../components/FormModal';
import {IconChecklist} from '../../icons';
import {useState} from 'react';
import {usePreflight} from '../../api/hooks';
import {ToolbarButton} from '../../components/actions/ToolbarButton';
import {StatusBadge} from '../../components/StatusBadge';
import {notifyFailure} from '../../components/notify';
import {statusTone} from '../../lib/status';

export function PreflightButton({folderId, dataset, text}: { folderId: string; dataset: string; text: string }) {
    const preflight = usePreflight();
    const [opened, setOpened] = useState(false);
    const findings = preflight.data ?? [];
    const failed = findings.filter((finding) => statusTone(finding.verdict).tone === 'danger').length;
    const warned = findings.filter((finding) => statusTone(finding.verdict).tone === 'warning').length;
    const run = () => preflight.mutate({dataset, content: text, folderId}, {
        onSuccess: () => setOpened(true),
        onError: notifyFailure('Not validated'),
    });
    return (
        <>
            <ToolbarButton label="Validate" icon={IconChecklist} hint="Check the spec, its sources and the services it uses"
                           loading={preflight.isPending} onClick={run}/>
            <ViewModal opened={opened} onClose={() => setOpened(false)} title={`Validate ${dataset}`}>
                <Text size="sm" c="dimmed" mb="sm">
                    {failed ? `${failed} to fix before it can run` : warned ? 'Can run; look at the warnings' : 'Ready to run'}
                </Text>
                <Table verticalSpacing="snug">
                    <Table.Tbody>
                        {findings.map((finding) => (
                            <Table.Tr key={finding.subject}>
                                <Table.Td w={110}><StatusBadge status={finding.verdict}/></Table.Td>
                                <Table.Td w={200}><Text size="sm" fw={500}>{finding.subject}</Text></Table.Td>
                                <Table.Td><Text size="sm">{finding.detail}</Text></Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            </ViewModal>
        </>
    );
}
