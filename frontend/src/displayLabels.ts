import { statusLabels, type CaseStatus } from './workflow'
export const sourceLabels = { MANUAL: 'Handmatig ingevoerd', SYNTHETIC: 'Synthetische demonstratiedata', OFFICIAL_OPEN_DATA: 'Gemeente Amsterdam Open Data' }
export const sourceLabel = (value?: string | null) => sourceLabels[value as keyof typeof sourceLabels] || 'Bron onbekend'
export const statusLabel = (value?: string | null) => !value?.trim() ? 'Nieuw' : statusLabels[value as CaseStatus] || value

export function importErrorMessage(error: unknown): string {
  const failure = error as { status?: number; kind?: string; name?: string } | null
  const messages: Record<number, string> = {
    401: 'De beheersessie is ongeldig. Meld je opnieuw aan.',
    403: 'Je hebt onvoldoende bevoegdheid voor deze import.',
    400: 'De importaanvraag is ongeldig. Kies een limiet van 1 tot en met 5.',
    409: 'Er is een import- of cursorconflict, of de preview is verlopen. Controleer de bronstatus en voer opnieuw een preview uit.',
    429: 'Tijdelijk te veel verzoeken. Wacht even voordat je opnieuw probeert.',
    502: 'De externe Amsterdambron is tijdelijk niet beschikbaar.',
    503: 'De Amsterdambron of benodigde infrastructuur is tijdelijk niet beschikbaar.',
  }
  if (failure?.status && messages[failure.status]) return messages[failure.status]
  if (failure?.kind === 'network' || failure?.name === 'AbortError') return 'De verbinding is verbroken of de aanvraag duurde te lang. Controleer de verbinding en probeer opnieuw.'
  return 'Er is een onverwachte fout opgetreden. Probeer later opnieuw.'
}
