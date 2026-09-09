import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AmsterdamSourcePanel } from './AmsterdamSourcePanel'
import { SchedulerPanel } from './SchedulerPanel'
import './styles.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App /><AmsterdamSourcePanel viewOfficial={() => { window.location.assign('/?sourceType=OFFICIAL_OPEN_DATA&page=0&size=20') }} /><SchedulerPanel /></StrictMode>)
