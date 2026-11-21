import {describe, expect, it} from 'vitest';
import {runtimeDetail, runtimeLabel} from './runtime';

const RUNTIME = {dataflow: '3.0.0-SNAPSHOT', spark: '4.2.0', delta: '4.4.1', scala: '2.13.16', java: '21.0.7'};

describe('runtime', () => {
    it('names the release and Spark in short, every version in full', () => {
        expect(runtimeLabel(RUNTIME)).toBe('3.0 · Spark 4.2.0');
        expect(runtimeDetail(RUNTIME)).toBe('DataFlow 3.0.0-SNAPSHOT · Spark 4.2.0 · Delta 4.4.1 · Scala 2.13.16 · Java 21.0.7');
    });

    it('keeps a version it cannot shorten', () => {
        expect(runtimeLabel({...RUNTIME, dataflow: 'unknown'})).toBe('unknown · Spark 4.2.0');
    });
});
