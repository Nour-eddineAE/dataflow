import {Group, Kbd, Stack, Text} from '@mantine/core';
import {ViewModal} from './FormModal';
import {Fragment} from 'react';

const TAB_OUT: [string[], string] = [['⌃', '⇧', 'M'], 'Let Tab leave the editor, or indent again'];

const GROUPS: { title: string; keys: [string[], string][] }[] = [
    {title: 'Anywhere', keys: [[['⌘', 'K'], 'Search'], [['⌘', 'B'], 'Collapse or expand the sidebar'], [['?'], 'Keyboard shortcuts']]},
    {
        title: 'SQL editor',
        keys: [
            [['⌘', '↵'], 'Run the statement under the cursor, or the selection'],
            [['⌘', 'S'], 'Save the query'],
            [['⌘', 'Z'], 'Undo'],
            [['⌘', '⇧', 'Z'], 'Redo'],
            [['⇧', '⌥', 'F'], 'Format'],
            [['⌘', '.'], 'Quick fix'],
            [['⌘', '/'], 'Comment line'],
            [['⌃', 'Space'], 'Suggest tables and columns'],
            [['⌘', 'C'], 'Copy the selected result cell'],
            [['←', '→'], 'Move between query tabs'],
            [['⌫'], 'Close the focused query tab'],
            TAB_OUT,
        ],
    },
    {
        title: 'File editor',
        keys: [[['⌘', 'S'], 'Save'], [['⌘', 'Z'], 'Undo'], [['⌘', '⇧', 'Z'], 'Redo'], [['⌃', 'Space'], 'Suggest keys and values'], TAB_OUT],
    },
    {
        title: 'Pipeline designer',
        keys: [
            [['←', '→', '↑', '↓'], 'Move to the next block'],
            [['⌘', 'D'], 'Duplicate the block'],
            [['⌫'], 'Delete the block'],
        ],
    },
];

export function ShortcutsModal({opened, onClose}: { opened: boolean; onClose: () => void }) {
    return (
        <ViewModal opened={opened} onClose={onClose} title="Keyboard shortcuts" size="md">
            <Stack gap="lg">
                {GROUPS.map((group) => (
                    <Stack key={group.title} gap="cozy">
                        <Text size="xs" fw={500} c="dimmed">{group.title}</Text>
                        {group.keys.map(([keys, label]) => (
                            <Group key={label} justify="space-between" wrap="nowrap">
                                <Text size="sm">{label}</Text>
                                <Group gap="tight" wrap="nowrap">
                                    {keys.map((key, index) => (
                                        <Fragment key={key + index}><Kbd>{key}</Kbd></Fragment>
                                    ))}
                                </Group>
                            </Group>
                        ))}
                    </Stack>
                ))}
            </Stack>
        </ViewModal>
    );
}
