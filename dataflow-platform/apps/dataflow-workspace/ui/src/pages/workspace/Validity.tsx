import {Loader, Text} from '@mantine/core';
import type {DraftCheck} from '../../api/types';
import {IconAlertTriangle, IconCircleCheckFilled, IconCircleXFilled} from '../../icons';
import {COLOR} from '../../lib/colors';
import {plural} from '../../lib/format';
import {ICON_STATUS, ICON_TINY} from '../../lib/icons';

export function Validity({checking, result}: { checking: boolean; result?: DraftCheck }) {
    const [icon, label] = checking || !result
        ? [<Loader key="checking" {...ICON_TINY}/>, 'Checking']
        : !result.valid
            ? [<IconCircleXFilled key="problems" {...ICON_STATUS} color={COLOR.danger}/>, plural(result.problems.length, 'problem')]
            : result.warnings.length
                ? [<IconAlertTriangle key="warnings" {...ICON_STATUS} color={COLOR.warning}/>, plural(result.warnings.length, 'warning')]
                : [<IconCircleCheckFilled key="valid" {...ICON_STATUS} color={COLOR.success}/>, 'Valid'];
    return (
        <span className="df-validity">
            {icon}
            <Text size="xs">{label}</Text>
        </span>
    );
}
