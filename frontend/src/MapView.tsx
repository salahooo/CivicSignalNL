import { useEffect } from 'react'
import { CircleMarker, MapContainer, Popup, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import type { ReportMapResponse } from './types'

import { sourceLabel, statusLabel } from './displayLabels'

type Props = { data: ReportMapResponse | null; selected?: string; focus: [number, number] | null; onViewport: (bbox: string, zoom: number) => void; onSelect: (id: string) => void }

export default function MapView({ data, selected, focus, onViewport, onSelect }: Props) {
  const tiles = import.meta.env.VITE_OSM_TILE_URL || 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
  return <MapContainer center={[52.2, 5.25]} zoom={7} minZoom={3} maxZoom={19} scrollWheelZoom className="leaflet-map" aria-label="Kaart met openbare meldingslocaties">
    <TileLayer url={tiles} attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>-bijdragers'/>
    <Viewport onViewport={onViewport}/><Focus position={focus}/>
    {data?.clusters.map(cluster => <Cluster key={cluster.key} cluster={cluster}/>)}
    {data?.points.map(point => <CircleMarker key={point.reportId} center={[point.location.lat, point.location.lon]} radius={selected === point.reportId ? 10 : 7} pathOptions={{ color: selected === point.reportId ? '#d99b2b' : '#17614e', fillColor: '#fff', fillOpacity: 1 }} eventHandlers={{ click: () => onSelect(point.reportId) }}><Popup><strong>{point.reportId}</strong><br/>{point.category || 'Onbekend'}<br/>{point.municipality || 'Onbekend'} · {point.district || 'Onbekend'}<br/>{statusLabel(point.reportStatus)}<br/>{point.occurredAt ? new Date(point.occurredAt).toLocaleString('nl-NL') : 'Niet beschikbaar'}<br/>{sourceLabel(point.sourceType)}</Popup></CircleMarker>)}
  </MapContainer>
}

function Cluster({ cluster }: { cluster: ReportMapResponse['clusters'][number] }) { const map = useMap(); const center: [number, number] = [cluster.location.lat, cluster.location.lon]; return <CircleMarker center={center} radius={Math.min(30, 10 + Math.log2(cluster.count + 1) * 3)} pathOptions={{ color: '#155d4d', fillColor: '#d99b2b', fillOpacity: .88 }} eventHandlers={{ click: () => map.setView(center, Math.min(map.getZoom() + 3, 14)) }}><Tooltip permanent direction="center" className="cluster-label">{cluster.count}</Tooltip><Popup>{cluster.count} meldingen in dit gebied. Zoom in voor punten.</Popup></CircleMarker> }
function Viewport({ onViewport }: { onViewport: (bbox: string, zoom: number) => void }) { useMapEvents({ moveend(event) { const map = event.target; const bounds = map.getBounds(); onViewport([bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()].map(value => value.toFixed(5)).join(','), map.getZoom()) } }); return null }
function Focus({ position }: { position: [number, number] | null }) { const map = useMap(); useEffect(() => { if (position) map.setView(position, 14) }, [map, position]); return null }
