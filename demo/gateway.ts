import {
  cartView,
  discover,
  discovery,
  HungiiError,
  type Json,
  menuView,
  type ToolCall,
  withFood,
} from "../supabase/functions/_shared/food.ts";
import {
  type Connection,
  sdkFactory,
} from "../supabase/functions/_shared/sessions.ts";
import { mockProvider } from "./mcp-server.ts";
import {
  dishes,
  MockFailure,
  MockFood,
  promotions,
  restaurants,
  rounded,
} from "./catalogue.ts";
import { readJsonObject } from "../supabase/functions/_shared/http.ts";
const MOCK_URL = "http://127.0.0.1:8890/food";
class LocalSessions {
  constructor(private network: typeof fetch = fetch) {}
  entries = new Map<
    string,
    { connection: Promise<Connection>; tail: Promise<void> }
  >();
  async run<T>(
    user: string,
    token: string,
    url: string,
    task: (c: Connection) => Promise<T>,
  ): Promise<T> {
    let e = this.entries.get(user);
    if (!e) {
      e = {
        connection: sdkFactory(url, token, this.network),
        tail: Promise.resolve(),
      };
      this.entries.set(user, e);
    }
    const previous = e.tail;
    let release!: () => void;
    e.tail = new Promise<void>((r) => release = r);
    await previous;
    try {
      return await task(await e.connection);
    } finally {
      release();
    }
  }
  async drop(user: string) {
    const e = this.entries.get(user);
    if (e) {
      await e.tail;
      await e.connection.then((c) => c.close(), () => {});
      this.entries.delete(user);
    }
  }
  async close() {
    await Promise.all([...this.entries.keys()].map((u) => this.drop(u)));
  }
}
export function createGateway(
  provider = mockProvider(),
  network: typeof fetch = fetch,
) {
  const sessions = new LocalSessions(network);
  const users = new Map<
    string,
    {
      connected: boolean;
      addressId: string | null;
      requests: Map<string, Json>;
    }
  >();
  const stateFor = (id: string) => {
    let s = users.get(id);
    if (!s) {
      if (users.size >= 128) {
        throw new HungiiError(
          "HUNGII_DEMO_BUSY",
          "Restart the local simulator to reset sessions.",
          503,
        );
      }
      s = { connected: true, addressId: null, requests: new Map() };
      users.set(id, s);
    }
    return s;
  };
  const handler = async (request: Request): Promise<Response> => {
    const session = request.headers.get("x-hungii-demo-session") ??
      "not-provider";
    if (!/^(?:not-provider|[A-Za-z0-9_-]{16,96})$/.test(session)) {
      return reply({
        error: {
          code: "HUNGII_DEMO_SESSION",
          message: "Invalid demo session.",
        },
      }, 400);
    }
    const token = "hungii-local-demo-" + session;
    let traceEngine: MockFood | undefined;
    const response = (value: Json, status = 200) =>
      reply({
        ...value,
        demo: true,
        mcpTrace: traceEngine?.events.slice(-24).map((e) => e.tool) ?? [],
      }, status);

    try {
      const url = new URL(request.url);
      if (request.method === "GET" && url.pathname === "/health") {
        return response({ status: "ready", tools: 20 });
      }
      if (request.method !== "POST" || url.pathname !== "/api") {
        return response({ error: { message: "Use /api." } }, 405);
      }
      const b = await readJsonObject(request, 50000);
      if (
        typeof b.action !== "string" || !b.action.trim() ||
        b.action.length > 100
      ) {
        throw new HungiiError(
          "HUNGII_BAD_INPUT",
          "Choose a valid simulator action.",
        );
      }
      const state = stateFor(session), engine = provider.getState(token);
      traceEngine = engine;
      const enriched = (result: Json): Json => ({
        ...result,
        meals: (result.meals ?? []).filter((m: Json) =>
          !dishes.find((d) => d.id === m.dishId)?.side
        ).map((m: Json) => {
          const d = dishes.find((d) => d.id === m.dishId)!;
          const items = [{
            menu_item_id: d.id,
            quantity: 1,
            variants: d.variantsV2
              ? [{ group_id: "portion", variation_id: "regular" }]
              : [],
          }];
          let quote = engine.quote(items);
          for (const offer of promotions) {
            const offered = engine.quote(items, offer.code);
            if (offered.appliedCoupon && offered.payable < quote.payable) {
              quote = offered;
            }
          }
          return {
            ...m,
            description: d.description,
            nutrition: d.nutrition,
            nutritionSource: d.nutritionSource,
            tags: d.tags,
            photo: d.photo,
            estimatedPayable: quote.payable,
            hasVariants: !!d.variantsV2,
            hasAddons: !!d.addons,
          };
        }),
      });
      if (b.action === "status") {
        return response({
          connected: state.connected,
          addressId: state.addressId,
          environment: "Synthetic Swiggy MCP",
          cartWritesEnabled: true,
          priceUnitVerified: true,
        });
      }
      if (b.action === "connect") {
        state.connected = true;
        return response({ connected: true });
      }
      if (b.action === "disconnect" || b.action === "reset_demo") {
        if (b.confirm !== true) {
          throw new HungiiError(
            "HUNGII_CONFIRM_REQUIRED",
            "Confirm before resetting.",
          );
        }
        await sessions.drop(session);
        provider.reset(token);
        state.addressId = null;
        state.requests.clear();
        state.connected = b.action === "reset_demo";
        return response({
          disconnected: true,
          revocationConfirmed: true,
          deleted: true,
        });
      }
      if (b.action === "agent_chat") {
        if (b.consent !== true) {
          throw new HungiiError(
            "HUNGII_AGENT_CONSENT",
            "Allow cloud assistant processing first.",
          );
        }
        const agent = await fetch("http://127.0.0.1:8789/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            session,
            message: String(b.message ?? "").slice(0, 1500),
            context: b.context ?? {},
            history: b.history ?? [],
          }),
          signal: AbortSignal.timeout(65000),
        }).catch(() => null);
        if (!agent) {
          throw new HungiiError(
            "HUNGII_AGENT_SETUP",
            "Cloud assistant is not running yet. Meal discovery and mock checkout still work.",
            503,
          );
        }
        const result = await agent.json();
        return response(result, agent.status);
      }
      if (!state.connected) {
        throw new HungiiError(
          "HUNGII_RECONNECT",
          "Start the synthetic connection from Accounts.",
          401,
        );
      }
      return await withFood(
        MOCK_URL,
        token,
        async (call: ToolCall) => {
          const addressId = state.addressId;
          if (b.action === "addresses" || b.action === "select_address") {
            const data = await call("get_addresses", {
              page: b.page ?? 1,
              pageSize: 10,
            });
            if (b.action === "select_address") {
              if (!data.addresses.some((a: Json) => a.id === b.addressId)) {
                throw new HungiiError(
                  "HUNGII_ADDRESS",
                  "Choose a returned address.",
                );
              }
              state.addressId = b.addressId;
              return response({ addressId: state.addressId });
            }
            return response({
              addresses: data.addresses.map((a: Json) => ({
                id: a.id,
                label: a.addressTag,
                addressLine: a.addressLine,
              })),
              pagination: data.pagination,
            });
          }
          if (!addressId) {
            throw new HungiiError(
              "HUNGII_ADDRESS_REQUIRED",
              "Choose a synthetic delivery address first.",
            );
          }
          const cart = async (): Promise<Json> => {
            const raw = await call("get_food_cart", { addressId });
            const value = cartView(raw, "rupees");
            const q = engine.quote(engine.cartItems, engine.coupon);
            const ids = engine.cartItems.map((i) => i.menu_item_id);
            const side = dishes.filter((d) =>
              d.restaurantId === engine.restaurantId && d.side &&
              !ids.includes(d.id)
            );
            let nudge: Json | null = null;
            for (const d of side) {
              const items = [...engine.cartItems, {
                menu_item_id: d.id,
                quantity: 1,
              }];
              for (const p of promotions) {
                const quote = engine.quote(items, p.code),
                  saving = rounded(q.payable - quote.payable);
                if (
                  saving > 0 && quote.appliedCoupon &&
                  (!nudge || saving > nudge.saving)
                ) {
                  nudge = {
                    dishId: d.id,
                    name: d.name,
                    price: d.price,
                    saving,
                    newPayable: quote.payable,
                    coupon: p.code,
                    nutrition: d.nutrition,
                  };
                }
              }
            }
            const items = raw.data.items.map((i: Json) => {
              const d = dishes.find((d) => d.id === i.menu_item_id)!;
              const fraction = i.variants?.some((v: Json) =>
                  v.variation_id === "large"
                )
                ? 1.3
                : 1;
              const added = (i.addons ?? []).length;
              const nutrition = Object.fromEntries(
                Object.entries(d.nutrition).map((
                  [k, v],
                ) => [
                  k,
                  (v as number[]).map((x) =>
                    Math.round(
                      (x * fraction + (k === "calories"
                            ? 100
                            : k === "protein"
                            ? 10
                            : k === "carbs"
                            ? 4
                            : 5) * added) * i.quantity,
                    )
                  ),
                ]),
              );
              return {
                ...i,
                menuItemId: i.menu_item_id,
                nutrition,
                photo: d.photo,
                customizations: {
                  variants: d.variantsV2 ?? [],
                  addons: d.addons ?? [],
                },
              };
            });
            return {
              ...value,
              items,
              address: engine.address(addressId).addressLine,
              fees: {
                packaging: q.packaging,
                platform: q.platform,
                tax: q.tax,
              },
              nudge,
              revision: engine.revision,
            };
          };
          if (b.action === "discover") {
            const first = enriched(
              discovery(
                await discover(
                  call,
                  addressId,
                  String(b.query ?? "healthy bowls"),
                  b.collection,
                ),
                "rupees",
              ),
            );
            if (first.meals.length >= 3) return response(first);
            const broader = enriched(
              discovery(await discover(call, addressId, "healthy"), "rupees"),
            );
            const meals = [
              ...new Map(
                [...first.meals, ...broader.meals].map((m: Json) => [m.id, m]),
              ).values(),
            ];
            const restaurants = [
              ...new Map(
                [...first.restaurants, ...broader.restaurants].map((
                  r: Json,
                ) => [r.id, r]),
              ).values(),
            ];
            return response({
              ...first,
              meals,
              restaurants,
              expandedQuery: true,
            });
          }
          if (b.action === "menu") {
            return response(
              enriched(
                menuView(
                  await call("search_menu", {
                    addressId,
                    query: b.query,
                    restaurantIdOfAddedItem: b.restaurantId,
                    ...(b.vegOnly ? { vegFilter: 1 } : {}),
                  }),
                  "rupees",
                ),
              ),
            );
          }
          if (b.action === "cart") return response({ cart: await cart() });
          if (b.action === "coupons") {
            return response({
              sections: (await call("fetch_food_coupons", {
                addressId,
                restaurantId: b.restaurantId ?? engine.restaurantId,
              })).coupon_sections,
            });
          }
          const applyBest = async () => {
            await call("fetch_food_coupons", {
              addressId,
              restaurantId: engine.restaurantId!,
            });
            let best = engine.quote(engine.cartItems, engine.coupon);
            for (const p of promotions) {
              const q = engine.quote(engine.cartItems, p.code);
              if (q.appliedCoupon && q.payable < best.payable) best = q;
            }
            if (
              best.appliedCoupon && best.appliedCoupon !== engine.coupon
            ) {
              await call("apply_food_coupon", {
                addressId,
                cartId: engine.cartId,
                couponCode: best.appliedCoupon,
              });
            }
          };
          if (b.action === "cart_create") {
            const d = dishes.find((d) =>
              d.id === b.dishId && d.restaurantId === b.restaurantId
            );
            if (!d) {
              throw new HungiiError(
                "HUNGII_ITEM",
                "Choose a returned menu item.",
              );
            }
            if (
              engine.cartItems.length &&
              engine.restaurantId !== b.restaurantId && b.replace !== true
            ) {
              throw new HungiiError(
                "HUNGII_REPLACE_CART",
                "A different restaurant is in your cart. Confirm replacing it.",
                409,
              );
            }
            const menu = await call("search_menu", {
              addressId,
              query: d.name,
              restaurantIdOfAddedItem: d.restaurantId,
            });
            const item = menu.items.find((i: Json) => i.menu_item_id === d.id);
            if (!item || item.inStock === 0) {
              throw new HungiiError(
                "HUNGII_ITEM",
                "This meal is no longer available.",
              );
            }
            if (
              engine.cartItems.length && engine.restaurantId !== d.restaurantId
            ) await call("flush_food_cart", {});
            const variants = (item.variantsV2 ?? []).map((g: Json) => ({
              group_id: g.groupId,
              variation_id: g.variations.find((v: Json) =>
                v.default === 1
              )?.id ?? g.variations[0].id,
            }));
            const items = engine.restaurantId === d.restaurantId
              ? [...engine.cartItems]
              : [];
            if (!items.some((i) => i.menu_item_id === d.id)) {
              items.push({
                menu_item_id: d.id,
                quantity: 1,
                variants,
              });
            }
            await call("update_food_cart", {
              addressId,
              restaurantId: d.restaurantId,
              cartItems: items,
              cutleryOptIn: false,
            });
            await call("get_food_cart", { addressId });
            await applyBest();
            return response({ cart: await cart() });
          }
          if (
            ["cart_quantity", "cart_variant", "cart_addons", "cart_add_side"]
              .includes(b.action)
          ) {
            if (!engine.restaurantId) {
              throw new HungiiError(
                "HUNGII_EMPTY_CART",
                "Your cart is empty.",
              );
            }
            let items = structuredClone(engine.cartItems);
            const item = items.find((i) => i.menu_item_id === b.dishId);
            if (b.action === "cart_add_side") {
              const d = dishes.find((d) =>
                d.id === b.dishId && d.restaurantId === engine.restaurantId &&
                d.side
              );
              if (!d) {
                throw new HungiiError(
                  "HUNGII_ITEM",
                  "Choose a side from this restaurant.",
                );
              }
              if (!item) items.push({ menu_item_id: d.id, quantity: 1 });
              else item.quantity++;
            } else {
              if (!item) {
                throw new HungiiError(
                  "HUNGII_ITEM",
                  "Cart item not found.",
                );
              }
              if (b.action === "cart_quantity") {
                if (
                  !Number.isInteger(b.quantity) || b.quantity < 0 ||
                  b.quantity > 10
                ) {
                  throw new HungiiError(
                    "HUNGII_QUANTITY",
                    "Use 0–10 portions.",
                  );
                }
                item.quantity = b.quantity;
                items = items.filter((i) => i.quantity > 0);
              }
              if (b.action === "cart_variant") {
                item.variants = [{
                  group_id: b.groupId,
                  variation_id: b.variationId,
                }];
              }
              if (b.action === "cart_addons") item.addons = b.addons;
            }
            await call("update_food_cart", {
              addressId,
              restaurantId: engine.restaurantId,
              cartItems: items,
            });
            await call("get_food_cart", { addressId });
            if (items.length) await applyBest();
            return response({ cart: await cart() });
          }
          if (b.action === "cart_coupon") {
            await call("apply_food_coupon", {
              addressId,
              cartId: engine.cartId,
              couponCode: b.couponCode,
            });
            return response({ cart: await cart() });
          }
          if (b.action === "cart_clear") {
            await call("flush_food_cart", {});
            return response({ cart: await cart() });
          }
          if (b.action === "payment_options") {
            return response({
              paymentOptions: await call("get_payment_options", { addressId }),
              cart: await cart(),
            });
          }
          if (b.action === "checkout") {
            if (b.confirm !== true) {
              throw new HungiiError(
                "HUNGII_CONFIRM_REQUIRED",
                "Confirm the final bill, method and address before placing a mock order.",
              );
            }
            if (!/^.{16,96}$/.test(b.requestId ?? "")) {
              throw new HungiiError(
                "HUNGII_REQUEST_ID",
                "A checkout request ID is required.",
              );
            }
            const prior = state.requests.get(b.requestId);
            if (prior) return response(prior);
            const current = await cart();
            if (
              current.revision !== b.revision || current.payable !== b.payable
            ) {
              throw new HungiiError(
                "HUNGII_CART_CHANGED",
                "Your cart changed. Review the current bill before paying.",
                409,
              );
            }
            const options = await call("get_payment_options", { addressId });
            const method = options.allMethods.find((m: Json) =>
              m.id === b.methodId && m.enabled !== false
            );
            if (!method) {
              throw new HungiiError(
                "HUNGII_METHOD",
                "Choose an available method.",
              );
            }
            const result = await call("place_food_order", {
              addressId,
              paymentMethod: method.groupName === "UPI" ? "UPI" : method.id,
              ...(method.kind === "intent" ? { intentApp: method.id } : {}),
              ...(method.kind === "qr" ? { generateUPIQR: true } : {}),
              noteToRestaurant: String(b.note ?? "").slice(0, 200),
            });
            const value = { payment: result, checkoutCart: current };
            state.requests.set(b.requestId, value);
            return response(value);
          }
          if (
            b.action === "simulate_payment" || b.action === "payment_status"
          ) {
            if (b.action === "simulate_payment") {
              engine.settle(
                b.paasId,
                b.outcome,
              );
            }
            const result = await call("check_payment_status", {
              paasId: b.paasId,
              orderId: b.orderId,
              addressId,
              cartId: b.cartId,
            });
            let confirmation: Json | null = null;
            if (result.isTerminalSuccess && !result.confirmed) {
              const p = engine.payments.get(b.paasId)!;
              confirmation = await call("confirm_order", {
                orderId: p.id,
                addressId: p.addressId,
                cartId: p.cartId,
                lat: p.lat,
                lng: p.lng,
              });
              result.confirmed = confirmation.result === "success";
            }
            return response({ paymentStatus: result, confirmation });
          }
          if (b.action === "order") {
            return response({
              details: await call("get_food_order_details", {
                orderId: b.orderId,
              }),
              delivery: await call("get_food_delivery_status", {
                orderId: b.orderId,
              }),
              tracking: await call("track_food_order", { orderId: b.orderId }),
            });
          }
          if (b.action === "orders") {
            return response(
              await call("get_food_orders", { addressId, activeOnly: false }),
            );
          }
          throw new HungiiError(
            "HUNGII_DEMO_ACTION",
            "Unknown simulator action.",
            409,
          );
        },
        session,
        true,
        sessions,
      );
    } catch (error) {
      const e = error instanceof HungiiError
        ? error
        : error instanceof MockFailure
        ? new HungiiError(error.code, error.message, 409)
        : new HungiiError(
          "HUNGII_DEMO_FAILED",
          "The simulator could not complete this request. Check its server connection.",
          503,
        );
      return response(
        { error: { code: e.code, message: e.message } },
        e.status,
      );
    }
  };
  return { handler, provider, close: () => sessions.close() };
}
const reply = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
if (import.meta.main) {
  const gateway = createGateway();
  Deno.serve({ hostname: "127.0.0.1", port: 8890 }, gateway.provider.handler);
  const bind = Deno.env.get("HUNGII_DEMO_BIND") ?? "127.0.0.1";
  if (
    bind !== "127.0.0.1" &&
    !/^100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.\d+\.\d+$/.test(bind)
  ) throw new Error("Use loopback or a Tailscale 100.64/10 address.");
  Deno.serve({ hostname: bind, port: 8788 }, gateway.handler);
  console.info("Hungii synthetic checkout ready · no live orders or money");
}
