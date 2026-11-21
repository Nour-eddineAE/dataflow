import {createContext, useContext} from 'react';
import type {RunningTask} from './TaskOutput';

export const TaskTarget = createContext<((task: RunningTask) => void) | undefined>(undefined);

export function useTaskTarget() {
    const show = useContext(TaskTarget);
    if (!show) throw new Error('A task was started outside the file editor, which shows its output; render it inside TaskTarget.');
    return show;
}
