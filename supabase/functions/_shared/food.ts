import { Ajv } from "ajv";
import { HungiiError, type Json } from "./errors.ts";
import { FoodSessions } from "./sessions.ts";
export { HungiiError, type Json } from "./errors.ts";
export type ToolCall = (name: string, args: Json) => Promise<Json>;
export function payload(result: Json): Json {
  let envelope = result.structuredContent;
  if (!envelope) {
    const block = result.content?.find((v: Json) => v.type === "text");
    try { envelope = JSON.parse(block?.text ?? ""); } catch { /* Never parse prose as a menu. */ }
  }
  if (result.isError || envelope?.success !== true || !envelope.data) {
    if(envelope?.error?.code === "RATE_LIMITED") throw new HungiiError("HUNGII_BUSY", "Swiggy asked us to wait. Please try later.",429,30);
    throw new HungiiError("HUNGII_UPSTREAM_RESPONSE", "Swiggy could not complete this request. Please retry or reconnect.", 502);
  }
  return envelope.data;
}

// Exact names verified against Builders Club. No arbitrary proxy, ordering or OTP endpoints.
const READ_TOOLS = new Set(["get_addresses", "search_restaurants", "search_menu", "get_restaurant_menu", "get_food_cart", "fetch_food_coupons", "get_food_orders", "get_food_order_details", "get_payment_options"]);
export const foodSessions = new FoodSessions();
export async function withFood<T>(url: string, token: string, run: (call: ToolCall) => Promise<T>, user: string, localDemo = false, sessions: Pick<FoodSessions,"run"> = foodSessions): Promise<T> {
  const endpoint = new URL(url);
  const demo = localDemo && endpoint.protocol === "http:" && ["127.0.0.1", "localhost"].includes(endpoint.hostname) && endpoint.pathname === "/food";
  if (!demo && (endpoint.protocol !== "https:" || !["mcp.swiggy.com", "mcp-staging.swiggy.com"].includes(endpoint.hostname) || endpoint.pathname !== "/food")) {
    throw new HungiiError("HUNGII_CONFIG", "The Swiggy Food endpoint is not configured correctly.", 503);
  }
  return await sessions.run(user, token, url, async connection => {
    const tools = connection.tools;
    const ajv = new Ajv({ strict: false, allErrors: true });
    return await run(async (name, args) => {
      if (!READ_TOOLS.has(name) && !demo) throw new HungiiError("HUNGII_WRITE_DISABLED", "Cart writes need an approved, verified item contract before they can be enabled.", 409);
      const tool = tools.find(t => t.name === name);
      if (!tool) throw new HungiiError("HUNGII_TOOL_UNAVAILABLE", "This feature is unavailable for the connected Swiggy account.", 409);
      if (!ajv.compile(tool.inputSchema)(args)) throw new HungiiError("HUNGII_SCHEMA_CHANGED", "Swiggy's current request format differs from the verified integration. Please update Hungii.", 409);
      const result = await connection.call(name, args);
      if (demo && result.structuredContent?.error?.code?.startsWith("HUNGII_MOCK_")) {
        throw new HungiiError(result.structuredContent.error.code, result.structuredContent.error.message, 409);
      }
      return payload(result);
    });
  });
}

export function money(value: unknown, unit: string): number | null {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) return null;
  return unit === "rupees" ? value : unit === "paise" ? value / 100 : null;
}
export function discovery(data: Json, unit: string): Json {
  const restaurants = (data.restaurants ?? []).filter((r: Json) => r.availabilityStatus === "OPEN" && typeof r.id === "string");
  const byId = new Map(restaurants.map((r: Json) => [r.id, r]));
  const unique = new Map<string, Json>();
  for (const dish of data.dishes ?? []) {
    const restaurant = byId.get(dish.restaurantId) as Json | undefined;
    // Missing stock is unknown, not sold out. Missing/open status cannot prove serviceability.
    if (!restaurant || !dish.id || dish.inStock === false || typeof dish.name !== "string") continue;
    const id = `${dish.restaurantId}:${dish.id}`;
    unique.set(id, { id, dishId: dish.id, restaurantId: dish.restaurantId, name: dish.name,
      restaurant: restaurant.name, veg: typeof dish.isVeg === "boolean" ? dish.isVeg : null,
      itemPrice: money(dish.price, unit), etaMinutes: dish.deliveryTimeMinutes ?? restaurant.deliveryTimeMinutes ?? null,
      distanceKm: restaurant.distanceKm ?? null, imageUrl: safeImage(dish.imageUrl),
      description: dish.description ?? "", offerText: restaurant.offer ?? null, nutrition: null,
      stockConfirmed: dish.inStock === true });
  }
  return { meals: [...unique.values()], restaurants: restaurants.map((r: Json) => ({ id: r.id, name: r.name, etaMinutes: r.deliveryTimeMinutes ?? null, distanceKm: r.distanceKm ?? null })),
    nextOffset: data.nextOffset ?? null, hasMore: data.hasMore === true, priceUnitVerified: unit !== "unverified" && ["rupees", "paise"].includes(unit) };
}
export function safeImage(url: unknown): string | null {
  if (typeof url !== "string") return null;
  try { return new URL(url).protocol === "https:" ? url : null; } catch { return null; }
}
export function menuView(data: Json, unit: string): Json {
  return { meals: (data.items ?? []).filter((i: Json) => i.menu_item_id && i.restaurant_id && i.inStock !== 0).map((i: Json) => ({
    id: `${i.restaurant_id}:${i.menu_item_id}`, dishId: i.menu_item_id, restaurantId: i.restaurant_id,
    name: i.name, restaurant: i.restaurant_name ?? "", veg: typeof i.isVeg === "boolean" ? i.isVeg : null,
    itemPrice: money(i.price, unit), etaMinutes: null, distanceKm: null, imageUrl: safeImage(i.imageUrl),
    description: "", offerText: null, nutrition: null, stockConfirmed: i.inStock === 1,
    hasVariants: i.hasVariants === true, hasAddons: i.hasAddons === true,
  })), hasMore: data.hasMore === true, nextOffset: data.nextOffset ?? null };
}
export async function discover(call: ToolCall, addressId: string, query: string, collection?: string, offset?: number): Promise<Json> {
  const args: Json = { addressId, query };
  if (collection && ["EATRIGHT", "BOLT", "STORE_99"].includes(collection)) args.collection = collection;
  if (offset !== undefined) args.offset = offset;
  const result = await call("search_restaurants", args);
  if (collection === "STORE_99" && !(result.dishes?.length || result.restaurants?.length)) {
    delete args.collection;
    return { ...await call("search_restaurants", args), broadenedBudgetCollection: true };
  }
  return result;
}
export function cartView(data: Json, unit: string): Json {
  const cart = data.data ?? {};
  const discount = money(cart.offers?.coupon_discount, unit);
  return { cartId: cart.cart_id ?? null, restaurantId: cart.restaurant?.id ?? null,
    restaurant: cart.restaurant?.name ?? null, delivery: cart.restaurant?.deliverySubtitle ?? null,
    items: (cart.items ?? []).map((i: Json) => ({ menuItemId: i.menu_item_id, name: i.name, quantity: i.quantity })),
    itemTotal: money(cart.pricing?.item_total, unit), deliveryCharge: money(cart.pricing?.delivery_charge, unit),
    taxes: money(cart.pricing?.taxes_and_charges, unit), payable: money(cart.pricing?.to_pay, unit),
    appliedCoupon: cart.offers?.coupon_applied && discount !== null && discount > 0 ? cart.offers.coupon_applied : null,
    couponDiscount: discount !== null && discount > 0 ? discount : 0 };
}
