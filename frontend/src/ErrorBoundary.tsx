import { Component, type ErrorInfo, type ReactNode } from 'react'

export class ErrorBoundary extends Component<{ children: ReactNode; title: string }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  componentDidCatch(error: Error, info: ErrorInfo) { console.error(this.props.title, error, info.componentStack) }
  render() {
    if (this.state.failed) return <section className="panel error-panel" role="alert"><h2>{this.props.title}</h2><p>Dit onderdeel kon niet veilig worden weergegeven.</p><button onClick={() => this.setState({ failed: false })}>Opnieuw proberen</button></section>
    return this.props.children
  }
}
