import {Menu, Tooltip} from '@mantine/core';
import {IconEdit} from '../icons';
import {useNavigate} from 'react-router-dom';
import {useEditLink} from '../api/hooks';
import {ICON} from '../lib/icons';
import {Action, type ActionSize} from './actions/Action';

function reason(project?: string) {
    return project ? `Connect a Git folder of project ${project} to edit` : 'Not released from any project';
}

export function EditSpecButton({dataset, size = 'md'}: { dataset: string; size?: ActionSize }) {
    const navigate = useNavigate();
    const {to, project} = useEditLink(dataset);
    const button = (
        <Action size={size} icon={IconEdit} label="Edit" disabled={!to} onClick={() => to && navigate(to)}/>
    );
    return to ? button : <Tooltip label={reason(project)}><span>{button}</span></Tooltip>;
}

export function EditSpecMenuItem({dataset}: { dataset: string }) {
    const navigate = useNavigate();
    const {to, project} = useEditLink(dataset);
    return (
        <Menu.Item leftSection={<IconEdit {...ICON}/>} disabled={!to}
                   title={to ? undefined : reason(project)}
                   onClick={() => to && navigate(to)}>Edit spec</Menu.Item>
    );
}
