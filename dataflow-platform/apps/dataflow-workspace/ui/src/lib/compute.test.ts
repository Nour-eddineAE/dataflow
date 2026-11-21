import {describe, expect, it} from 'vitest';
import type {Compute} from '../api/types';
import {driverSummary, executorSummary, formProblems, fromForm, fromSpec, mebibytes, policySummary, sparkSummary, type SpecCompute, toForm, toSpec} from './compute';

const SIZED: Compute = {
    executors: {min: 1, max: 4},
    driverMemory: '2g',
    executorCores: 2,
    sparkConf: {'spark.sql.shuffle.partitions': '16', 'spark.sql.parquet.filterPushdown': 'false'},
};

describe('compute form', () => {
    it('round-trips a compute and keeps Spark settings it does not show', () => {
        const form = toForm(SIZED);

        expect(form).toMatchObject({driverMemory: '2g', minExecutors: 1, maxExecutors: 4, shufflePartitions: 16, adaptive: ''});
        expect(fromForm({...form, shufflePartitions: '', adaptive: 'false', driverMemory: ' 3G '}, SIZED)).toEqual({
            executors: {min: 1, max: 4},
            driverMemory: '3g',
            executorMemory: null,
            driverCores: null,
            executorCores: 2,
            sparkConf: {'spark.sql.parquet.filterPushdown': 'false', 'spark.sql.adaptive.enabled': 'false'},
        });
    });

    it('turns the Spark event log on, off or back to the install default', () => {
        const on = fromForm({...toForm(SIZED), eventLog: 'true'}, SIZED);

        expect(on.sparkConf).toMatchObject({'spark.eventLog.enabled': 'true'});
        expect(toForm(on).eventLog).toBe('true');
        expect(toForm(fromForm({...toForm(on), eventLog: 'false'}, on)).eventLog).toBe('false');
        expect(fromForm({...toForm(on), eventLog: ''}, on).sparkConf).not.toHaveProperty(['spark.eventLog.enabled']);
    });

    it('leaves empty fields to the install default', () => {
        expect(fromForm(toForm({}), {})).toEqual({
            executors: null, driverMemory: null, executorMemory: null, driverCores: null, executorCores: null, sparkConf: {},
        });
    });

    it('names a bad size and a minimum above the maximum', () => {
        expect(formProblems({...toForm({}), driverMemory: 'lots', minExecutors: 5, maxExecutors: 2})).toEqual({
            driverMemory: 'A size such as 512m or 4g',
            minExecutors: 'More than the maximum',
        });
    });

    it('summarises a size in a few words', () => {
        expect(driverSummary(SIZED)).toBe('2g');
        expect(executorSummary(SIZED)).toBe('1–4 × 2 cores');
        expect(executorSummary({executors: {max: 6}})).toBe('up to 6');
        expect(executorSummary({})).toBeNull();
        expect(sparkSummary(SIZED)).toEqual(['shuffle.partitions 16', 'parquet.filterPushdown false']);
    });

    it('refuses sizes above the install policy and names the limit', () => {
        const policy = {maxDriverMemory: '2g', maxDriverCores: 2, maxExecutors: 8, maxExecutorMemory: '4g'};
        const form = {...toForm({}), driverMemory: '2049m', driverCores: 3, minExecutors: 9, maxExecutors: 12, executorMemory: '4g'} as const;

        expect(formProblems(form, policy)).toEqual({
            driverMemory: 'At most 2g on this install',
            driverCores: 'At most 2 on this install',
            minExecutors: 'At most 8 on this install',
            maxExecutors: 'At most 8 on this install',
        });
        expect(formProblems({...form, driverMemory: '1800m', driverCores: 2, minExecutors: 1, maxExecutors: 8}, policy)).toEqual({});
        expect(mebibytes('2g')).toBe(2048);
        expect(policySummary(policy)).toEqual(['driver up to 2g', 'driver up to 2 cores', 'up to 8 executors', 'executors up to 4g']);
        expect(policySummary({})).toEqual([]);
    });
});

describe('compute in a pipeline spec', () => {
    const OWN: SpecCompute = {
        executors: {max: 4},
        'driver-memory': '2g',
        'driver-cores': 2,
        'spark-conf': {'spark.sql.parquet.filterPushdown': 'false'},
    };

    it('reads and writes the spec keys, keeping settings the form does not show', () => {
        expect(toSpec(fromForm(toForm(fromSpec(OWN)), fromSpec(OWN)))).toEqual(OWN);
    });

    it('reads YAML booleans and numbers in spark-conf as the form expects', () => {
        const yaml = {'spark-conf': {'spark.sql.adaptive.enabled': true, 'spark.sql.shuffle.partitions': 8}} as unknown as SpecCompute;

        expect(toForm(fromSpec(yaml))).toMatchObject({adaptive: 'true', shufflePartitions: 8});
    });

    it('removes compute from the spec when every field is empty', () => {
        expect(fromSpec(undefined)).toEqual({sparkConf: {}});
        expect(toSpec(fromForm(toForm({}), {}))).toBeUndefined();
    });
});
