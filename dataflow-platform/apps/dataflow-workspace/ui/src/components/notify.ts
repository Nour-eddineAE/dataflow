import {notifications} from '@mantine/notifications';
import {ApiError} from '../api/client';
import {COLOR} from '../lib/colors';
import {NOTICE} from '../lib/timing';

export type Note = { title?: string; message: string };

export function failureMessage(error: Error) {
    return error instanceof ApiError && error.problems?.length
        ? error.problems.map((problem) => problem.message).join('\n')
        : error.message;
}

export function notifyDone(note: Note) {
    notifications.show({...note, autoClose: NOTICE.brief});
}

export function notifyWarning(note: Note) {
    notifications.show({...note, color: COLOR.warning, autoClose: NOTICE.brief});
}

export function notifyFailure(title?: string) {
    return (error: Error) => notifications.show({title, message: failureMessage(error), color: COLOR.danger, autoClose: NOTICE.error});
}
