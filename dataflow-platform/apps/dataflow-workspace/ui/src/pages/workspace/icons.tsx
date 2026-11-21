import {Text} from '@mantine/core';
import {
    IconBrandGit,
    IconCode,
    IconFile,
    IconFileText,
    IconFolder,
    IconHome,
    IconRoute,
    IconUsers,
    IconUsersGroup,
} from '../../icons';
import type {FileState} from '../../api/types';
import {PIPELINE_FILE} from '../../lib/names';
import type {Item} from '../../lib/workspace';
import {COLOR} from '../../lib/colors';

const ICON = {size: 15, stroke: 1.6};

export function itemIcon(item: Item) {
    switch (item.kind) {
        case 'home':
            return <IconHome {...ICON} />;
        case 'shared':
            return <IconUsers {...ICON} />;
        case 'users':
            return <IconUsersGroup {...ICON} />;
        case 'git':
            return <IconBrandGit {...ICON} />;
        case 'dir':
            return <IconFolder {...ICON} />;
        default:
            return fileIcon(item.name);
    }
}

function fileIcon(name: string) {
    if (name === PIPELINE_FILE) return <IconRoute {...ICON} />;
    if (name === 'dataflow.yml') return <IconFileText {...ICON} />;
    if (name.endsWith('.java') || name.endsWith('.py') || name.endsWith('.sql')) return <IconCode {...ICON} />;
    if (name.endsWith('.md')) return <IconFileText {...ICON} />;
    return <IconFile {...ICON} />;
}

export function itemType(item: Item) {
    switch (item.kind) {
        case 'git':
            return 'Git folder';
        case 'file':
            if (item.name === PIPELINE_FILE) return 'Pipeline';
            if (item.name === 'dataflow.yml') return 'Project';
            if (item.name.endsWith('.java')) return 'Transformation';
            return 'File';
        default:
            return 'Folder';
    }
}

const MARKS: Record<FileState, { letter: string; word: string; color: string } | undefined> = {
    UNCHANGED: undefined,
    MODIFIED: {letter: 'M', word: 'Modified', color: COLOR.warning},
    ADDED: {letter: 'A', word: 'Added', color: COLOR.success},
    DELETED: {letter: 'D', word: 'Deleted', color: COLOR.danger},
};

export function StateMark({state, word}: { state?: FileState; word?: boolean }) {
    const mark = state ? MARKS[state] : undefined;
    if (!mark) return null;
    return (
        <Text span size="xs" fw={600} c={mark.color} className={word ? undefined : 'df-mono'} style={{flex: 'none'}}>
            {word ? mark.word : mark.letter}
        </Text>
    );
}
