import type {RunRuntime} from '../api/types';

const RELEASE = /^(?<major>\d+)\.(?<minor>\d+)/;

export function runtimeLabel(runtime: RunRuntime): string {
    const release = RELEASE.exec(runtime.dataflow)?.groups;
    return `${release ? `${release.major}.${release.minor}` : runtime.dataflow} · Spark ${runtime.spark}`;
}

export function runtimeDetail(runtime: RunRuntime): string {
    return `DataFlow ${runtime.dataflow} · Spark ${runtime.spark} · Delta ${runtime.delta} · Scala ${runtime.scala} · Java ${runtime.java}`;
}
