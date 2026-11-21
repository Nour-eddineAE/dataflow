import {useNavigate} from 'react-router-dom';
import {Action} from '../components/actions/Action';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {PAGE} from '../lib/links';

export function NotFoundPage() {
    const navigate = useNavigate();
    return (
        <Page>
            <PageHeader title="Page not found" actions={<Action label="Home" onClick={() => navigate(PAGE.home)}/>}/>
        </Page>
    );
}
