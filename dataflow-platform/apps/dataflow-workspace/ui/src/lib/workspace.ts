import type {FileState, FolderSummary, FolderTree} from '../api/types';

export type ItemKind = 'home' | 'shared' | 'users' | 'dir' | 'git' | 'file';

export type Item = {
    path: string;
    name: string;
    kind: ItemKind;
    folder?: FolderSummary;
    file?: string;
    state?: FileState;
    children?: Item[];
    loading?: boolean;
};

const order = (item: Item) => (item.kind === 'file' ? 1 : 0);

function sort(items: Item[]) {
    items.sort((a, b) => order(a) - order(b) || a.name.localeCompare(b.name));
    items.forEach((item) => item.children && sort(item.children));
    return items;
}

function folderFiles(folder: FolderSummary, tree: FolderTree | undefined): Item[] | undefined {
    if (!tree) return undefined;
    const root: Item[] = [];
    const dirs = new Map<string, Item>();
    for (const file of tree.files) {
        const parts = file.path.split('/');
        let level = root;
        let within = '';
        for (const part of parts.slice(0, -1)) {
            within = within ? `${within}/${part}` : part;
            let dir = dirs.get(within);
            if (!dir) {
                dir = {path: `${folder.path}/${within}`, name: part, kind: 'dir', folder, file: within, children: []};
                dirs.set(within, dir);
                level.push(dir);
            }
            level = dir.children!;
        }
        level.push({
            path: `${folder.path}/${file.path}`,
            name: parts[parts.length - 1],
            kind: 'file',
            folder,
            file: file.path,
            state: file.state
        });
    }
    return root;
}

export function workspaceItems(me: string, folders: FolderSummary[], trees: Record<string, FolderTree | undefined>, expanded: Set<string>): Item[] {
    const home: Item = {path: `Users/${me}`, name: 'Home', kind: 'home', children: []};
    const shared: Item = {path: 'Shared', name: 'Shared', kind: 'shared', children: []};
    const users: Item = {path: 'Users', name: 'Users', kind: 'users', children: []};
    const places = new Map<string, Item>([[home.path, home], [shared.path, shared], [users.path, users]]);
    const place = (path: string): Item => {
        const known = places.get(path);
        if (known) return known;
        const cut = path.lastIndexOf('/');
        const parent = place(path.slice(0, cut));
        const dir: Item = {path, name: path.slice(cut + 1), kind: 'dir', children: []};
        parent.children!.push(dir);
        places.set(path, dir);
        return dir;
    };
    for (const folder of folders) {
        const cut = folder.path.lastIndexOf('/');
        const files = folderFiles(folder, trees[folder.id]);
        place(folder.path.slice(0, cut)).children!.push({
            path: folder.path, name: folder.path.slice(cut + 1), kind: 'git', folder,
            children: files ?? [], loading: !files && expanded.has(folder.path),
        });
    }
    return sort([home, shared, ...(users.children!.length ? [users] : [])]);
}

export function findItem(items: Item[], path: string): Item | undefined {
    for (const item of items) {
        if (item.path === path) return item;
        if (item.children && path.startsWith(`${item.path}/`)) {
            const found = findItem(item.children, path);
            if (found) return found;
        }
    }
    return undefined;
}

export function owningFolder(folders: FolderSummary[], path: string): FolderSummary | undefined {
    return folders.find((folder) => path === folder.path || path.startsWith(`${folder.path}/`));
}

export function ancestors(path: string): string[] {
    const parts = path.split('/');
    return parts.slice(0, -1).map((_, index) => parts.slice(0, index + 1).join('/'));
}

export function parentOf(path: string): string {
    const cut = path.lastIndexOf('/');
    return cut < 0 ? '' : path.slice(0, cut);
}

const HOME = 'Home';

export type Crumb = { label: string; path: string };

export function crumbsOf(path: string, me: string): Crumb[] {
    const home = `Users/${me}`;
    const parts = path.split('/').filter(Boolean);
    const crumbs = parts.map((part, index) => ({label: part, path: parts.slice(0, index + 1).join('/')}));
    return path === home || path.startsWith(`${home}/`) ? [{label: HOME, path: home}, ...crumbs.slice(2)] : crumbs;
}

export function listingColumns(children: Item[]) {
    return {
        branch: children.some((child) => child.kind === 'git' && !!child.folder?.branch),
        state: children.some((child) => (child.kind === 'file' && !!child.state && child.state !== 'UNCHANGED')
            || (child.kind === 'git' && !!child.folder?.drafts)),
    };
}

