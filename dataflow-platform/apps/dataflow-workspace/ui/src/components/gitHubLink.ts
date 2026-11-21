import {type LoaderFunctionArgs, redirect} from 'react-router-dom';
import {PAGE} from '../lib/links';
import {notifyDone, notifyFailure} from './notify';

export function announceGitHubLink({request}: LoaderFunctionArgs) {
    const params = new URL(request.url).searchParams;
    const linked = params.get('github');
    const failed = params.get('github-error');
    if (!linked && !failed) return null;
    if (linked) notifyDone({message: `Linked ${linked}`});
    if (failed) notifyFailure('GitHub not linked')(new Error(failed));
    return redirect(`${PAGE.settings}?section=git`);
}
