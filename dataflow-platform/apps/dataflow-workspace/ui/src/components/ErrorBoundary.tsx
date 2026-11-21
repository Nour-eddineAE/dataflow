import {Container, Group, Text} from '@mantine/core';
import {Component, type ErrorInfo, type ReactNode} from 'react';
import {Notice} from './Notice';
import {Action} from './actions/Action';
import {DIALOG_WIDTH} from './Page';

type Props = { children: ReactNode; resetKey: string };
type State = { error?: Error };

export class ErrorBoundary extends Component<Props, State> {
    state: State = {};

    static getDerivedStateFromError(error: Error): State {
        return {error};
    }

    componentDidUpdate(previous: Props) {
        if (this.state.error && previous.resetKey !== this.props.resetKey) {
            this.setState({error: undefined});
        }
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        console.error(error, info.componentStack);
    }

    render() {
        if (!this.state.error) return this.props.children;
        return (
            <Container size={DIALOG_WIDTH} py="full">
                <Notice title="This page failed to render">
                    <Text size="sm" className="df-mono" fz="xs">{this.state.error.message}</Text>
                    <Group mt="snug"><Action size="sm" label="Try again" onClick={() => this.setState({error: undefined})}/></Group>
                </Notice>
            </Container>
        );
    }
}
