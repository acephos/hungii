import {withFood,foodSessions,discovery,discover,menuView,cartView,HungiiError,type Json} from "../supabase/functions/_shared/food.ts";
import {trackerState,PRIVACY_VERSION} from "../supabase/functions/_shared/tracker.ts";
import {mockProvider} from "./mcp-server.ts";
const provider=mockProvider();Deno.serve({hostname:"127.0.0.1",port:8890},provider.handler);
let connected=false,addressId:string|null=null,state:Json|null=null;
const reply=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
Deno.serve({hostname:"127.0.0.1",port:8788},async request=>{
  try {
    if(request.method!=="POST"||new URL(request.url).pathname!=="/api")return reply({demo:true},405);
    const b=await request.json();
    if(b.action==="status")return reply({connected,addressId,environment:"local demo · synthetic data",cartWritesEnabled:false,priceUnitVerified:true});
    if(b.action==="connect") {if(b.consent!==true||b.privacyVersion!==PRIVACY_VERSION)throw new HungiiError("HUNGII_CONSENT","Read and allow demo retention first.");connected=true;return reply({connected:true});}
    if(["disconnect","delete_account","delete_cloud_tracker"].includes(b.action)) {if(b.confirm!==true)throw new HungiiError("HUNGII_CONFIRM_REQUIRED","Confirm deletion.");if(b.action==="delete_cloud_tracker"){state=null;return reply({deleted:true});}connected=false;addressId=null;state=null;await foodSessions.drop("local-demo");return reply({deleted:true,disconnected:true,revocationConfirmed:true});}
    if(b.action==="state_save"){state=trackerState(b.state);return reply({saved:true});}
    if(b.action==="state_get")return reply({state});
    if(!connected)throw new HungiiError("HUNGII_RECONNECT","Enable the local demo from Accounts.",401);
    return await withFood("http://127.0.0.1:8890/food","hungii-local-demo-not-provider",async call=>{
      if(b.action==="addresses"||b.action==="select_address"){
        const data=await call("get_addresses",{page:b.page??1,pageSize:10});
        if(b.action==="select_address"){if(!data.addresses.some((a:Json)=>a.id===b.addressId))throw new HungiiError("HUNGII_ADDRESS","Select the synthetic address.");addressId=b.addressId;return reply({addressId});}
        return reply({addresses:data.addresses.map((a:Json)=>({id:a.id,label:a.addressTag,addressLine:a.addressLine})),pagination:data.pagination});
      }
      if(!addressId)throw new HungiiError("HUNGII_ADDRESS_REQUIRED","Select the synthetic address.");
      if(b.action==="discover")return reply(discovery(await discover(call,addressId,b.query,b.collection),"rupees"));
      if(b.action==="menu")return reply(menuView(await call("search_menu",{addressId,query:b.query,restaurantIdOfAddedItem:b.restaurantId,...(b.vegOnly?{vegFilter:1}:{})}),"rupees"));
      if(b.action==="cart")return reply(cartView(await call("get_food_cart",{addressId}),"rupees"));
      if(b.action==="coupons")return reply({sections:(await call("fetch_food_coupons",{addressId,restaurantId:b.restaurantId})).coupon_sections});
      throw new HungiiError("HUNGII_WRITE_DISABLED","The local demo cannot place orders.");
    },"local-demo",true);
  } catch(error) {const e=error instanceof HungiiError?error:new HungiiError("HUNGII_DEMO_FAILED","The local demo could not complete this request.",503);return reply({error:{code:e.code,message:e.message}},e.status);}
});
