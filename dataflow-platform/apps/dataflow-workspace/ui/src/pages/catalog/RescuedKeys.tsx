import {Box, Group, Table, Text} from '@mantine/core';
import {useNavigate} from 'react-router-dom';
import {useEditFile, useEditLink, useRescued} from '../../api/hooks';
import {Panel} from '../../components/Panel';
import {IconAction} from '../../components/actions/IconAction';
import {IconCopy, IconPlus} from '../../icons';
import {formatNumber} from '../../lib/format';
import {addSilverColumn} from '../../lib/silverColumns';
import {notifyDone, notifyFailure} from '../../components/notify';

const ACTIONS_WIDTH = 72;

export function RescuedKeys({dataset}: { dataset: string }) {
    const {data} = useRescued(dataset, true);
    const {folderId, file, to} = useEditLink(dataset);
    if (!data?.length) return null;
    return (
        <Box mt="lg">
            <Panel title="Rescued" flush>
                <Table>
                    <Table.Tbody>
                        {data.map((key) => (
                            <Table.Tr key={key.key}>
                                <Table.Td>
                                    <Text size="sm" className="df-mono">{key.key}</Text>
                                    <Text fz="caption"
                                          c="dimmed">{key.known ? 'values that did not fit the type' : 'not in the spec'} · {formatNumber(key.rows)} rows</Text>
                                </Table.Td>
                                <Table.Td><Text size="xs" c="dimmed" className="df-mono"
                                                lineClamp={1}>{key.sample}</Text></Table.Td>
                                <Table.Td w={ACTIONS_WIDTH}>
                                    {key.suggestion && (
                                        <Group gap={2} wrap="nowrap" justify="flex-end">
                                            {folderId && file && to && <AddToDraft column={key.suggestion} folderId={folderId} file={file} to={to}/>}
                                            <IconAction label="Copy a silver column for the spec" icon={IconCopy}
                                                        onClick={() => navigator.clipboard.writeText(key.suggestion!).then(() => notifyDone({message: 'Column copied'}))}/>
                                        </Group>
                                    )}
                                </Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            </Panel>
        </Box>
    );
}

function AddToDraft({column, folderId, file, to}: { column: string; folderId: string; file: string; to: string }) {
    const navigate = useNavigate();
    const edit = useEditFile(folderId);
    const add = () => edit.mutate({
        file,
        change: (content) => {
            const result = addSilverColumn(content, column);
            if ('problem' in result) throw new Error(result.problem);
            return result.text;
        },
    }, {
        onSuccess: () => {
            notifyDone({message: 'Column added to the draft'});
            navigate(to);
        },
        onError: notifyFailure('Not added'),
    });
    return <IconAction label="Add to draft spec" icon={IconPlus} onClick={add}/>;
}
