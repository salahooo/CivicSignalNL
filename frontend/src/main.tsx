import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AmsterdamSourcePanel } from './AmsterdamSourcePanel'
import { SchedulerPanel } from './SchedulerPanel'
import {AdminAuthProvider} from './AdminAuth'
import {AdminLogin} from './AdminLogin'
import {useAdmin} from './AdminAuth'
import './styles.css'

function Root(){const{authorization}=useAdmin();return <><App admin={!!authorization}/><AdminLogin />{authorization&&<><AmsterdamSourcePanel viewOfficial={() => window.location.assign('/?sourceType=OFFICIAL_OPEN_DATA&page=0&size=20')} /><SchedulerPanel /></>}</>};createRoot(document.getElementById('root')!).render(<StrictMode><AdminAuthProvider><Root/></AdminAuthProvider></StrictMode>)
