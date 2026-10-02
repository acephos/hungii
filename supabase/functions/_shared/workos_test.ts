import { generateKeyPair, SignJWT } from 'jose';
import { WorkOSAuth } from './workos.ts';
import { HungiiError } from './errors.ts';

function assert(value:unknown,message:string):asserts value {if(!value)throw new Error(message);}
const client='client_01H000000000000000000000001';
const subject='user_01H000000000000000000000001';
const sid='session_01H000000000000000000000001';
// Issuer follows the default application, while client_id binds this app.
const issuer='https://api.workos.com/user_management/client_01H000000000000000000000099';
async function setup() {
  const keys=await generateKeyPair('RS256');
  const token=async(overrides:Record<string,unknown>={})=>await new SignJWT({client_id:client,sid,...overrides}).setProtectedHeader({alg:'RS256'}).setIssuer(issuer).setSubject(subject).setIssuedAt().setExpirationTime('5m').sign(keys.privateKey);
  return {keys,token};
}
const active={id:sid,user_id:subject,status:'active',expires_at:new Date(Date.now()+86400000).toISOString()};
async function rejected(task:Promise<unknown>) {try{await task;throw new Error('Accepted invalid identity');}catch(e){assert(e instanceof HungiiError,'Unsafe error');assert(e.code==='HUNGII_LOGIN_REQUIRED','Wrong denial');}}

Deno.test('WorkOS validates signature, issuer, client, mandatory expiry and impersonation before calling API',async()=>{
  const {keys,token}=await setup();let traffic=0;
  const network:typeof fetch=()=>{traffic++;return Promise.resolve(Response.json({data:[active]}));};
  const auth=new WorkOSAuth({clientId:client,apiKey:'synthetic-key',issuer},network,()=>keys.publicKey);
  assert((await auth.verify(await token())).subject===subject,'Wrong subject');
  const before=traffic;
  await rejected(auth.verify(await token({client_id:'client_01H000000000000000000000002'})));
  await rejected(auth.verify(await token({act:{sub:'impersonator'}})));
  await rejected(auth.verify(await token({sid:'bad'})));
  const wrongIssuer=await new SignJWT({sid,client_id:client}).setProtectedHeader({alg:'RS256'}).setIssuer('https://attacker.invalid').setSubject(subject).setIssuedAt().setExpirationTime('5m').sign(keys.privateKey);
  await rejected(auth.verify(wrongIssuer));
  const noExpiry=await new SignJWT({sid,client_id:client}).setProtectedHeader({alg:'RS256'}).setIssuer(issuer).setSubject(subject).setIssuedAt().sign(keys.privateKey);
  await rejected(auth.verify(noExpiry));
  const expired=await new SignJWT({sid,client_id:client}).setProtectedHeader({alg:'RS256'}).setIssuer(issuer).setSubject(subject).setIssuedAt().setExpirationTime(Math.floor(Date.now()/1000)-1).sign(keys.privateKey);
  await rejected(auth.verify(expired));
  const another=await generateKeyPair('RS256');
  const forged=await new SignJWT({sid,client_id:client}).setProtectedHeader({alg:'RS256'}).setIssuer(issuer).setSubject(subject).setIssuedAt().setExpirationTime('5m').sign(another.privateKey);
  await rejected(auth.verify(forged));assert(traffic===before,'Invalid token used upstream');
});
Deno.test('WorkOS live revocation rejects absent, ended, expired and other-user sessions',async()=>{
  const {keys,token}=await setup();let rows=[active];
  const auth=new WorkOSAuth({clientId:client,apiKey:'synthetic-key',issuer},()=>Promise.resolve(Response.json({data:rows})),()=>keys.publicKey);
  const jwt=await token();rows=[];await rejected(auth.verify(jwt));
  rows=[{...active,status:'revoked'}];await rejected(auth.verify(jwt));
  rows=[{...active,user_id:'user_01H000000000000000000000002'}];await rejected(auth.verify(jwt));
  rows=[{...active,expires_at:'2000-01-01T00:00:00Z'}];await rejected(auth.verify(jwt));
});
Deno.test('WorkOS session pagination, server refresh and scoped revocation/deletion use documented endpoints',async()=>{
  const {keys,token}=await setup();const jwt=await token();const calls:{path:string,body:any}[]=[];
  const network:typeof fetch=async(input,init)=>{
    const url=new URL(String(input));const body=init?.body?JSON.parse(String(init.body)):null;calls.push({path:url.pathname+url.search,body});
    if(url.pathname.endsWith('/authenticate'))return Response.json({access_token:jwt,refresh_token:'rotated',user:{id:subject,email:'excluded@example.invalid'}});
    if(url.pathname.endsWith('/sessions'))return Response.json(url.searchParams.has('after')?{data:[active]}:{data:[],list_metadata:{after:'next-page'}});
    return new Response(null,{status:204});
  };
  const auth=new WorkOSAuth({clientId:client,apiKey:'synthetic-key',issuer},network,()=>keys.publicKey);
  const refreshed=await auth.refresh('synthetic-refresh');assert(refreshed.refresh_token==='rotated'&&!refreshed.user.email,'PII leaked in refresh');
  const identity=await auth.verify(jwt);await auth.signOut(identity);await auth.deleteUser(identity);
  assert(calls.some(c=>c.path==='/user_management/authenticate'&&c.body.client_secret==='synthetic-key'&&c.body.grant_type==='refresh_token'),'Refresh contract changed');
  assert(calls.some(c=>c.path==='/user_management/sessions/revoke'&&c.body.session_id===sid),'Wrong revoke target');
  assert(calls.some(c=>c.path===`/user_management/users/${subject}`),'Wrong delete target');
});
Deno.test('WorkOS outage fails closed without exposing provider bodies',async()=>{
  const {keys,token}=await setup();const auth=new WorkOSAuth({clientId:client,apiKey:'synthetic-key',issuer},()=>Promise.resolve(new Response('sensitive provider body',{status:500})),()=>keys.publicKey);
  try{await auth.verify(await token());throw new Error('Outage accepted');}catch(e){assert(e instanceof HungiiError&&e.status===503&&!e.message.includes('sensitive'),'Unsafe failure');}
});
