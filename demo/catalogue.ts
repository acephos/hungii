// Hungii-authored synthetic fixtures. No Swiggy catalogue or user data.
export const restaurants = [
  { id:"demo-bowls",name:"Demo Bowl Studio",availabilityStatus:"OPEN",deliveryTimeMinutes:22,distanceKm:1.2 },
  { id:"demo-kitchen",name:"Demo Everyday Kitchen",availabilityStatus:"OPEN",deliveryTimeMinutes:18,distanceKm:0.8 },
  { id:"demo-wraps",name:"Demo Wrap Workshop",availabilityStatus:"OPEN",deliveryTimeMinutes:30,distanceKm:2.4 },
];
export const dishes = [
  {id:"tofu",restaurantId:"demo-bowls",name:"Spicy tofu rice bowl",price:185,isVeg:true,description:"Synthetic demo: spicy tofu, rice and vegetables.",inStock:true},
  {id:"chicken",restaurantId:"demo-bowls",name:"Pepper chicken bowl",price:210,isVeg:false,description:"Synthetic demo: pepper chicken and rice.",inStock:true},
  {id:"dal",restaurantId:"demo-kitchen",name:"Everyday dal and rice",price:125,isVeg:true,description:"Synthetic demo: mild dal with rice.",inStock:true},
  {id:"paneer",restaurantId:"demo-wraps",name:"Cheesy paneer wrap",price:175,isVeg:true,description:"Synthetic demo: cheesy paneer in a wrap.",inStock:true},
  {id:"eggs",restaurantId:"demo-kitchen",name:"Egg curry and rice",price:155,isVeg:false,description:"Synthetic demo: filling egg curry with rice.",inStock:true},
  {id:"chickpea",restaurantId:"demo-wraps",name:"Spicy chickpea wrap",price:145,isVeg:true,description:"Synthetic demo: spicy chickpeas and vegetables.",inStock:true},
  {id:"soldout",restaurantId:"demo-bowls",name:"Unavailable demo dish",price:100,isVeg:true,inStock:false},
];
export const schemas = [
  {name:"get_addresses",inputSchema:{type:"object",properties:{page:{type:"number",minimum:1},pageSize:{type:"number",minimum:1,maximum:10}},additionalProperties:false}},
  {name:"search_restaurants",inputSchema:{type:"object",properties:{addressId:{type:"string"},query:{type:"string"},offset:{type:"number"},collection:{enum:["EATRIGHT","BOLT","STORE_99"]}},required:["addressId","query"],additionalProperties:false}},
  {name:"search_menu",inputSchema:{type:"object",properties:{addressId:{type:"string"},query:{type:"string"},restaurantIdOfAddedItem:{type:"string"},vegFilter:{enum:[0,1]},offset:{type:"number"}},required:["addressId","query"],additionalProperties:false}},
  {name:"get_food_cart",inputSchema:{type:"object",properties:{addressId:{type:"string"},restaurantName:{type:"string"}},required:["addressId"],additionalProperties:false}},
  {name:"fetch_food_coupons",inputSchema:{type:"object",properties:{addressId:{type:"string"},restaurantId:{type:"string"}},required:["addressId","restaurantId"],additionalProperties:false}},
];
export function fixture(name:string,a:Record<string,any>) {
  if(name!=="get_addresses"&&a.addressId!=="demo-address") throw new Error("Select the synthetic address first");
  if(name==="get_addresses") return {addresses:[{id:"demo-address",addressTag:"Demo location",addressLine:"Synthetic address · no real delivery",phoneNumber:""}],pagination:{page:1,pageSize:10,total:1,totalPages:1,hasMore:false}};
  if(name==="search_restaurants") return {restaurants,dishes: a.collection==="STORE_99"?[]:dishes,hasMore:false};
  if(name==="search_menu") return {items:dishes.filter(d=>(!a.restaurantIdOfAddedItem||d.restaurantId===a.restaurantIdOfAddedItem)&&(a.vegFilter!==1||d.isVeg)).map(d=>({menu_item_id:d.id,restaurant_id:d.restaurantId,restaurant_name:restaurants.find(r=>r.id===d.restaurantId)?.name,name:d.name,price:d.price,isVeg:d.isVeg,inStock:d.inStock?1:0})),totalItems:6,hasMore:false};
  if(name==="get_food_cart") return {addressId:a.addressId,data:{cart_id:"demo-empty-cart",restaurant:{},items:[],pricing:{item_total:0,delivery_charge:0,taxes_and_charges:0,to_pay:0},offers:{}}};
  if(name==="fetch_food_coupons") return {coupon_sections:[{coupons:[{id:"demo-offer",title:"Synthetic offer example",description:"Demo only: an offer may have basket/payment conditions. No discount has been applied.",applicable:false}]}]};
  throw new Error("Only documented read tools exist in this mock");
}
