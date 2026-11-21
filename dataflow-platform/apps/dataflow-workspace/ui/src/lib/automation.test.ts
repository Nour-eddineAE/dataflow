import {describe, expect, it} from 'vitest';
import type {Trigger} from '../api/types';
import {sortTriggers, triggerWhen} from './automation';

const trigger = (name: string, kind: Trigger['kind'], cron?: string): Trigger =>
    ({name, kind, cron, running: true});

describe('automation', () => {
    it('says when each kind of trigger fires', () => {
        expect(triggerWhen(trigger('earthquakes_schedule', 'SCHEDULE', '0 */6 * * *'))).toBe('Every 6 h');
        expect(triggerWhen(trigger('zone_hours_on_inputs', 'INPUTS'))).toBe('When its inputs land');
        expect(triggerWhen(trigger('orphaned_runs', 'PLATFORM'))).toBe('Platform check');
    });

    it('lists pipelines before platform checks, by name within a kind', () => {
        const sorted = sortTriggers([
            trigger('orphaned_runs', 'PLATFORM'),
            trigger('zone_hours_on_inputs', 'INPUTS'),
            trigger('taxi_trips_schedule', 'SCHEDULE'),
            trigger('earthquakes_schedule', 'SCHEDULE'),
        ]);
        expect(sorted.map((item) => item.name)).toEqual([
            'earthquakes_schedule', 'taxi_trips_schedule', 'zone_hours_on_inputs', 'orphaned_runs']);
    });
});
