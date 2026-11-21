import type {Lineage} from '../api/types';

export type FocusedLineage = { lineage: Lineage; hidden: Record<string, number> };

export function focusOn(lineage: Lineage, tableId: string): FocusedLineage {
    const edges = lineage.edges.filter((edge) => edge.fromTable !== edge.toTable && (edge.fromTable === tableId || edge.toTable === tableId));
    const linked = new Set(edges.flatMap((edge) => [`${edge.fromTable}|${edge.fromColumn}`, `${edge.toTable}|${edge.toColumn}`]));
    const hidden: Record<string, number> = {};
    const tables = lineage.tables
        .filter((table) => table.id === tableId || edges.some((edge) => edge.fromTable === table.id || edge.toTable === table.id))
        .map((table) => {
            if (table.id === tableId) return table;
            const columns = table.columns.filter((column) => linked.has(`${table.id}|${column.name}`));
            if (columns.length < table.columns.length) hidden[table.id] = table.columns.length - columns.length;
            return {...table, columns};
        });
    return {lineage: {tables, edges, warnings: lineage.warnings}, hidden};
}
