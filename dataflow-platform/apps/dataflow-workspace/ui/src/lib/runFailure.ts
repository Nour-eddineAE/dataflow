import {BEFORE_START, failurePlace, TOWER} from './failurePlace';
import {plural} from './format';

export type RunFailure = { title: string; exception?: string; inTower: boolean };

const EXCEPTION = /^([A-Za-z_][\w.]*(?:Error|Exception|Failure))\s*:/;

export function runFailure(flows: { flow: string; status: string; startedAt?: string }[], error: string): RunFailure {
    const exception = EXCEPTION.exec(error.trim())?.[1].split('.').pop();
    const place = failurePlace(flows);
    if (place === BEFORE_START) return {title: 'Failed before any flow started', exception, inTower: true};
    if (place === TOWER) return {title: `All ${plural(flows.length, 'flow')} finished; the run failed in the tower`, exception, inTower: true};
    return {title: `Failed in ${place}`, exception, inTower: false};
}
