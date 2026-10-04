import { HungiiError, type Json } from "./errors.ts";
export const PRIVACY_VERSION = "2026-10-04.1";
export function trackerState(value: unknown): Json {
  const fail = () => { throw new HungiiError("HUNGII_BAD_INPUT", "Only valid user-entered tracker totals can be synced."); };
  if (!value || typeof value !== "object" || Array.isArray(value)) return fail();
  const o=value as Json, keys=["day","calorieGoal","proteinGoal","carbGoal","fatGoal","allowance","spent","opportunities","intake"];
  if(Object.keys(o).some(k=>!keys.includes(k) && k!=="preferences") || keys.some(k=>!(k in o))) return fail();
  if(typeof o.day!=="string" || !/^\d{4}-\d{2}-\d{2}$/.test(o.day) || !Number.isFinite(Date.parse(o.day)) || new Date(o.day).toISOString().slice(0,10)!==o.day) return fail();
  for(const k of keys.slice(1,-1)) if(!Number.isInteger(o[k]) || o[k]<0 || o[k]>(k==="opportunities"?8:100000)) return fail();
  if(!o.intake || typeof o.intake!=="object" || Array.isArray(o.intake) || Object.keys(o.intake).sort().join()!==["calories","carbs","fat","protein"].sort().join()) return fail();
  for(const range of Object.values(o.intake)) {
    if(!Array.isArray(range)||range.length!==2||range.some(n=>!Number.isInteger(n)||n<0||n>100000)||range[0]>range[1]) return fail();
  }
  if(o.preferences!==undefined) preferencesState(o.preferences);
  return structuredClone(o);
}

export function preferencesState(value: unknown): Json {
  const fail=()=>{throw new HungiiError('HUNGII_BAD_INPUT','Only your entered profile and preferences can be synced.');};
  if(!value || typeof value!=='object' || Array.isArray(value))return fail();
  const o=value as Json,keys=['displayName','taste','query','highProtein','vegOnly','budgetOnly','fast'];
  if(Object.keys(o).sort().join()!==keys.sort().join())return fail();
  if(typeof o.displayName!=='string' || o.displayName.length>60 || typeof o.query!=='string' || !o.query.trim() || o.query.length>120)return fail();
  if(!['any','spicy','cheesy','sweet','bland','light','filling'].includes(o.taste))return fail();
  if(['highProtein','vegOnly','budgetOnly','fast'].some(k=>typeof o[k]!=='boolean'))return fail();
  return structuredClone(o);
}
