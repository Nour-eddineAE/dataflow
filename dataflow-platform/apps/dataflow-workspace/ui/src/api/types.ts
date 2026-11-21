export type Problem = { message: string; line?: number };

export type RunSummary = {
    runId: string;
    dataset?: string;
    job: string;
    status: string;
    trigger: string;
    partition?: string;
    createdAt?: string;
    startedAt?: string;
    endedAt?: string;
    durationMs?: number;
    task?: string;
    startedBy?: string;
};

export type DatasetSummary = {
    dataset: string;
    valid: boolean;
    version?: number;
    owner?: string;
    description?: string;
    schedule?: string;
    partitions?: string;
    source?: string;
    format?: string;
    counts: Record<string, number>;
    keys: string[];
    lastRun?: RunSummary;
    problems: Problem[];
};

export type LakeTable = {
    id: string;
    layer: string;
    label: string;
    key: string;
    sqlName?: string;
    description: string;
    delta: boolean;
    exists: boolean;
    commits: number;
    updatedAt?: string | null;
};

export type ServedTable = {
    dataset: string;
    table: string;
    sqlName: string;
    from: string;
    mode: string;
    orderBy?: string;
    exists: boolean;
};

export type CatalogTree = {
    datasets: { dataset: string; valid: boolean; tables: LakeTable[] }[];
    serving: ServedTable[];
};

export type ColumnInfo = { name: string; type?: string; role: string; expression?: string; description?: string };

export type TableDetail = {
    dataset: string;
    table: LakeTable;
    columns: ColumnInfo[];
    rows?: number;
    files?: number;
    bytes?: number;
    lastCommit?: Commit;
};

export type Commit = {
    version: number;
    timestamp: string;
    operation: string;
    parameters?: string;
    metrics?: string;
    engine?: string;
};

export type QueryResult = {
    columns: { name: string; type: string }[];
    rows: unknown[][];
    truncated: boolean;
    elapsedMs: number;
    rowsRead: number;
    bytesRead: number;
};

export type GraphNode = {
    id: string;
    label: string;
    layer: string;
    flow?: string;
    status?: string;
    rows?: number;
    durationMs?: number;
    note?: string;
};

export type PipelineGraph = { nodes: GraphNode[]; edges: { from: string; to: string }[] };

export type FlowRun = {
    flow: string;
    status: string;
    startedAt?: string;
    endedAt?: string;
    durationMs?: number;
    rowsIn?: number;
    rowsOut?: number;
    rowsDropped?: number;
    details: Record<string, unknown>;
};

export type ExpectationResult = { name: string; check: string; action: string; evaluated?: number; failing?: number };

export type LogLine = { timestamp: string; level: string; type: string; message: string };

export type OutputLine = {
    timestamp?: string | null;
    level?: string | null;
    logger?: string | null;
    flow?: string | null;
    message: string
};

export type RunOutput = { lines: OutputLine[]; truncated: boolean };

export type RunRuntime = { dataflow: string; spark: string; delta: string; scala: string; java: string };

export type RunDetail = {
    run: RunSummary;
    flows: FlowRun[];
    expectations: ExpectationResult[];
    log: LogLine[];
    error?: string;
    runtime?: RunRuntime | null;
    sparkApp?: string | null;
};

export type Lineage = {
    tables: {
        id: string;
        layer: string;
        label: string;
        columns: { name: string; type?: string; role: string; expression?: string }[]
    }[];
    edges: { fromTable: string; fromColumn: string; toTable: string; toColumn: string }[];
    warnings: string[];
};

export type Change = {
    severity: 'INFO' | 'SAFE' | 'DATA' | 'RISKY';
    area: string;
    title: string;
    detail: string;
    tables: string[]
};

export type DraftCheck = {
    valid: boolean;
    problems: Problem[];
    warnings: string[];
    impact: Change[];
    dataset?: string;
    graph?: PipelineGraph | null
};

export type StoredSpec = { dataset: string; content: string; problems: Problem[]; valid: boolean };

export type SpecCommit = { id: string; message: string; author: string; time: string };

export type FileState = 'UNCHANGED' | 'MODIFIED' | 'ADDED' | 'DELETED';

export type FolderSummary = {
    id: string;
    path: string;
    remoteUrl: string;
    branch: string;
    owner: string;
    project?: string;
    drafts: number
};

export type FolderFile = { path: string; state: FileState };

export type FolderTree = {
    id: string;
    path: string;
    remoteUrl: string;
    branch: string;
    head?: string;
    project?: string;
    targets: string[];
    ahead?: number;
    behind?: number;
    files: FolderFile[];
};

export type FileContent = { path: string; content?: string; committed?: string; state: FileState; problems: Problem[] };

export type GitCredential = {
    id: string;
    owner: string;
    name: string;
    username: string;
    provider?: string | null;
    createdAt: string
};

export type AccessGrant = {
    securable: string;
    principal: string;
    privilege: string;
    grantedBy: string;
    grantedAt: string
};

export type AccessAudit = {
    at: string;
    actor: string;
    action: string;
    securable: string;
    principal: string;
    privilege: string
};

export type Alert = {
    name: string;
    dataset?: string | null;
    severity?: string | null;
    state: string;
    since?: string | null;
    summary?: string | null
};

export type AlertsView = { available: boolean; reason?: string | null; alerts: Alert[] };

export type RescuedKey = {
    key: string;
    rows: number;
    sample?: string | null;
    known: boolean;
    suggestion?: string | null
};

export type TableChanges = {
    from: number;
    to: number;
    counts: { version: number; change: string; rows: number }[];
    rows: QueryResult
};

export type SavedQuery = { id: string; owner: string; name: string; sql: string; shared: boolean; updatedAt: string };

export type QueryAlertComparison = 'ANY_ROWS' | 'NO_ROWS' | 'ABOVE' | 'BELOW' | 'EQUAL';

export type QueryAlert = {
    queryId: string;
    name: string;
    owner: string;
    everyMinutes: number;
    comparison: QueryAlertComparison;
    threshold?: number | null;
    state: string;
    checkedAt?: string | null;
    since?: string | null;
    value?: string | null;
    error?: string | null
};

export type GitHubSettings = { ready: boolean; host: string; installUrl?: string | null };

export type ReleaseView = {
    project: string;
    target: string;
    commit: string;
    releasedAt: string;
    releasedBy: string;
    datasets: string[]
};

export type ReleaseRecord = {
    project: string;
    target: string;
    commit: string;
    folderId?: string;
    releasedBy: string;
    releasedAt: string
};

export type Why = {
    dataset: string;
    factUniqueId: string;
    column?: string;
    expression?: string;
    current: Record<string, unknown>;
    versions: QueryResult;
    arrivals?: QueryResult;
    quarantined?: QueryResult;
    raw?: QueryResult;
    rawNote?: string;
    upstream: Lineage['edges'];
    downstream: Lineage['edges'];
};

export type Partitions = { partitioned: boolean; kind?: string; partitions: { key: string; status: string }[] };

export type RuleRun = { rule: string; runId: string; at: string; failing: number; evaluated: number };

export type QualityRule = {
    dataset: string;
    name: string;
    check: string;
    action: string;
    history: RuleRun[];
    latest?: RuleRun;
};

export type SettingKind = 'TEXT' | 'SECRET' | 'FLAG' | 'NUMBER' | 'DURATION' | 'LIST' | 'ENTRIES';

export type SettingValue = string | string[] | Record<string, string>[] | null;

export type EffectiveSetting = {
    name: string;
    description: string;
    defaultValue?: string | null;
    origin?: string | null;
} & (
    | { kind: 'LIST'; value: string[] }
    | { kind: 'ENTRIES'; value: Record<string, string>[] }
    | { kind: Exclude<SettingKind, 'LIST' | 'ENTRIES'>; value: string | null });

export type SettingsSection = { name: string; description: string; settings: EffectiveSetting[] };

export type EffectiveSettings = { workspace: SettingsSection[]; serving: SettingsSection[]; engine: SettingsSection[]; tower: SettingsSection[] };

export type Compute = {
    executors?: { min?: number | null; max?: number | null } | null;
    driverMemory?: string | null;
    executorMemory?: string | null;
    driverCores?: number | null;
    executorCores?: number | null;
    sparkConf?: Record<string, string>;
};

export type TargetCompute = {
    name: string;
    production: boolean;
    branch?: string | null;
    compute: Compute;
    releasedCommit?: string | null;
    releasedAt?: string | null;
    releasedBy?: string | null;
    pipelines: { dataset: string; compute: Compute }[];
};

export type ComputePolicy = {
    maxDriverMemory?: string | null;
    maxDriverCores?: number | null;
    maxExecutorMemory?: string | null;
    maxExecutorCores?: number | null;
    maxExecutors?: number | null;
};

export type ProjectCompute = {
    folderId: string;
    folder: string;
    project: string;
    head?: SpecCommit | null;
    rights: { read: boolean; edit: boolean; release: boolean };
    targets: TargetCompute[];
    problem?: string | null;
};

export type TableUsage = { table: string; queries: number; last?: string | null };
export type ColumnUsage = { name: string; queries: number };

export type CleanupKind = 'PREVIEWS' | 'EVENT_LOGS' | 'CHECKPOINTS' | 'UPLOADS' | 'RELEASES' | 'CATALOGS';

export type CleanupItem = { kind: CleanupKind; location: string; files: number; bytes: number };

export type StorageView = {
    status: { enabled: boolean; appliedAt?: string; problem?: string };
    signInUrl?: string;
    datasets: { dataset: string; paths: { prefix: string; url: string }[] }[];
};

export type ServingHealth = {
    activity: { queries: number; failed: number; averageMs?: number | null; slowestMs: number };
    memoryBytes: number;
    failedRefreshes: { database: string; view: string; error: string }[];
};

export type PlatformService = {
    id: string;
    name: string;
    runsOn: string;
    health: 'HEALTHY' | 'FAILED' | 'UNKNOWN';
    detail: string;
    version?: string | null;
    responseMs?: number | null;
    link?: string | null;
    actions: string[];
    manage: boolean
};

export type Dashboard = { uid: string; title: string; group: string; description?: string; datasetVariable?: string };

export type Status = {
    tower: boolean;
    serving: boolean;
    git: boolean;
    version: string;
    signIn: boolean;
    links: { tower?: string; objectStore?: string; serving?: string; dashboards?: string; mail?: string; sparkHistory?: string };
};

export type FlowCell = {
    runId: string;
    flow: string;
    status: string;
    startedAt: string;
    durationMs?: number | null;
    rowsOut?: number | null
};

export type EventRun = {
    runId: string;
    status: string;
    trigger: string;
    partition?: string;
    startedAt: string;
    endedAt?: string;
    durationMs?: number
};


export type SchemaColumn = { name: string; type: string };

export type SqlFunction = { name: string; aggregate: boolean };

export type FunctionDoc = {
    name: string;
    syntax?: string | null;
    description?: string | null;
    returns?: string | null
};

export type Schema = Record<string, SchemaColumn[]>;

export type Tabular = { columns: { name: string; type: string }[]; rows: (string | null)[][] };

export type PreviewResult = {
    dataset: string;
    origin: SampleOrigin | null;
    rows: number;
    rawColumns: { name: string; type: string }[];
    silver: Tabular | null;
    rules: { name: string; action: string; failing: number; evaluated: number }[];
    quarantine: Tabular | null;
    gold: { name: string; rows?: number; sample?: Tabular; error?: string }[];
};

export type SampleOrigin = 'BRONZE' | 'SOURCE';

export type ImpactResult = {
    dataset: string;
    origin: SampleOrigin | null;
    rows: number;
    rules: {
        name: string;
        actionBefore?: string;
        actionAfter?: string;
        failingBefore?: number;
        failingAfter?: number
    }[];
    columns: { name: string; state: 'ADDED' | 'REMOVED' | 'KEPT'; changedRows: number }[];
    keys: { changed: boolean; factsBefore: number; factsAfter: number; merged: number; split: number };
    changedFacts: number;
    gold: { name: string; rowsBefore?: number; rowsAfter?: number; error?: string }[];
};

export type TaskKind = 'preview' | 'impact' | 'infer';

export type SourceGuess = {
    source: string;
    name: string;
    type: string;
    from: string;
    required: boolean;
    nulls: number;
    distinct: number;
    min?: string | null;
    max?: string | null
};

export type InferResult = {
    dataset: string;
    format: string;
    options: Record<string, string>;
    sampledRows: number;
    columns: SourceGuess[];
    keys: string[];
    sequenceBy?: string | null;
    schedule?: string | null;
    spec: string;
    reasons: { choice: string; why: string }[];
    problems: string[];
};

export type TaskResult = {
    task: string;
    kind: TaskKind;
    dataset: string;
    status: 'done' | 'failed';
    error?: string;
    result?: PreviewResult | ImpactResult | InferResult;
    finishedAt: string;
};

export type TaskState = { taskId: string; runId: string; status: string; result?: TaskResult };

export type ColumnProfile = {
    name: string;
    type: string;
    nulls: number;
    distinct?: number | null;
    min?: string | null;
    max?: string | null
};

export type TableProfile = { rows: number; columns: ColumnProfile[]; elapsedMs: number; bytesRead: number };

export type ScheduleState = { cron: string; timezone: string; running: boolean; nextRun?: string | null };

export type Trigger = {
    name: string;
    kind: 'SCHEDULE' | 'INPUTS' | 'PLATFORM';
    dataset?: string | null;
    cron?: string | null;
    timezone?: string | null;
    running: boolean;
    lastStatus?: string | null;
    lastAt?: string | null;
    lastMessage?: string | null;
    nextAt?: string | null
};

export type DeltaDetails = {
    properties: Record<string, string>;
    partitionColumns: string[];
    clusteringColumns: string[];
    minReaderVersion?: number | null;
    minWriterVersion?: number | null;
    features: string[];
    createdAt?: string | null;
};

export type RuleCount = { rule: string; rows: number };

export type RunComparison = { previousRunId?: string | null; previousFlows: FlowRun[] };

export type FlowEvent = {
    flow: string;
    status: string;
    startedAt?: string;
    endedAt?: string;
    durationMs?: number;
    rowsIn?: number;
    rowsOut?: number;
    rowsDropped?: number
};

export type BoardChartKind = 'TIME' | 'BARS' | 'HISTOGRAM';

export type BoardChart = {
    id: string;
    title: string;
    kind: BoardChartKind;
    x: string;
    measure?: string | null;
    split?: string | null;
    low?: string | null;
    high?: string | null
};

export type TableBoard = {
    table: string;
    label: string;
    sqlName: string;
    rows: number;
    time?: string | null;
    latest?: string | null;
    filters: string[];
    charts: BoardChart[]
};

export type Board = { dataset: string; tables: TableBoard[]; problems: string[] };

export type TableStorage = {
    table: string;
    layer: string;
    commits: number;
    files: number;
    bytes: number;
    smallFiles: number;
    waitingBytes: number;
    reclaimedBytes: number;
    updatedAt?: string | null;
    sizeChanges: { day: string; bytes: number }[];
};

export type BoardData = {
    chart: string;
    sql: string;
    grain?: string | null;
    rows: unknown[][];
    truncated: boolean;
    elapsedMs: number;
    cached: boolean
};

export type Freshness = {
    state: 'fresh' | 'late' | 'never' | 'manual' | 'paused';
    lastSuccess?: string | null;
    expectedBy?: string | null
};

export type BoardOverview = {
    dataset: string;
    schedule?: string | null;
    freshness: Freshness;
    runs: EventRun[];
    flows: FlowEvent[];
    rules: { rule: string; runId: string; at: string; failing: number; evaluated: number }[];
    quarantine: { rule: string; rows: number }[];
    storage: TableStorage[];
};

export type Finding = { subject: string; verdict: 'PASSED' | 'WARNING' | 'FAILED'; detail: string };

export type TransformationInfo = {
    id: string;
    name: string;
    version: number;
    inputs: string[];
    params: { key: string; type: string }[];
};

export type QueryRun = {
    time: string;
    person: string;
    query: string;
    durationMs: number;
    rowsRead: number;
    bytesRead: number;
    resultRows: number;
    status: 'QueryFinish' | 'ExceptionBeforeStart' | 'ExceptionWhileProcessing';
    error: string;
};

export type FavoriteKind = 'PIPELINE' | 'TABLE' | 'SERVED' | 'QUERY';

export type Favorite = { kind: FavoriteKind; ref: string; createdAt: string };
