import {describe, expect, it} from 'vitest';
import type {SqlFunction} from '../api/types';
import {functionsStartingWith} from './functionMatch';

const fn = (name: string) => ({name, aggregate: false}) as SqlFunction;
const FUNCTIONS = [fn('max'), fn('MACStringToNum'), fn('magnitude'), fn('timeSeriesMaxToGrid')];

describe('functionsStartingWith', () => {
    it('offers only functions whose name starts with what was typed', () => {
        expect(functionsStartingWith(FUNCTIONS, 'mag').map((entry) => entry.name)).toEqual(['magnitude']);
    });

    it('ignores case', () => {
        expect(functionsStartingWith(FUNCTIONS, 'MA').map((entry) => entry.name)).toEqual(['max', 'MACStringToNum', 'magnitude']);
    });

    it('offers every function before anything is typed', () => {
        expect(functionsStartingWith(FUNCTIONS, '')).toHaveLength(4);
    });
});
