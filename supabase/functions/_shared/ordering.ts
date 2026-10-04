import {
  cartView,
  HungiiError,
  type Json,
  money,
  type ToolCall,
} from "./food.ts";
import { digest, seal, unseal } from "./secrets.ts";

export type OrderingConfig = {
  enabled: boolean;
  unit: string;
  itemIdField: string;
  quantityField: string;
};
export function orderingConfig(): OrderingConfig {
  const unit = Deno.env.get("SWIGGY_PRICE_UNIT") ?? "unverified";
  const itemIdField = Deno.env.get("SWIGGY_CART_ITEM_ID_FIELD") ?? "";
  const quantityField = Deno.env.get("SWIGGY_CART_QUANTITY_FIELD") ?? "";
  const fieldsValid =
    [itemIdField, quantityField].every((f) =>
      /^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(f) &&
      !["__proto__", "constructor", "prototype"].includes(f)
    ) && itemIdField !== quantityField;
  const endpoint = Deno.env.get("SWIGGY_FOOD_URL") ?? "";
  const approvedEnvironment =
    endpoint === "https://mcp-staging.swiggy.com/food" ||
    endpoint === "https://mcp.swiggy.com/food" &&
      Deno.env.get("SWIGGY_PRODUCTION_APPROVED") === "true";
  return {
    enabled: approvedEnvironment &&
      Deno.env.get("SWIGGY_ORDERING_ENABLED") === "true" &&
      Deno.env.get("SWIGGY_CART_CONTRACT_VERIFIED") === "true" &&
      Deno.env.get("SWIGGY_SESSION_RESUME_VERIFIED") === "true" &&
      ["rupees", "paise"].includes(unit) && fieldsValid,
    unit,
    itemIdField,
    quantityField,
  };
}
export interface AttemptStore {
  latest(): Promise<Json | null>;
  get(id: string): Promise<Json | null>;
  begin(id: string, data: Json): Promise<boolean>;
  save(id: string, data: Json): Promise<void>;
}
const bad = (message: string): never => {
  throw new HungiiError("HUNGII_BAD_INPUT", message);
};
const id = (value: unknown): string =>
  typeof value === "string" && value.length > 0 && value.length <= 150
    ? value
    : bad("Invalid identifier.");
const requestId = (value: unknown): string =>
  typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      value,
    )
    ? value
    : bad("Invalid checkout request.");
const active = (a: Json | null): boolean =>
  !!a && ["placing", "pending", "unresolved"].includes(a.phase);
export function paymentMethods(options: Json): Json[] {
  if (!Array.isArray(options.allMethods)) return [];
  return options.allMethods.filter((m: Json) =>
    typeof m.id === "string" && m.enabled !== false
  ).flatMap((m: Json) => {
    const base = {
      id: m.id,
      displayName: typeof m.displayName === "string" ? m.displayName : m.id,
      groupName: m.groupName,
    };
    if (options.cod?.available === true && options.cod.id === m.id) {
      return [{ ...base, paymentMethod: "Cash" }];
    }
    if (
      options.swiggyMoney?.available === true && options.swiggyMoney.id === m.id
    ) return [{ ...base, paymentMethod: "SwiggyPay" }];
    if (
      options.platforms?.mobile?.methods?.some((v: Json) => v.id === m.id) &&
      (m.kind === "intent" || m.kind === undefined)
    ) return [{ ...base, paymentMethod: "UPI", intentApp: m.id }];
    if (
      options.platforms?.desktop?.methods?.some((v: Json) => v.id === m.id) &&
      (m.kind === "qr" || m.kind === undefined)
    ) return [{ ...base, paymentMethod: "UPI", generateUPIQR: true }];
    return [];
  });
}
export function safeBridge(value: unknown): string | null {
  if (typeof value !== "string") return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password &&
        (url.hostname === "swiggy.com" || url.hostname.endsWith(".swiggy.com"))
      ? url.href
      : null;
  } catch {
    return null;
  }
}
export function attemptView(a: Json): Json {
  const p = a.provider ?? {};
  return {
    requestId: a.requestId,
    createdAt: a.createdAt,
    checkoutCart: a.cart,
    payment: {
      orderId: p.orderId ?? null,
      paasId: p.paasId ?? null,
      cartId: p.cartId ?? null,
      bridgeUrl: safeBridge(p.bridgeUrl),
      pollingIntervalInMs: Math.max(
        1000,
        Number(p.pollingIntervalInMs) || 5000,
      ),
      maxTimeToPollForInMs: Math.min(
        600000,
        Math.max(1000, Number(p.maxTimeToPollForInMs) || 60000),
      ),
      status: a.phase === "confirmed"
        ? "CONFIRMED"
        : a.phase === "failed"
        ? "FAILED"
        : a.phase === "pending"
        ? "PENDING_PAYMENT"
        : "UNRESOLVED",
    },
    paymentStatus: {
      orderId: p.orderId ?? null,
      status: a.status ?? a.phase,
      confirmed: a.phase === "confirmed",
      isTerminalFailure: a.phase === "failed",
      terminal: ["confirmed", "failed"].includes(a.phase),
    },
  };
}

// Provider contract details are verified during staging, never inferred from a mock.
// The store claim precedes the non-idempotent placement call and survives worker death.
export class FoodOrdering {
  constructor(
    private call: ToolCall,
    private store: AttemptStore,
    private config: OrderingConfig,
    private owner: string,
    private addressId: string,
    private secret: string,
    private now: () => number = Date.now,
  ) {}
  private requireEnabled() {
    if (!this.config.enabled) {
      throw new HungiiError(
        "HUNGII_ORDERING_PENDING",
        "Swiggy ordering is awaiting access and staging verification. Your tracker remains available.",
        409,
      );
    }
  }
  private async writable() {
    this.requireEnabled();
    if (active(await this.store.latest())) {
      throw new HungiiError(
        "HUNGII_CHECKOUT_UNRESOLVED",
        "Resolve your previous checkout before changing the basket or ordering again.",
        409,
      );
    }
  }
  private async cart(): Promise<Json> {
    const raw = await this.call("get_food_cart", { addressId: this.addressId });
    const cart = cartView(raw, this.config.unit);
    const items = raw.data?.items ?? [];
    cart.items = items.map((i: Json) => ({
      menuItemId: i.menu_item_id,
      name: i.name,
      quantity: i.quantity,
      total: money(i.total, this.config.unit),
      variants: i.variants ?? [],
      addons: i.addons ?? [],
    }));
    cart.fees = { tax: cart.taxes };
    cart.revision = await digest(
      JSON.stringify({ addressId: this.addressId, ...cart }),
    );
    return cart;
  }
  private validateCart(cart: Json) {
    if (
      !cart.cartId || !cart.restaurantId || !cart.items?.length ||
      !Number.isFinite(cart.payable) || cart.payable <= 0 ||
      cart.payable > 1000 ||
      cart.items.some((i: Json) =>
        !i.menuItemId || !Number.isInteger(i.quantity) || i.quantity < 1 ||
        i.quantity > 10
      )
    ) {
      throw new HungiiError(
        "HUNGII_CART_INVALID",
        "Refresh the basket. Swiggy checkout requires a verified total up to ₹1,000 and available items.",
        409,
      );
    }
  }
  private async labelledCart(): Promise<Json> {
    const cart = await this.cart();
    const addresses = await this.call("get_addresses", {});
    // Search at most 10 pages, under the request budget; no invented address label.
    let found = (addresses.addresses ?? []).find((a: Json) =>
      a.id === this.addressId
    );
    for (
      let page = 2;
      !found && page <= Math.min(addresses.pagination?.totalPages ?? 1, 10);
      page++
    ) {
      found = (await this.call("get_addresses", { page })).addresses?.find((
        a: Json,
      ) => a.id === this.addressId);
    }
    if (!found?.addressLine) {
      throw new HungiiError(
        "HUNGII_ADDRESS_REQUIRED",
        "Choose your saved delivery address again.",
        409,
      );
    }
    return { ...cart, address: found.addressLine };
  }
  async run(action: string, body: Json): Promise<Json> {
    this.requireEnabled();
    if (action === "checkout_resume") {
      const a = await this.store.latest();
      return a ? attemptView(a) : { payment: null };
    }
    if (action === "cart") return { cart: await this.labelledCart() };
    if (["cart_create", "cart_quantity", "cart_coupon"].includes(action)) {
      await this.writable();
      const current = await this.cart();
      if (action === "cart_coupon") {
        await this.call("apply_food_coupon", {
          addressId: this.addressId,
          couponCode: id(body.couponCode),
          ...(current.cartId ? { cartId: current.cartId } : {}),
        });
      } else {
        const restaurantId = action === "cart_create"
          ? id(body.restaurantId)
          : id(current.restaurantId);
        const dishId = id(body.dishId);
        const quantity = action === "cart_create" ? 1 : body.quantity;
        if (!Number.isInteger(quantity) || quantity < 0 || quantity > 10) {
          bad("Quantity must be between 0 and 10.");
        }
        if (
          current.items.length && current.restaurantId !== restaurantId &&
          body.replace !== true
        ) {
          throw new HungiiError(
            "HUNGII_REPLACE_CART",
            "This meal is from another restaurant. Confirm replacing your basket.",
            409,
          );
        }
        let items = current.restaurantId === restaurantId ? current.items : [];
        // A schema for nested customization members has not yet been verified.
        if (items.some((i: Json) => i.variants?.length || i.addons?.length)) {
          throw new HungiiError(
            "HUNGII_CUSTOMIZATION_PENDING",
            "This basket has options that Hungii cannot edit yet. Update those items in Swiggy.",
            409,
          );
        }
        if (quantity > 0) {
          const menu = await this.call("search_menu", {
            addressId: this.addressId,
            restaurantIdOfAddedItem: restaurantId,
            query: typeof body.query === "string"
              ? body.query.slice(0, 150)
              : dishId,
          });
          const item = menu.items?.find((i: Json) =>
            i.menu_item_id === dishId && i.restaurant_id === restaurantId
          );
          if (!item || item.inStock !== 1) {
            throw new HungiiError(
              "HUNGII_STOCK_CHANGED",
              "This item is unavailable. Choose another meal.",
              409,
            );
          }
          if (item.hasVariants || item.hasAddons) {
            throw new HungiiError(
              "HUNGII_CUSTOMIZATION_PENDING",
              "This meal needs portion or extra selections. Choose a meal without options until staging verifies that format.",
              409,
            );
          }
        }
        items = items.filter((i: Json) => i.menuItemId !== dishId);
        if (quantity > 0) items.push({ menuItemId: dishId, quantity });
        const cartItems = items.map((i: Json) => ({
          [this.config.itemIdField]: i.menuItemId,
          [this.config.quantityField]: i.quantity,
        }));
        await this.call("update_food_cart", {
          addressId: this.addressId,
          restaurantId,
          cartItems,
        });
      }
      return { cart: await this.labelledCart() };
    }
    if (action === "payment_options") {
      await this.writable();
      const options = await this.call("get_payment_options", {
        addressId: this.addressId,
      });
      const cart = await this.labelledCart();
      this.validateCart(cart);
      const methods = paymentMethods(options);
      if (!methods.length) {
        throw new HungiiError(
          "HUNGII_PAYMENT_UNAVAILABLE",
          "Swiggy has no supported payment option for this basket.",
          409,
        );
      }
      const quote = await seal(
        JSON.stringify({
          cart,
          methods,
          addressId: this.addressId,
          expires: this.now() + 120000,
        }),
        `${this.owner}:checkout-quote`,
        this.secret,
      );
      return {
        cart: { ...cart, quote },
        paymentOptions: { allMethods: methods },
      };
    }
    if (action === "checkout") {
      const key = requestId(body.requestId);
      const existing = await this.store.get(key);
      if (existing) return attemptView(existing);
      if (body.confirm !== true) {
        throw new HungiiError(
          "HUNGII_CONFIRM_REQUIRED",
          "Confirm the basket, address, total and payment method first.",
        );
      }
      const quote = JSON.parse(
        await unseal(
          idQuote(body.quote),
          `${this.owner}:checkout-quote`,
          this.secret,
        ),
      );
      if (quote.expires <= this.now() || quote.addressId !== this.addressId) {
        throw new HungiiError(
          "HUNGII_QUOTE_EXPIRED",
          "Your checkout review expired. Refresh payment options.",
          409,
        );
      }
      const cart = await this.cart();
      this.validateCart(cart);
      if (
        cart.revision !== quote.cart.revision || cart.payable !== body.payable
      ) {
        throw new HungiiError(
          "HUNGII_CART_CHANGED",
          "Your basket or price changed. Review the updated total before ordering.",
          409,
        );
      }
      const fresh = paymentMethods(
        await this.call("get_payment_options", { addressId: this.addressId }),
      );
      const method = quote.methods.find((m: Json) => m.id === body.methodId);
      if (
        !method ||
        !fresh.some((m) => JSON.stringify(m) === JSON.stringify(method))
      ) {
        throw new HungiiError(
          "HUNGII_PAYMENT_CHANGED",
          "Payment options changed. Choose a current method.",
          409,
        );
      }
      const attempt: Json = {
        requestId: key,
        phase: "placing",
        cart: quote.cart,
        addressId: this.addressId,
        createdAt: this.now(),
      };
      if (!await this.store.begin(key, attempt)) {
        throw new HungiiError(
          "HUNGII_CHECKOUT_UNRESOLVED",
          "A checkout is already in progress. Refresh its status.",
          409,
        );
      }
      // Any error after this point is ambiguous: NEVER replay place_food_order.
      try {
        const provider = await this.call("place_food_order", {
          addressId: this.addressId,
          paymentMethod: method.paymentMethod,
          ...(method.intentApp ? { intentApp: method.intentApp } : {}),
          ...(method.generateUPIQR ? { generateUPIQR: true } : {}),
          ...(body.note
            ? { noteToRestaurant: String(body.note).slice(0, 200) }
            : {}),
        });
        attempt.provider = provider;
        if (typeof provider.orderId !== "string" || !provider.orderId) {
          attempt.phase = "unresolved";
        } else if (
          provider.normalizedStatus === "success" &&
          provider.status !== "PENDING_PAYMENT"
        ) attempt.phase = "confirmed";
        else if (
          provider.status === "PENDING_PAYMENT" &&
          typeof provider.paasId === "string" &&
          provider.addressId === this.addressId &&
          Number.isFinite(provider.lat) && Number.isFinite(provider.lng)
        ) attempt.phase = "pending";
        else attempt.phase = "unresolved";
      } catch {
        attempt.phase = "unresolved";
      }
      await this.store.save(key, attempt);
      return attemptView(attempt);
    }
    if (action === "payment_status") {
      const key = requestId(body.requestId), a = await this.store.get(key);
      if (!a) {
        throw new HungiiError(
          "HUNGII_CHECKOUT_MISSING",
          "No checkout was found. Check your recent Swiggy orders before trying again.",
          409,
        );
      }
      if (a.phase !== "pending") return attemptView(a);
      const p = a.provider;
      if (
        a.lastPolled &&
        this.now() - a.lastPolled <
          Math.max(1000, Number(p.pollingIntervalInMs) || 5000)
      ) return attemptView(a);
      a.lastPolled = this.now();
      await this.store.save(key, a);
      const args = {
        paasId: p.paasId,
        orderId: p.orderId,
        addressId: p.addressId,
        lat: p.lat,
        lng: p.lng,
        ...(p.cartId ? { cartId: p.cartId } : {}),
      };
      const status = await this.call("check_payment_status", args);
      a.status = status.status;
      if (status.confirmed === true && status.isTerminalSuccess === true) {
        a.phase = "confirmed";
      } else if (
        status.isTerminalFailure === true && status.terminal === true
      ) {
        a.phase = ["failed", "cart_changed"].includes(
            String(status.status).toLowerCase(),
          )
          ? "failed"
          : "unresolved";
      } else if (
        status.isTerminalSuccess === true && status.terminal === true
      ) {
        const { paasId: _unused, ...confirmArgs } = args;
        const result = await this.call("confirm_order", confirmArgs);
        if (result.result === "success" && result.orderId === p.orderId) {
          a.phase = "confirmed";
        } else if (result.result === "failed") a.phase = "unresolved";
      }
      await this.store.save(key, a);
      return attemptView(a);
    }
    if (action === "order") {
      const latest = await this.store.latest();
      if (
        !latest || latest.phase !== "confirmed" ||
        latest.provider?.orderId !== body.orderId
      ) {
        throw new HungiiError(
          "HUNGII_ORDER_NOT_CONFIRMED",
          "Refresh checkout before tracking this order.",
          409,
        );
      }
      return {
        orderId: latest.provider.orderId,
        tracking: await this.call("track_food_order", {
          orderId: latest.provider.orderId,
        }),
      };
    }
    throw new HungiiError(
      "HUNGII_ACTION",
      "This checkout action is not supported yet.",
      409,
    );
  }
}
function idQuote(value: unknown): string {
  return typeof value === "string" && value.length <= 50000
    ? value
    : bad("Refresh your checkout review.");
}
