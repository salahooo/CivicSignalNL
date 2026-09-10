import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { AnalyticsBucket, AnalyticsSummary } from './types'

import { sourceLabel, statusLabel } from './displayLabels'

const colors = ['#17614e', '#2b6f91', '#d99b2b', '#795c9b', '#4f7b64', '#8c6b47']
const count = (value: number) => new Intl.NumberFormat('nl-NL').format(value)
const date = (value: string) => new Intl.DateTimeFormat('nl-NL', { day: '2-digit', month: 'short' }).format(new Date(value))
const dateLabel = (value: unknown) => date(String(value))
const tooltip = (value: unknown) => [count(Number(value)), 'Meldingen']

function EmptyChart() { return <p className="chart-empty">Voor deze selectie zijn geen gegevens beschikbaar.</p> }
function TableSummary({ data }: { data: AnalyticsBucket[] }) { return <details><summary>Tekstsamenvatting</summary><ul>{data.map(item => <li key={item.value}>{item.value}: {count(item.count)}</li>)}</ul></details> }

export default function ChartsPanel({ summary }: { summary: AnalyticsSummary }) {
  summary = { ...summary, topSources: summary.topSources.map(i => ({ ...i, value: sourceLabel(i.value) })), topStatuses: summary.topStatuses.map(i => ({ ...i, value: statusLabel(i.value) })) }
  const areas = summary.topMunicipalities.length ? summary.topMunicipalities : summary.topDistricts
  return <section aria-labelledby="charts-title"><div className="section-heading"><div><p className="eyebrow">Patronen</p><h2 id="charts-title">Visualisaties</h2></div></div><div className="chart-grid">
    <figure className="chart-card wide" aria-labelledby="timeline-title"><figcaption><h3 id="timeline-title">Meldingen door de tijd</h3><p>Aantal meldingen per {summary.interval === 'DAY' ? 'dag' : summary.interval === 'WEEK' ? 'week' : 'maand'}.</p></figcaption>{summary.timeline.length ? <div className="chart"><ResponsiveContainer><LineChart data={summary.timeline}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="timestamp" tickFormatter={date}/><YAxis allowDecimals={false} domain={[0, 'auto']}/><Tooltip labelFormatter={dateLabel} formatter={tooltip}/><Line dataKey="count" stroke={colors[0]} strokeWidth={3} dot={false} isAnimationActive={false}/></LineChart></ResponsiveContainer></div> : <EmptyChart/>}<TableSummary data={summary.timeline.map(item => ({ value: date(item.timestamp), count: item.count }))}/></figure>
    <BucketBars title="Meldingen per categorie" description="Meest voorkomende categorieën." data={summary.topCategories}/>
    <figure className="chart-card" aria-labelledby="sources-title"><figcaption><h3 id="sources-title">Meldingen per bron</h3><p>Verdeling naar herkomst.</p></figcaption>{summary.topSources.length ? <div className="chart"><ResponsiveContainer><PieChart><Pie data={summary.topSources} dataKey="count" nameKey="value" innerRadius={45} outerRadius={76} isAnimationActive={false}>{summary.topSources.map((entry, index) => <Cell key={entry.value} fill={colors[index % colors.length]}/>)}</Pie><Tooltip formatter={tooltip}/><Legend verticalAlign="bottom"/></PieChart></ResponsiveContainer></div> : <EmptyChart/>}<TableSummary data={summary.topSources}/></figure>
    <BucketBars title="Topgemeenten of stadsdelen" description={summary.topMunicipalities.length ? 'Gemeenten met de meeste meldingen.' : 'Stadsdelen met de meeste meldingen.'} data={areas}/>
    <figure className="chart-card" aria-labelledby="status-title"><figcaption><h3 id="status-title">Statusverdeling</h3><p>Bronstatussen binnen de selectie.</p></figcaption>{summary.topStatuses.length ? <div className="chart"><ResponsiveContainer><BarChart data={summary.topStatuses}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="value"/><YAxis allowDecimals={false} domain={[0, 'auto']}/><Tooltip formatter={tooltip}/><Bar dataKey="count" fill={colors[1]} isAnimationActive={false}/></BarChart></ResponsiveContainer></div> : <EmptyChart/>}<TableSummary data={summary.topStatuses}/></figure>
  </div></section>
}

function BucketBars({ title, description, data }: { title: string; description: string; data: AnalyticsBucket[] }) {
  const id = title.toLowerCase().replaceAll(' ', '-')
  return <figure className="chart-card" aria-labelledby={id}><figcaption><h3 id={id}>{title}</h3><p>{description}</p></figcaption>{data.length ? <div className="chart"><ResponsiveContainer><BarChart layout="vertical" data={data} margin={{ left: 22 }}><CartesianGrid strokeDasharray="3 3"/><XAxis type="number" domain={[0, 'auto']} allowDecimals={false}/><YAxis type="category" dataKey="value" width={92}/><Tooltip formatter={tooltip}/><Bar dataKey="count" fill={colors[0]} isAnimationActive={false}/></BarChart></ResponsiveContainer></div> : <EmptyChart/>}<TableSummary data={data}/></figure>
}
