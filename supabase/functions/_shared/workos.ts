import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from 'jose';
import { HungiiError, type Json } from './errors.ts';

type Config = { clientId: string; apiKey: string; issuer: string };
export type WorkOSIdentity = { subject: string; sessionId: string };
const loginRequired = () => new HungiiError('HUNGII_LOGIN_REQUIRED','Please sign in to Hungii again.',401);
const id = (value: unknown, prefix: string): value is string => typeof value==='string' && new RegExp(`^${prefix}_[A-Za-z0-9]{10,64}$`).test(value);

export class WorkOSAuth {
  private keys: JWTVerifyGetKey;
  constructor(private config: Config, private network: typeof fetch=fetch, keys?: JWTVerifyGetKey) {
    if(!id(config.clientId,'client') || !config.apiKey || !(config.issuer==='https://api.workos.com' || /^https:\/\/api\.workos\.com\/user_management\/client_[A-Za-z0-9]{10,64}$/.test(config.issuer))) {
      throw new HungiiError('HUNGII_SETUP_REQUIRED','Hungii account sign-in is being configured.',503);
    }
    this.keys=keys??createRemoteJWKSet(new URL(`https://api.workos.com/sso/jwks/${config.clientId}`),{timeoutDuration:5000});
  }
  private async request(path: string, method='GET', body?: Json): Promise<Json> {
    let response: Response;
    try { response=await this.network(`https://api.workos.com/user_management/${path}`,{method,redirect:'error',signal:AbortSignal.timeout(8000),
      headers:{Authorization:`Bearer ${this.config.apiKey}`,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})}); }
    catch { throw new HungiiError('HUNGII_AUTH_UNAVAILABLE','Account service is unavailable. Try again later.',503); }
    if(response.status===404 || response.status===400 && path==='authenticate') {await response.body?.cancel();throw loginRequired();}
    if(!response.ok) {await response.body?.cancel();throw new HungiiError('HUNGII_AUTH_UNAVAILABLE','Account service could not complete this request. Try later.',503);}
    if(response.status===204) return {};
    const raw=await response.text();
    if(!raw)return {};
    try{return JSON.parse(raw);}catch{throw new HungiiError('HUNGII_AUTH_UNAVAILABLE','Invalid account service response.',503);}
  }
  async verify(token: string): Promise<WorkOSIdentity> {
    if(token.length>16384)throw loginRequired();
    let payload;
    try {payload=(await jwtVerify(token,this.keys,{issuer:this.config.issuer,algorithms:['RS256'],requiredClaims:['sub','sid','exp','iat','client_id']})).payload;}
    catch {throw loginRequired();}
    if(payload.client_id!==this.config.clientId || !id(payload.sub,'user') || !id(payload.sid,'session') || payload.act)throw loginRequired();
    // Check live revocation, not just the JWT's signature. Never persist responses.
    let cursor: string|undefined;
    for(let page=0;page<5;page++) {
      const params=new URLSearchParams({limit:'100',...(cursor?{after:cursor}:{})});
      const sessions=await this.request(`users/${payload.sub}/sessions?${params}`);
      if(!Array.isArray(sessions.data))throw new HungiiError('HUNGII_AUTH_UNAVAILABLE','Invalid account service response.',503);
      const session=sessions.data.find((s:Json)=>s.id===payload.sid);
      if(session) {
        if(session.user_id!==payload.sub || session.status!=='active' || !Number.isFinite(Date.parse(session.expires_at)) || Date.parse(session.expires_at)<=Date.now())throw loginRequired();
        return {subject:payload.sub,sessionId:payload.sid};
      }
      cursor=sessions.list_metadata?.after;if(!cursor)break;
    }
    throw loginRequired();
  }
  async refresh(refreshToken: string): Promise<Json> {
    if(!refreshToken || refreshToken.length>8192)throw loginRequired();
    const result=await this.request('authenticate','POST',{client_id:this.config.clientId,client_secret:this.config.apiKey,grant_type:'refresh_token',refresh_token:refreshToken});
    if(typeof result.access_token!=='string' || typeof result.refresh_token!=='string')throw loginRequired();
    const identity=await this.verify(result.access_token);
    if(result.user?.id!==identity.subject)throw loginRequired();
    return {access_token:result.access_token,refresh_token:result.refresh_token,user:{id:identity.subject}};
  }
  async signOut(identity: WorkOSIdentity): Promise<void> {await this.request('sessions/revoke','POST',{session_id:identity.sessionId});}
  async deleteUser(identity: WorkOSIdentity): Promise<void> {
    try {await this.request(`users/${identity.subject}`,'DELETE');}catch(error){if(error instanceof HungiiError && error.code==='HUNGII_LOGIN_REQUIRED')return;throw error;}
  }
}
let auth: WorkOSAuth|undefined;
export function workosAuth(): WorkOSAuth {
  return auth??=new WorkOSAuth({clientId:Deno.env.get('WORKOS_CLIENT_ID')??'',apiKey:Deno.env.get('WORKOS_API_KEY')??'',issuer:Deno.env.get('WORKOS_JWT_ISSUER')??'https://api.workos.com'});
}
