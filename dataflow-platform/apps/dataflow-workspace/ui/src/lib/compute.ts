import type {Compute, ComputePolicy} from '../api/types';

export const SHUFFLE = 'spark.sql.shuffle.partitions';
export const ADAPTIVE = 'spark.sql.adaptive.enabled';
const EVENT_LOG = 'spark.eventLog.enabled';
export const MEMORY = /^[1-9][0-9]*[mg]$/i;
export const SIZES = ['512m', '1g', '2g', '4g', '8g', '16g'];

export type Switch = '' | 'true' | 'false';

export type ComputeForm = {
    driverMemory: string;
    driverCores: number | '';
    minExecutors: number | '';
    maxExecutors: number | '';
    executorMemory: string;
    executorCores: number | '';
    shufflePartitions: number | '';
    adaptive: Switch;
    eventLog: Switch;
};

const DRIVER_MEMORY = 'driver-memory';
const DRIVER_CORES = 'driver-cores';
const EXECUTOR_MEMORY = 'executor-memory';
const EXECUTOR_CORES = 'executor-cores';
const SPARK_CONF = 'spark-conf';

export type SpecCompute = {
    executors?: { min?: number; max?: number };
    [DRIVER_MEMORY]?: string;
    [DRIVER_CORES]?: number;
    [EXECUTOR_MEMORY]?: string;
    [EXECUTOR_CORES]?: number;
    [SPARK_CONF]?: Record<string, string>;
};

export type ComputeProblems = Partial<Record<keyof ComputeForm, string>>;

const number = (value?: number | null): number | '' => (value == null ? '' : value);
const optional = (value: number | ''): number | null => (value === '' ? null : value);
const text = (value: string): string | null => (value.trim() === '' ? null : value.trim().toLowerCase());
const toSwitch = (value?: string): Switch => (value === 'true' || value === 'false' ? value : '');

export function toForm(compute: Compute): ComputeForm {
    const conf = compute.sparkConf ?? {};
    return {
        driverMemory: compute.driverMemory ?? '',
        driverCores: number(compute.driverCores),
        minExecutors: number(compute.executors?.min),
        maxExecutors: number(compute.executors?.max),
        executorMemory: compute.executorMemory ?? '',
        executorCores: number(compute.executorCores),
        shufflePartitions: conf[SHUFFLE] ? Number(conf[SHUFFLE]) : '',
        adaptive: toSwitch(conf[ADAPTIVE]),
        eventLog: toSwitch(conf[EVENT_LOG]),
    };
}

export function fromForm(form: ComputeForm, original: Compute): Compute {
    const conf: Record<string, string> = {...(original.sparkConf ?? {})};
    delete conf[SHUFFLE];
    delete conf[ADAPTIVE];
    delete conf[EVENT_LOG];
    if (form.shufflePartitions !== '') conf[SHUFFLE] = String(form.shufflePartitions);
    if (form.adaptive !== '') conf[ADAPTIVE] = form.adaptive;
    if (form.eventLog !== '') conf[EVENT_LOG] = form.eventLog;
    const executors = form.minExecutors === '' && form.maxExecutors === ''
        ? null
        : {min: optional(form.minExecutors), max: optional(form.maxExecutors)};
    return {
        executors,
        driverMemory: text(form.driverMemory),
        executorMemory: text(form.executorMemory),
        driverCores: optional(form.driverCores),
        executorCores: optional(form.executorCores),
        sparkConf: conf,
    };
}

export function fromSpec(compute: SpecCompute = {}): Compute {
    return {
        executors: compute.executors,
        driverMemory: compute[DRIVER_MEMORY],
        executorMemory: compute[EXECUTOR_MEMORY],
        driverCores: compute[DRIVER_CORES],
        executorCores: compute[EXECUTOR_CORES],
        sparkConf: Object.fromEntries(Object.entries(compute[SPARK_CONF] ?? {}).map(([key, value]) => [key, String(value)])),
    };
}

export function toSpec(compute: Compute): SpecCompute | undefined {
    const {min, max} = compute.executors ?? {};
    const conf = compute.sparkConf ?? {};
    const spec: SpecCompute = {
        ...(min != null || max != null ? {executors: {...(min != null && {min}), ...(max != null && {max})}} : {}),
        ...(compute.driverMemory && {[DRIVER_MEMORY]: compute.driverMemory}),
        ...(compute.driverCores != null && {[DRIVER_CORES]: compute.driverCores}),
        ...(compute.executorMemory && {[EXECUTOR_MEMORY]: compute.executorMemory}),
        ...(compute.executorCores != null && {[EXECUTOR_CORES]: compute.executorCores}),
        ...(Object.keys(conf).length > 0 && {[SPARK_CONF]: conf}),
    };
    return Object.keys(spec).length > 0 ? spec : undefined;
}

const MIB_PER_GIB = 1024;

const SIZE = /^(?<amount>\d+)(?<unit>[mg])$/i;

export function mebibytes(size: string): number {
    const parts = SIZE.exec(size.trim())?.groups;
    if (!parts) return Number.NaN;
    return Number(parts.amount) * (parts.unit.toLowerCase() === 'g' ? MIB_PER_GIB : 1);
}

export function formProblems(form: ComputeForm, policy: ComputePolicy = {}): ComputeProblems {
    const problems: ComputeProblems = {};
    const size = 'A size such as 512m or 4g';
    const memory = (key: 'driverMemory' | 'executorMemory', limit?: string | null) => {
        const value = form[key].trim();
        if (!value) return;
        if (!MEMORY.test(value)) problems[key] = size;
        else if (limit && mebibytes(value) > mebibytes(limit)) problems[key] = `At most ${limit} on this install`;
    };
    const count = (key: 'driverCores' | 'executorCores' | 'minExecutors' | 'maxExecutors', limit?: number | null) => {
        const value = form[key];
        if (value !== '' && limit != null && value > limit) problems[key] = `At most ${limit} on this install`;
    };
    memory('driverMemory', policy.maxDriverMemory);
    memory('executorMemory', policy.maxExecutorMemory);
    count('driverCores', policy.maxDriverCores);
    count('executorCores', policy.maxExecutorCores);
    count('minExecutors', policy.maxExecutors);
    count('maxExecutors', policy.maxExecutors);
    if (!problems.minExecutors && form.minExecutors !== '' && form.maxExecutors !== '' && form.minExecutors > form.maxExecutors) {
        problems.minExecutors = 'More than the maximum';
    }
    return problems;
}

export function policySummary(policy: ComputePolicy): string[] {
    return [
        policy.maxDriverMemory && `driver up to ${policy.maxDriverMemory}`,
        policy.maxDriverCores != null && `driver up to ${cores(policy.maxDriverCores)}`,
        policy.maxExecutors != null && `up to ${policy.maxExecutors} executors`,
        policy.maxExecutorMemory && `executors up to ${policy.maxExecutorMemory}`,
        policy.maxExecutorCores != null && `executors up to ${cores(policy.maxExecutorCores)}`,
    ].filter((part): part is string => !!part);
}

const cores = (value?: number | null) => (value == null ? null : `${value} ${value === 1 ? 'core' : 'cores'}`);

export function driverSummary(compute: Compute): string | null {
    return [compute.driverMemory, cores(compute.driverCores)].filter(Boolean).join(' · ') || null;
}

export function executorSummary(compute: Compute): string | null {
    const {min, max} = compute.executors ?? {};
    const count = min != null && max != null ? `${min}–${max}` : max != null ? `up to ${max}` : min != null ? `at least ${min}` : null;
    const each = [compute.executorMemory, cores(compute.executorCores)].filter(Boolean).join(', ');
    return [count, each && `× ${each}`].filter(Boolean).join(' ') || null;
}

export function sparkSummary(compute: Compute): string[] {
    return Object.entries(compute.sparkConf ?? {}).map(([key, value]) => `${key.replace(/^spark\.sql\./, '')} ${value}`);
}
