import {Group} from '@mantine/core';
import type {ReactNode} from 'react';
import {useNavigate} from 'react-router-dom';
import type {FavoriteKind} from '../../api/types';
import {IconTerminal2} from '../../icons';
import {Action} from '../../components/actions/Action';
import {CopyValue} from '../../components/CopyValue';
import {FavoriteButton} from '../../components/FavoriteButton';
import {type Crumb, PageHeader} from '../../components/PageHeader';
import {links} from '../../lib/links';

export function TableHeader({crumbs, icon, name, sqlName, exists, favorite}: {
    crumbs: Crumb[];
    icon: ReactNode;
    name: string;
    sqlName?: string;
    exists: boolean;
    favorite: { kind: FavoriteKind; reference: string };
}) {
    const navigate = useNavigate();
    return (
        <PageHeader
            crumbs={crumbs}
            icon={icon}
            title={<Group gap="tight" wrap="nowrap"><span>{name}</span>{sqlName && <CopyValue value={sqlName} label="Copy name"/>}</Group>}
            actions={
                <>
                    <FavoriteButton kind={favorite.kind} reference={favorite.reference} size={32}/>
                    {sqlName && exists && (
                        <Action tone="primary" icon={IconTerminal2} label="Query"
                                onClick={() => navigate(links.query(`SELECT *\nFROM ${sqlName}\nLIMIT 100`))}/>
                    )}
                </>
            }
        />
    );
}
