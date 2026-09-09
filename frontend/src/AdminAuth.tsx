import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { adminMe, configureAdminClient } from './api'

type State = { authorization: string | null; login: (username: string, password: string) => Promise<void>; logout: () => void }
const AdminContext = createContext<State | null>(null)

export const createBasicAuthorization = (username: string, password: string) =>
  `Basic ${btoa(String.fromCharCode(...new TextEncoder().encode(`${username}:${password}`)))}`

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [authorization, setAuthorization] = useState<string | null>(null)
  const logout = () => { configureAdminClient(null, () => {}); setAuthorization(null) }
  useEffect(() => configureAdminClient(authorization, logout), [authorization])
  const login = async (username: string, password: string) => {
    const header = createBasicAuthorization(username, password)
    await adminMe(header)
    configureAdminClient(header, logout)
    setAuthorization(header)
  }
  return <AdminContext.Provider value={{ authorization, login, logout }}>{children}</AdminContext.Provider>
}

export function useAdmin() {
  const context = useContext(AdminContext)
  if (!context) throw new Error('AdminAuthProvider ontbreekt')
  return context
}
