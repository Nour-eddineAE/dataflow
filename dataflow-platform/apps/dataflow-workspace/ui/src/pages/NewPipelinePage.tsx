import {Anchor, Table, Text} from '@mantine/core';
import {IconGitBranch} from '../icons';
import {Link, useNavigate} from 'react-router-dom';
import {useFolders} from '../api/hooks';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {Action} from '../components/actions/Action';
import {PAGE, links} from '../lib/links';
import {ICON_EMPTY} from '../lib/icons';

export function NewPipelinePage() {
    const navigate = useNavigate();
    const {data, error, isLoading} = useFolders();
    const projects = (data ?? []).filter((folder) => folder.project);
    if (isLoading) return <Loading/>;
    if (error) return <Page><ErrorState error={error}/></Page>;
    return (
        <Page>
            <PageHeader title="New pipeline" crumbs={[{label: 'Pipelines', to: PAGE.pipelines}, {label: 'New'}]}/>
            {!projects.length ? (
                <Empty icon={<IconGitBranch {...ICON_EMPTY}/>} title="No project"
                       action={<Action label="Create Git folder" onClick={() => navigate(PAGE.newGitFolder)}/>}/>
            ) : (
                <Panel flush>
                    <Table>
                        <Table.Thead>
                            <Table.Tr>
                                <Table.Th>Folder</Table.Th>
                                <Table.Th>Project</Table.Th>
                                <Table.Th>Branch</Table.Th>
                            </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {projects.map((folder) => (
                                <Table.Tr key={folder.id} className="df-row-link"
                                          onClick={() => navigate(links.newPipelineIn(folder.path))}>
                                    <Table.Td>
                                        <Anchor component={Link}
                                                to={links.newPipelineIn(folder.path)}
                                                size="sm" className="df-link">{folder.path}</Anchor>
                                    </Table.Td>
                                    <Table.Td><Text size="sm">{folder.project}</Text></Table.Td>
                                    <Table.Td><Text size="sm">{folder.branch}</Text></Table.Td>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                </Panel>
            )}
        </Page>
    );
}
