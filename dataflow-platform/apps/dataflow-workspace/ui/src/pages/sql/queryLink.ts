import type {LoaderFunctionArgs} from 'react-router-dom';
import {api} from '../../api/client';
import type {SavedQuery} from '../../api/types';
import {notifyFailure} from '../../components/notify';
import {newTabId, type QueryLink} from '../../lib/queryTabs';

export async function loadQueryLink({request}: LoaderFunctionArgs): Promise<QueryLink | null> {
    const params = new URL(request.url).searchParams;
    const savedId = params.get('saved');
    if (savedId) {
        return api.get<SavedQuery>(`/sql/saved/${savedId}`)
            .then((saved) => ({tab: newTabId(), saved}))
            .catch((error: Error) => {
                notifyFailure('Not opened')(error);
                return null;
            });
    }
    const sql = params.get('q');
    return sql !== null || params.has('new') ? {tab: newTabId(), sql: sql ?? ''} : null;
}
