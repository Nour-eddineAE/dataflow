import type {Partitions} from '../api/types';

type Partition = Partitions['partitions'][number];

const MATERIALIZED = 'MATERIALIZED';

export const notRun = (partitions: Partition[]) =>
    partitions.filter((partition) => partition.status !== MATERIALIZED).map((partition) => partition.key);

export const newestNotRun = (partitions: Partition[]) => notRun(partitions).at(-1);
