import type {QualityRule} from '../api/types';
import {statusTone} from './status';

export type RuleStatus = 'PASSED' | 'WARNING' | 'FAILED' | 'UNKNOWN';

type Checked = Pick<QualityRule, 'action' | 'latest'>;

export function ruleStatus(rule: Checked): RuleStatus {
    if (!rule.latest?.evaluated) return 'UNKNOWN';
    if (!rule.latest.failing) return 'PASSED';
    return rule.action === 'warn' ? 'WARNING' : 'FAILED';
}

const toneOf = (rule: Checked) => statusTone(ruleStatus(rule)).tone;

export const isFailing = (rule: Checked) => ['danger', 'warning'].includes(toneOf(rule));

export const isPassing = (rule: Checked) => toneOf(rule) === 'success';
