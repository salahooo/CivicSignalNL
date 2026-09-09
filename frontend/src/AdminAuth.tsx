import {createContext,useContext,useState,type ReactNode} from 'react';
import {adminMe} from './api';
type State={authorization:string|null;login:(u:string,p:string)=>Promise<void>;logout:()=>void};const C=createContext<State|null>(null);
const basic=(u:string,p:string)=>'Basic '+btoa(String.fromCharCode(...new TextEncoder().encode(`${u}:${p}`)));
export function AdminAuthProvider({children}:{children:ReactNode}){const[authorization,setAuthorization]=useState<string|null>(null);const login=async(u:string,p:string)=>{const h=basic(u,p);await adminMe(h);setAuthorization(h)};return <C.Provider value={{authorization,login,logout:()=>setAuthorization(null)}}>{children}</C.Provider>};export const useAdmin=()=>{const c=useContext(C);if(!c)throw new Error('AdminAuthProvider ontbreekt');return c};
