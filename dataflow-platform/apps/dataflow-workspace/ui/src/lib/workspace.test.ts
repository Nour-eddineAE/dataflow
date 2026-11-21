import {describe, expect, it} from 'vitest';
import type {FolderSummary, FolderTree} from '../api/types';
import {ancestors, crumbsOf, findItem, type Item, listingColumns, owningFolder, workspaceItems} from './workspace';

const folder: FolderSummary = {
    id: 'f1',
    path: 'Shared/examples',
    remoteUrl: 'file:///remotes/x.git',
    branch: 'main',
    owner: 'local',
    project: 'examples',
    drafts: 1
};
const mine: FolderSummary = {...folder, id: 'f2', path: 'Users/ada/team/analytics', project: 'analytics', drafts: 0};
const tree: FolderTree = {
    id: 'f1', path: 'Shared/examples', remoteUrl: '', branch: 'main', targets: ['prod'], files: [
        {path: 'README.md', state: 'UNCHANGED'},
        {path: 'pipelines/taxi_trips/pipeline.yml', state: 'UNCHANGED'},
        {path: 'pipelines/earthquakes/pipeline.yml', state: 'MODIFIED'},
        {path: 'dataflow.yml', state: 'UNCHANGED'},
    ],
};

describe('workspaceItems', () => {
    it('puts each Git folder under its place and its files under it, folders first', () => {
        const items = workspaceItems('ada', [folder, mine], {f1: tree}, new Set());

        expect(items.map((item) => item.name)).toEqual(['Home', 'Shared']);
        expect(findItem(items, 'Users/ada/team')?.kind).toBe('dir');
        expect(findItem(items, 'Users/ada/team/analytics')?.kind).toBe('git');
        const examples = findItem(items, 'Shared/examples')!;
        expect(examples.children!.map((item) => item.name)).toEqual(['pipelines', 'dataflow.yml', 'README.md']);
        expect(findItem(items, 'Shared/examples/pipelines')!.children!.map((item) => item.name)).toEqual(['earthquakes', 'taxi_trips']);
        const spec = findItem(items, 'Shared/examples/pipelines/earthquakes/pipeline.yml')!;
        expect(spec.kind).toBe('file');
        expect(spec.file).toBe('pipelines/earthquakes/pipeline.yml');
        expect(spec.state).toBe('MODIFIED');
    });

    it('marks an opened folder whose files are not loaded yet', () => {
        const items = workspaceItems('ada', [mine], {}, new Set(['Users/ada/team/analytics']));
        expect(findItem(items, 'Users/ada/team/analytics')?.loading).toBe(true);
    });

    it('shows other people under Users', () => {
        const items = workspaceItems('bob', [mine], {}, new Set());
        expect(items.map((item) => item.name)).toEqual(['Home', 'Shared', 'Users']);
    });

    it('finds the folder a path belongs to and the path ancestors', () => {
        expect(owningFolder([folder, mine], 'Shared/examples/pipelines')?.id).toBe('f1');
        expect(owningFolder([folder, mine], 'Shared/other')).toBeUndefined();
        expect(ancestors('Shared/examples/pipelines/a.yml')).toEqual(['Shared', 'Shared/examples', 'Shared/examples/pipelines']);
    });
});

describe('crumbsOf', () => {
    it('names the person\'s own folder Home', () => {
        expect(crumbsOf('Users/maria/sales/pipelines', 'maria')).toEqual([
            {label: 'Home', path: 'Users/maria'},
            {label: 'sales', path: 'Users/maria/sales'},
            {label: 'pipelines', path: 'Users/maria/sales/pipelines'},
        ]);
    });

    it('keeps another person\'s folder under Users', () => {
        expect(crumbsOf('Users/omar/sales', 'maria').map((crumb) => crumb.label)).toEqual(['Users', 'omar', 'sales']);
        expect(crumbsOf('Users/mariana', 'maria').map((crumb) => crumb.label)).toEqual(['Users', 'mariana']);
    });
});

describe('listingColumns', () => {
    const file = (state: Item['state']): Item => ({path: `x/${state}`, name: 'pipeline.yml', kind: 'file', state});

    it('hides Branch and State inside a Git folder with nothing changed', () => {
        expect(listingColumns([file('UNCHANGED'), {path: 'x/pipelines', name: 'pipelines', kind: 'dir'}])).toEqual({branch: false, state: false});
    });

    it('shows Branch for Git folders and State once something changed', () => {
        expect(listingColumns([{path: 'Shared/examples', name: 'examples', kind: 'git', folder}])).toEqual({branch: true, state: true});
        expect(listingColumns([file('MODIFIED')])).toEqual({branch: false, state: true});
    });
});

