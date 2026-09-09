import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AdminAuthProvider } from './AdminAuth'
import './styles.css'

createRoot(document.getElementById('root')!).render(<StrictMode><AdminAuthProvider><App/></AdminAuthProvider></StrictMode>)
