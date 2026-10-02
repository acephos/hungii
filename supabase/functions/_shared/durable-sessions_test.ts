import type { SupabaseClient } from "@supabase/supabase-js";
import { DurableFoodSessions, rpcResult } from "./durable-sessions.ts";
import { mockProvider } from "../../../demo/mcp-server.ts";
import { base64 } from "./secrets.ts";
import { HungiiError, type Json } from "./errors.ts";
import { withFood } from "./food.ts";

function assert(value: unknown, message: string): asserts value {if(!value)throw new Error(message);}
// Postgres ownership behavior is tested separately on the actual Mumbai project.
class MemoryStore {
  rows=new Map<string,Json>();requests=0;
  rpc(name:string,args:Json) {
    let row=this.rows.get(args.owner_id);
    if(name==="claim_swiggy_session") {
      if(!row){row={phase:"initializing",generation:args.expected_generation,encrypted_metadata:null};this.rows.set(args.owner_id,row);}
      if(row.phase!=="blocked")row.lease_owner=args.lease_id;
      return Promise.resolve({data:[{...row}],error:null});
    }
    if(name==="reserve_swiggy_request") {
      const remaining=Math.ceil(((row?.cooldown_until??0)-Date.now())/1000);
      if(remaining>0)return Promise.resolve({data:remaining,error:null});
      this.requests++;return Promise.resolve({data:0,error:null});
    }
    if(name==="finish_swiggy_session") {
      if(!row||row.lease_owner!==args.lease_id)return Promise.resolve({data:false,error:null});
      row.encrypted_metadata=args.metadata_ciphertext??row.encrypted_metadata;
      row.phase=args.failure_code?"blocked":row.encrypted_metadata?"ready":row.phase;
      row.blocked_code=args.failure_code;row.cooldown_until=Date.now()+args.retry_seconds*1000;row.lease_owner=null;
      return Promise.resolve({data:true,error:null});
    }
    throw new Error("Unexpected RPC");
  }
}
for(const jsonResponses of [true,false])Deno.test(`new workers reuse the same logical session with ${jsonResponses?"JSON":"SSE"} responses`,async()=>{
  const server=mockProvider(jsonResponses),store=new MemoryStore(),key=base64(crypto.getRandomValues(new Uint8Array(32)));
  const network:typeof fetch=(input,init)=>server.handler(new Request(input,init));
  const run=async(user:string)=>{
    const worker=new DurableFoodSessions(store as unknown as SupabaseClient,key,1,network);
    return await withFood("http://127.0.0.1/food","hungii-local-demo-not-provider",call=>call("get_addresses",{page:1,pageSize:10}),user,true,worker);
  };
  try {
    assert((await run("a")).addresses.length>=1,"Missing address");
    await run("a");assert(server.stats.initializations===1,"Worker initialized again");
    await run("b");assert(Number(server.stats.initializations)===2,"Other user borrowed a session");
    assert(!store.rows.get("a")?.encrypted_metadata.includes("demo-address"),"Provider address retained in metadata");
    assert(store.rows.get("a")?.encrypted_metadata!==store.rows.get("b")?.encrypted_metadata,"Account ciphertext reused");
  } finally {await server.close();}
});
Deno.test("429 cooldown survives worker changes and rejected credentials stop calls",async()=>{
  const server=mockProvider(),store=new MemoryStore(),key=base64(crypto.getRandomValues(new Uint8Array(32)));let reject=0,traffic=0;
  const network:typeof fetch=async(input,init)=>{traffic++;const body=JSON.parse(String(init?.body));if(body.method==="tools/call"&&reject)return new Response(null,{status:reject,headers:{"Retry-After":"31"}});return await server.handler(new Request(input,init));};
  const run=()=>withFood("http://127.0.0.1/food","hungii-local-demo-not-provider",call=>call("get_addresses",{page:1,pageSize:10}),"a",true,new DurableFoodSessions(store as unknown as SupabaseClient,key,1,network));
  try {
    await run();reject=429;
    try{await run();throw new Error("429 accepted");}catch(e){assert(e instanceof HungiiError&&e.status===429,"Wrong rate error");}
    const before=traffic;
    try{await run();throw new Error("Cooldown ignored");}catch(e){assert(e instanceof HungiiError&&e.status===429,"Wrong cooldown error");}
    assert(traffic===before,"Network used during cooldown");
    store.rows.get("a")!.cooldown_until=0;reject=401;
    try{await run();throw new Error("401 accepted");}catch(e){assert(e instanceof HungiiError&&e.code==="HUNGII_RECONNECT","Wrong auth error");}
    const rejectedTraffic=traffic;
    try{await run();throw new Error("Rejected auth retried");}catch(e){assert(e instanceof HungiiError,"Wrong blocked error");}
    assert(traffic===rejectedTraffic,"Rejected credentials reused");
  } finally {await server.close();}
});
Deno.test("SSE parser handles chunked CRLF, multiline data, unrelated events and cancellation",async()=>{
  let cancelled=false;const raw=': heartbeat\r\n\r\nevent: message\r\ndata: {"jsonrpc":"2.0","method":"notifications/message"}\r\n\r\ndata: {"jsonrpc":"2.0",\r\ndata: "id":"wanted","result":{"safe":true}}\r\n\r\n';
  const stream=new ReadableStream({start(c){for(const char of raw)c.enqueue(new TextEncoder().encode(char));},cancel(){cancelled=true;}});
  assert((await rpcResult(new Response(stream,{headers:{"Content-Type":"text/event-stream"}}),"wanted")).safe===true,"SSE mismatch");
  assert(cancelled,"Response stream left open");
});
