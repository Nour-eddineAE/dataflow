import type {Trigger} from '../api/types';
import {describeTrigger} from './format';

const KINDS: Trigger['kind'][] = ['SCHEDULE', 'INPUTS', 'PLATFORM'];

export const triggerWhen = (trigger: Trigger) =>
    trigger.kind === 'PLATFORM' ? 'Platform check' : describeTrigger(trigger.cron ?? undefined, trigger.kind === 'INPUTS');

export const sortTriggers = (triggers: Trigger[]) =>
    [...triggers].sort((a, b) =>
        KINDS.indexOf(a.kind) - KINDS.indexOf(b.kind) || a.name.localeCompare(b.name));
