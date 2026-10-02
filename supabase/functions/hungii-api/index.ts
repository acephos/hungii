import { handler } from "./handler.ts";
// Native Android uses no browser origin. Permit our first-party dashboard for
// authenticated provisioning checks; CORS never replaces Auth.getUser.
Deno.serve(async request => {
  const origin=request.headers.get("Origin");
  if(request.method==="OPTIONS") return new Response(null,{status:origin==="https://supabase.com"?204:403,headers:origin==="https://supabase.com"?{"Access-Control-Allow-Origin":origin,"Access-Control-Allow-Headers":"authorization, apikey, content-type","Access-Control-Allow-Methods":"POST","Vary":"Origin"}:{}});
  const response=await handler(request);
  if(origin==="https://supabase.com") {
    response.headers.set("Access-Control-Allow-Origin",origin);response.headers.set("Access-Control-Expose-Headers","x-sb-edge-region");response.headers.set("Vary","Origin");
  }
  return response;
});
