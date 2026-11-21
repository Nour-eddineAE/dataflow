import type {Lineage} from '../api/types';

export function lineagePath(edges: Lineage['edges'], start?: string): Set<string> {
    const lit = new Set<string>();
    if (!start) return lit;
    const upstream = new Map<string, string[]>();
    const downstream = new Map<string, string[]>();
    edges.forEach((edge) => {
        const from = `${edge.fromTable}|${edge.fromColumn}`;
        const to = `${edge.toTable}|${edge.toColumn}`;
        downstream.set(from, [...(downstream.get(from) ?? []), to]);
        upstream.set(to, [...(upstream.get(to) ?? []), from]);
    });
    const walk = (links: Map<string, string[]>) => {
        const seen = new Set([start]);
        const queue = [start];
        for (let index = 0; index < queue.length; index += 1) {
            for (const next of links.get(queue[index]) ?? []) {
                if (seen.has(next)) continue;
                seen.add(next);
                queue.push(next);
            }
        }
        seen.forEach((key) => lit.add(key));
    };
    walk(upstream);
    walk(downstream);
    return lit;
}
