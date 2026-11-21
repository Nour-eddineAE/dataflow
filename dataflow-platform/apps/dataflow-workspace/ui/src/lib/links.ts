export const PAGE = {
    home: '/',
    workspace: '/workspace',
    newGitFolder: '/workspace?create=git-folder',
    catalog: '/catalog',
    services: '/services',
    sql: '/sql',
    newQuery: '/sql?new=1',
    queries: '/queries',
    dashboards: '/dashboards',
    alerts: '/alerts',
    pipelines: '/pipelines',
    newPipeline: '/pipelines/new',
    runs: '/runs',
    failedRuns: '/runs?status=failed',
    automation: '/automation',
    quality: '/quality',
    settings: '/admin',
    storage: '/storage',
} as const;

export const links = {
    table: (dataset: string, table: string) => `${PAGE.catalog}/lake/${dataset}/${table}`,
    served: (table: string) => `${PAGE.catalog}/serving/${table}`,
    pipeline: (dataset: string, tab?: string) => `${PAGE.pipelines}/${dataset}${tab ? `/${tab}` : ''}`,
    workspace: (path: string) => `${PAGE.workspace}?at=${encodeURIComponent(path)}`,
    newPipelineIn: (path: string) => `${PAGE.workspace}?at=${encodeURIComponent(path)}&create=pipeline`,
    run: (runId: string) => `${PAGE.runs}/${runId}`,
    query: (sql: string) => `${PAGE.sql}?q=${encodeURIComponent(sql)}`,
    savedQuery: (id: string) => `${PAGE.sql}?saved=${id}`,
    board: (dataset: string) => `${PAGE.dashboards}/${dataset}`,
    grafanaBoard: (uid: string) => `${PAGE.dashboards}/grafana/${uid}`,
};

export function tableOfNode(dataset: string, node: string) {
    if (node.startsWith('serving.')) return links.served(node.slice('serving.'.length));
    return links.table(dataset, node);
}
