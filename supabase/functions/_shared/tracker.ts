import { HungiiError, type Json } from "./errors.ts";
export const PRIVACY_VERSION = "2026-10-02.1";
export function trackerState(value: unknown): Json {
  const fail = () => { throw new HungiiError("HUNGII_BAD_INPUT", "Only valid user-entered tracker totals can be synced."); };
  if (!value || typeof value !== "object" || Array.isArray(value)) return fail();
  const o=value as Json, keys=["day","calorieGoal","proteinGoal","carbGoal","fatGoal","allowance","spent","opportunities","intake"];
  if(Object.keys(o).some(k=>!keys.includes(k)) || keys.some(k=>!(k in o))) return fail();
  if(typeof o.day!=="string" || !/^\d{4}-\d{2}-\d{2}$/.test(o.day) || !Number.isFinite(Date.parse(o.day)) || new Date(o.day).toISOString().slice(0,10)!==o.day) return fail();
  for(const k of keys.slice(1,-1)) if(!Number.isInteger(o[k]) || o[k]<0 || o[k]>(k==="opportunities"?8:100000)) return fail();
  if(!o.intake || typeof o.intake!=="object" || Array.isArray(o.intake) || Object.keys(o.intake).sort().join()!==["calories","carbs","fat","protein"].sort().join()) return fail();
  for(const range of Object.values(o.intake)) {
    if(!Array.isArray(range)||range.length!==2||range.some(n=>!Number.isInteger(n)||n<0||n>100000)||range[0]>range[1]) return fail();
  }
  return structuredClone(o);
}
