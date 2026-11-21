import {notifications} from '@mantine/notifications';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ApiError} from '../api/client';
import {COLOR} from '../lib/colors';
import {NOTICE} from '../lib/timing';
import {failureMessage, notifyDone, notifyFailure, notifyWarning} from './notify';

vi.mock('@mantine/notifications', () => ({notifications: {show: vi.fn()}}));

const show = vi.mocked(notifications.show);

describe('notify', () => {
    beforeEach(() => show.mockClear());

    it('shows a failure in the danger colour for the error time', () => {
        notifyFailure('Not saved')(new Error('disk full'));

        expect(show).toHaveBeenCalledWith({title: 'Not saved', message: 'disk full', color: COLOR.danger, autoClose: NOTICE.error});
    });

    it('lists every problem an API failure carries', () => {
        expect(failureMessage(new ApiError('Bad request', 400, [{message: 'name is empty'}, {message: 'key is missing'}])))
            .toBe('name is empty\nkey is missing');
    });

    it('falls back to the message when an API failure has no problems', () => {
        expect(failureMessage(new ApiError('Forbidden', 403, []))).toBe('Forbidden');
    });

    it('shows a warning in the warning colour', () => {
        notifyWarning({message: 'Saved as a draft with 2 problems'});

        expect(show).toHaveBeenCalledWith({message: 'Saved as a draft with 2 problems', color: COLOR.warning, autoClose: NOTICE.brief});
    });

    it('shows a success without a status colour', () => {
        notifyDone({title: 'Released abc1234', message: '2 pipelines use it'});

        expect(show).toHaveBeenCalledWith({title: 'Released abc1234', message: '2 pipelines use it', autoClose: NOTICE.brief});
    });
});
