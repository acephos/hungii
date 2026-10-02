import {Server} from "npm:@modelcontextprotocol/sdk@1.31.0/server/index.js";
import {WebStandardStreamableHTTPServerTransport} from "npm:@modelcontextprotocol/sdk@1.31.0/server/webStandardStreamableHttp.js";
import {ListToolsRequestSchema,CallToolRequestSchema} from "npm:@modelcontextprotocol/sdk@1.31.0/types.js";
import {Ajv} from "ajv";
import {fixture,schemas} from "./catalogue.ts";

export function mockProvider(jsonResponses=true) {
  const sessions=new Map<string,WebStandardStreamableHTTPServerTransport>();
  const servers:Server[]=[];
  const stats={initializations:0,calls:[] as string[]};
  const ajv=new Ajv({strict:false});
  const handler=async(request:Request):Promise<Response>=>{
    if(new URL(request.url).pathname!=="/food")return new Response("not found",{status:404});
    if(request.headers.get("authorization")!=="Bearer hungii-local-demo-not-provider")return new Response("unauthorized",{status:401});
    const id=request.headers.get("mcp-session-id");
    let transport=id?sessions.get(id):undefined;
    if(id&&!transport)return new Response("expired session",{status:404});
    if(!transport) {
      if(request.method!=="POST")return new Response("method",{status:405});
      const body=await request.clone().json();if(body.method!=="initialize")return new Response("initialize first",{status:400});
      if(sessions.size>=20)return new Response("capacity",{status:429,headers:{"Retry-After":"30"}});
      stats.initializations++;
      transport=new WebStandardStreamableHTTPServerTransport({sessionIdGenerator:()=>crypto.randomUUID(),enableJsonResponse:jsonResponses,onsessioninitialized:(id:string)=>{sessions.set(id,transport!);},onsessionclosed:(id:string)=>{sessions.delete(id);}});
      const server=new Server({name:"hungii-local-mock",version:"1.0.0"},{capabilities:{tools:{}}});servers.push(server);
      server.setRequestHandler(ListToolsRequestSchema,()=>({tools:schemas}));
      server.setRequestHandler(CallToolRequestSchema,(request:any)=>{
        const tool=schemas.find(t=>t.name===request.params.name);
        if(!tool||!ajv.compile(tool.inputSchema)(request.params.arguments??{}))throw new Error("Invalid read request");
        stats.calls.push(tool.name);
        const data=fixture(tool.name,request.params.arguments??{});
        const envelope={success:true,data};return {content:[{type:"text",text:JSON.stringify(envelope)}],structuredContent:envelope};
      });
      await server.connect(transport);
    }
    return transport.handleRequest(request);
  };
  return {handler,stats,close:async()=>{await Promise.all(servers.map(s=>s.close()));sessions.clear();}};
}
if(import.meta.main) {
  const provider=mockProvider();console.info("Hungii synthetic MCP: loopback only, no Swiggy connection.");
  Deno.serve({hostname:"127.0.0.1",port:8890},provider.handler);
}
