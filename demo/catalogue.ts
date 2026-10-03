// Hungii-authored data. Estimates, prices and promotions are fictional.
import data from "./fixtures.json" with { type: "json" };
import toolSchemas from "./tool-schemas.json" with { type: "json" };
export const restaurants = data.restaurants;
export const dishes = data.dishes;
export const schemas = toolSchemas;
export type Json = Record<string, any>;
export const rounded = (n: number) => Math.round(n * 100) / 100;
export class MockFailure extends Error {
  constructor(public code: string, message: string) {
    super(message);
  }
}
const fail = (code: string, message: string): never => {
  throw new MockFailure("HUNGII_MOCK_" + code, message);
};
export const promotions = [{
  code: "FUEL70",
  minimum: 219,
  discount: 70,
  title: "₹70 off your fuel",
  description: "Synthetic offer · ₹70 off food above ₹219.",
}, {
  code: "FIRST50",
  minimum: 299,
  discount: 50,
  title: "₹50 off your first bite",
  description: "Synthetic offer · ₹50 off above ₹299.",
}, {
  code: "FREESHIP",
  minimum: 299,
  discount: 29,
  title: "Delivery on us",
  description: "Synthetic offer · free ₹29 delivery above ₹299.",
}];
type Pending = {
  id: string;
  paasId: string;
  cartId: string;
  revision: number;
  addressId: string;
  lat: number;
  lng: number;
  status: string;
  confirmed: boolean;
  cart: Json;
  created: number;
  paymentMethod: string;
  note: string;
};
export class MockFood {
  addresses: Json[] = [{
    id: "demo-address",
    addressTag: "Home · demo",
    addressCategory: "HOME",
    addressLine:
      "Flat 302, Demo House, Bandra West, Mumbai · synthetic address",
    latitude: 19.0596,
    longitude: 72.8295,
    phoneNumber: "",
  }, {
    id: "demo-campus",
    addressTag: "Campus · demo",
    addressCategory: "OTHER",
    addressLine:
      "Student hostel, Demo Campus, Powai, Mumbai · synthetic address",
    latitude: 19.1197,
    longitude: 72.905,
    phoneNumber: "",
  }];
  cartId = "mock-cart-" + crypto.randomUUID();
  revision = 0;
  restaurantId: string | null = null;
  cartItems: Json[] = [];
  coupon: string | null = null;
  payments = new Map<string, Pending>();
  orders = new Map<string, Pending>();
  reports: Json[] = [];
  listed = false;
  sequence = 100000;
  events: { tool: string; time: number }[] = [];
  address(id: string) {
    const a = this.addresses.find((a) => a.id === id);
    if (!a || !this.listed) {
      fail(
        "ADDRESS_REQUIRED",
        "Call get_addresses and select a returned address first.",
      );
    }
    return a!;
  }
  dish(id: string) {
    const d = dishes.find((d) => d.id === id);
    if (!d) fail("ITEM", "This meal is not in the synthetic menu.");
    return d!;
  }
  price(item: Json) {
    const d = this.dish(item.menu_item_id);
    let p = d.price;
    for (const v of item.variants ?? []) {
      const g = d.variantsV2?.find((g) => g.groupId === v.group_id);
      const choice = g?.variations.find((x) => x.id === v.variation_id);
      if (!choice) fail("VARIANT", "Choose a returned variant ID.");
      p += choice!.price;
    }
    for (const a of item.addons ?? []) {
      const g = d.addons?.find((g) => g.groupId === a.group_id);
      const choice = g?.choices.find((x) => x.id === a.addon_id);
      if (!choice) fail("ADDON", "Choose a returned addon ID.");
      p += choice!.price;
    }
    return p;
  }
  quote(items: Json[], coupon: string | null = null): Json {
    const itemTotal = rounded(
      items.reduce((sum, i) => sum + this.price(i) * i.quantity, 0),
    );
    const delivery = items.length ? 29 : 0,
      packaging = items.length ? 8 : 0,
      platform = items.length ? 5 : 0,
      tax = rounded(itemTotal * .05);
    const offer = promotions.find((p) =>
      p.code === coupon && itemTotal >= p.minimum
    );
    const discount = offer?.discount ?? 0;
    return {
      itemTotal,
      delivery,
      packaging,
      platform,
      tax,
      discount,
      payable: rounded(
        Math.max(
          0,
          itemTotal + delivery + packaging + platform + tax - discount,
        ),
      ),
      appliedCoupon: offer?.code ?? null,
    };
  }
  cart(addressId: string): Json {
    this.address(addressId);
    const q = this.quote(this.cartItems, this.coupon);
    const r = restaurants.find((r) => r.id === this.restaurantId);
    return {
      addressId,
      availablePaymentMethods: ["UPI", "Cash"],
      data: {
        cart_id: this.cartId,
        restaurant: r
          ? {
            id: r.id,
            name: r.name,
            deliverySubtitle: `${r.deliveryTimeMinutes}–${
              r.deliveryTimeMinutes + 5
            } min`,
          }
          : {},
        items: this.cartItems.map((i) => {
          const d = this.dish(i.menu_item_id);
          const total = rounded(this.price(i) * i.quantity);
          return {
            menu_item_id: d.id,
            name: d.name,
            quantity: i.quantity,
            subtotal: total,
            total,
            final_price: total,
            is_veg: d.isVeg,
            in_stock: 1,
            variants: i.variants ?? [],
            addons: i.addons ?? [],
            valid_addons: d.addons ?? [],
          };
        }),
        item_count: this.cartItems.reduce((s, i) => s + i.quantity, 0),
        pricing: {
          item_total: q.itemTotal,
          delivery_charge: q.delivery,
          taxes_and_charges: rounded(q.packaging + q.platform + q.tax),
          to_pay: q.payable,
        },
        offers: {
          coupon_applied: q.appliedCoupon,
          coupon_discount: q.discount,
          free_delivery_applied: q.appliedCoupon === "FREESHIP",
        },
      },
    };
  }
  invalidate() {
    this.revision++;
    for (const p of this.payments.values()) {
      if (!p.confirmed && p.status === "PENDING") p.status = "CART_CHANGED";
    }
  }
  payment(p: Pending) {
    return {
      orderId: p.id,
      paasId: p.paasId,
      transactionId: "mock-txn-" + p.id,
      upiIntentUrl: "https://hungii.invalid/simulated-payment/" + p.paasId,
      bridgeUrl: "https://hungii.invalid/simulated-payment/" + p.paasId,
      isQrFlow: p.paymentMethod === "QR",
      pollingIntervalInMs: 1500,
      maxTimeToPollForInMs: 60000,
      paymentMethod: "UPI",
      status: "PENDING_PAYMENT",
      normalizedStatus: "pending",
      totalAmount: p.cart.pricing.to_pay,
      restaurantName: p.cart.restaurant.name,
      deliveryAddress: this.address(p.addressId).addressLine,
      addressId: p.addressId,
      cartId: p.cartId,
      lat: p.lat,
      lng: p.lng,
    };
  }
  confirmed(p: Pending) {
    return {
      orderId: p.id,
      status: "CONFIRMED",
      normalizedStatus: "success",
      items: p.cart.items,
      restaurantName: p.cart.restaurant.name,
      restaurantAddress: "Synthetic kitchen, Mumbai",
      totalAmount: p.cart.pricing.to_pay,
      estimatedDelivery: "20–25 min · simulated",
      deliveryAddress: this.address(p.addressId).addressLine,
    };
  }
  settle(paasId: string, outcome: string) {
    const p = this.payments.get(paasId);
    if (!p) fail("PAYMENT", "Payment session not found.");
    if (p!.status !== "PENDING") return;
    if (!["SUCCESS", "FAILED", "CANCELLED", "EXPIRED"].includes(outcome)) {
      fail("OUTCOME", "Unknown simulation outcome.");
    }
    p!.status = outcome;
  }
  confirm(p: Pending) {
    if (p.confirmed) return;
    if (p.status !== "SUCCESS" && p.paymentMethod !== "Cash") {
      fail(
        "PAYMENT_PENDING",
        "Payment has not succeeded. No order has been placed.",
      );
    }
    if (p.revision !== this.revision || p.cartId !== this.cartId) {
      fail(
        "CART_CHANGED",
        "Cart changed during payment. Review the latest total.",
      );
    }
    p.confirmed = true;
    this.orders.set(p.id, p);
    this.cartItems = [];
    this.restaurantId = null;
    this.coupon = null;
    this.cartId = "mock-cart-" + crypto.randomUUID();
    this.revision++;
  }
  async call(name: string, a: Json): Promise<Json> {
    this.events.push({ tool: name, time: Date.now() });
    this.events = this.events.slice(-100);
    if (name === "get_addresses") {
      this.listed = true;
      const page = a.page ?? 1, size = Math.min(10, a.pageSize ?? 10);
      return {
        addresses: this.addresses.slice((page - 1) * size, page * size),
        pagination: {
          page,
          pageSize: size,
          total: this.addresses.length,
          totalPages: Math.ceil(this.addresses.length / size),
          hasMore: page * size < this.addresses.length,
        },
      };
    }
    if (name === "create_address") {
      const id = "mock-address-" + crypto.randomUUID();
      this.addresses.push({ ...a, id, addressLine: a.fullAddress });
      return { addressId: id };
    }
    if (name === "delete_address") {
      this.address(a.addressId);
      if (this.cartItems.length) {
        fail("ADDRESS_CART", "Clear your cart before deleting an address.");
      }
      this.addresses = this.addresses.filter((x) => x.id !== a.addressId);
      return { statusCode: 0, statusMessage: "Synthetic address deleted" };
    }
    if (a.addressId) this.address(a.addressId);
    if (name === "search_restaurants" || name === "search_menu") {
      const words = String(a.query ?? "").toLowerCase().split(/\W+/).map((w) =>
        w.endsWith("s") ? w.slice(0, -1) : w
      ).filter((w) =>
        w.length > 2 &&
        !["food", "meal", "meals", "healthy", "want", "some"].includes(w)
      );
      let found = dishes.filter((d) =>
        (!a.restaurantIdOfAddedItem ||
          d.restaurantId === a.restaurantIdOfAddedItem) &&
        (a.vegFilter !== 1 || d.isVeg) && (!words.length || words.some((w) =>
          (d.name + " " + d.description + " " + restaurants.find((r) =>
            r.id === d.restaurantId
          )?.name).toLowerCase().includes(w)
        ))
      );
      if (a.collection === "EATRIGHT") {
        found = found.filter((d) => d.nutrition.protein[1] >= 20 && !d.side);
      }
      if (a.collection === "BOLT") {
        found = found.filter((d) =>
          (restaurants.find((r) => r.id === d.restaurantId)
            ?.deliveryTimeMinutes ?? 99) <= 19
        );
      }
      if (a.collection === "STORE_99") {
        found = found.filter((d) => d.price <= 99);
      }
      const offset = a.offset ?? 0,
        items = found.slice(offset, offset + 24),
        hasMore = offset + 24 < found.length;
      if (name === "search_restaurants") {
        return {
          restaurants: restaurants.filter((r) =>
            found.some((d) => d.restaurantId === r.id)
          ),
          dishes: items.map((
            {
              nutrition,
              nutritionSource,
              tags,
              photo,
              side,
              variantsV2,
              addons,
              ...d
            },
          ) => ({
            ...d,
            restaurantName: restaurants.find((r) => r.id === d.restaurantId)
              ?.name,
          })),
          query: a.query,
          hasMore,
          ...(hasMore ? { nextOffset: offset + 24 } : {}),
        };
      }
      return {
        items: items.map((d) => ({
          menu_item_id: d.id,
          restaurant_id: d.restaurantId,
          restaurant_name: restaurants.find((r) => r.id === d.restaurantId)
            ?.name,
          name: d.name,
          price: d.price,
          isVeg: d.isVeg,
          inStock: d.inStock ? 1 : 0,
          hasVariants: !!d.variantsV2,
          hasAddons: !!d.addons,
          ...(a.restaurantIdOfAddedItem
            ? { variantsV2: d.variantsV2, addons: d.addons }
            : {}),
        })),
        query: a.query,
        restaurantIdOfAddedItem: a.restaurantIdOfAddedItem,
        totalItems: found.length,
        hasMore,
        ...(hasMore ? { nextOffset: offset + 24 } : {}),
      };
    }
    if (name === "get_restaurant_menu") {
      const r = restaurants.find((r) => r.id === a.restaurantId);
      if (!r) fail("RESTAURANT", "Restaurant not found.");
      return {
        restaurantId: r!.id,
        restaurantName: r!.name,
        categories: [{
          name: "Meals",
          items: dishes.filter((d) => d.restaurantId === r!.id).map((d) => ({
            id: d.id,
            name: d.name,
            price: d.price,
            isVeg: d.isVeg,
            inStock: d.inStock,
          })),
        }],
      };
    }
    if (name === "get_food_cart") return this.cart(a.addressId);
    if (name === "flush_food_cart") {
      this.invalidate();
      this.cartItems = [];
      this.restaurantId = null;
      this.coupon = null;
      return { statusCode: 0, statusMessage: "Synthetic cart cleared" };
    }
    if (name === "update_food_cart") {
      const r = restaurants.find((r) => r.id === a.restaurantId);
      if (!r || r.availabilityStatus !== "OPEN") {
        fail("CLOSED", "This restaurant is closed.");
      }
      if (a.cartItems.length > 20) {
        fail("QUANTITY", "Synthetic carts support at most 20 lines.");
      }
      const items = a.cartItems.map((i: Json) => {
        if (
          !Number.isInteger(i.quantity) || i.quantity < 1 || i.quantity > 10
        ) fail("QUANTITY", "Choose 1–10 portions per meal.");
        const d = this.dish(i.menu_item_id);
        if (d.restaurantId !== a.restaurantId) {
          fail("RESTAURANT", "One restaurant per cart.");
        }
        if (!d.inStock) {
          fail("SOLD_OUT", "This meal is sold out. Choose another.");
        }
        if (d.variantsV2 && !i.variants?.length) {
          fail("VARIANT", "Select a portion first.");
        }
        if ((i.addons ?? []).length > 2) {
          fail("ADDON", "Choose at most two addons.");
        }
        this.price(i);
        return {
          menu_item_id: i.menu_item_id,
          quantity: i.quantity,
          variants: i.variants ?? [],
          addons: i.addons ?? [],
        };
      });
      this.invalidate();
      this.cartItems = items;
      this.restaurantId = items.length ? a.restaurantId : null;
      this.coupon = this.quote(items, this.coupon).appliedCoupon;
      return {
        ...this.cart(a.addressId),
        statusCode: 0,
        statusMessage: "Synthetic cart updated",
      };
    }
    if (name === "fetch_food_coupons") {
      const total = this.quote(this.cartItems).itemTotal;
      const offers = promotions.filter((p) =>
        !a.couponCode || p.code === a.couponCode
      ).map((p) => ({
        id: p.code,
        applicable: total >= p.minimum,
        applicabilityStatus: total >= p.minimum
          ? "APPLICABLE"
          : "MINIMUM_NOT_MET",
        title: p.title,
        description: p.description,
        subtitle: `Minimum food value ₹${p.minimum}`,
        terms_and_conditions: {
          bullet_texts: [
            `Minimum food value ₹${p.minimum}`,
            `Save ₹${p.discount}`,
            "Synthetic offer · no real provider discount",
          ],
        },
      }));
      return {
        coupon_sections: [{
          title: "Offers for you",
          type: "COUPONS",
          coupons: offers,
        }],
        summary: {
          total_coupons: offers.length,
          applicable_coupons: offers.filter((o) => o.applicable).length,
          sections_count: 1,
        },
      };
    }
    if (name === "apply_food_coupon") {
      if (a.cartId && a.cartId !== this.cartId) {
        fail("CART_CHANGED", "Cart changed. Refresh before applying offers.");
      }
      const offer = promotions.find((p) => p.code === a.couponCode);
      if (!offer || this.quote(this.cartItems).itemTotal < offer.minimum) {
        fail(
          "COUPON",
          "Your food subtotal does not meet this offer’s minimum.",
        );
      }
      this.invalidate();
      this.coupon = offer!.code;
      const q = this.quote(this.cartItems, this.coupon);
      return {
        statusCode: 0,
        statusMessage: "Synthetic coupon applied",
        data: {
          cart_id: this.cartId,
          pricing: {
            item_total: q.itemTotal,
            delivery_fee: q.delivery,
            packaging_fee: q.packaging,
            taxes: q.tax,
            coupon_discount: q.discount,
            to_pay: q.payable,
          },
          offers: { coupon_applied: offer!.code, coupon_discount: q.discount },
          item_count: this.cartItems.reduce((s, i) => s + i.quantity, 0),
        },
      };
    }
    if (name === "get_payment_options") {
      return {
        allMethods: [{
          id: "mock-upi",
          groupName: "UPI",
          displayName: "UPI app · simulated",
          kind: "intent",
          enabled: true,
        }, {
          id: "mock-qr",
          groupName: "UPI",
          displayName: "UPI QR · simulated",
          kind: "qr",
          enabled: true,
        }, {
          id: "Cash",
          groupName: "CASH",
          displayName: "Cash on delivery · simulated",
          enabled: true,
        }],
        cod: { available: true, id: "Cash", displayName: "Cash on delivery" },
        swiggyMoney: {
          available: false,
          id: "SwiggyPay",
          displayName: "Swiggy Money",
        },
        paymentAmount: String(this.quote(this.cartItems, this.coupon).payable),
        addressId: a.addressId,
        placeOrderToolName: "place_food_order",
      };
    }
    if (name === "place_food_order") {
      if (!this.cartItems.length) {
        fail("EMPTY_CART", "Add a meal before checkout.");
      }
      if (!["UPI", "Cash"].includes(a.paymentMethod)) {
        fail("METHOD", "Choose a returned payment method.");
      }
      const existing = [...this.payments.values()].find((p) =>
        !p.confirmed && p.status === "PENDING" &&
        p.revision === this.revision && p.addressId === a.addressId
      );
      if (existing) return this.payment(existing);
      const address = this.address(a.addressId),
        id = String(++this.sequence),
        paasId = "mock-paas-" + crypto.randomUUID();
      const p: Pending = {
        id,
        paasId,
        cartId: this.cartId,
        revision: this.revision,
        addressId: a.addressId,
        lat: address.latitude,
        lng: address.longitude,
        status: "PENDING",
        confirmed: false,
        cart: structuredClone(this.cart(a.addressId).data),
        created: Date.now(),
        paymentMethod: a.paymentMethod === "Cash"
          ? "Cash"
          : a.generateUPIQR
          ? "QR"
          : "UPI",
        note: a.noteToRestaurant ?? "",
      };
      this.payments.set(paasId, p);
      if (p.paymentMethod === "Cash") {
        p.status = "SUCCESS";
        this.confirm(p);
        return this.confirmed(p);
      }
      return this.payment(p);
    }
    if (name === "check_payment_status") {
      const p = this.payments.get(a.paasId);
      if (!p) fail("PAYMENT", "Payment not found.");
      if (
        a.orderId && a.orderId !== p!.id ||
        a.cartId && a.cartId !== p!.cartId ||
        a.addressId && a.addressId !== p!.addressId
      ) fail("PAYMENT_CONTEXT", "Payment identifiers do not match.");
      if (Date.now() - p!.created > 60000 && p!.status === "PENDING") {
        p!.status = "EXPIRED";
      }
      return {
        paasId: p!.paasId,
        orderId: p!.id,
        status: p!.status,
        terminal: p!.status !== "PENDING",
        isTerminalSuccess: p!.status === "SUCCESS",
        isTerminalFailure: !["PENDING", "SUCCESS"].includes(p!.status),
        confirmed: p!.confirmed,
        orderStatus: p!.confirmed ? "CONFIRMED" : "PENDING_PAYMENT",
        cartTotal: p!.cart.pricing.to_pay,
      };
    }
    if (name === "confirm_order") {
      const p = [...this.payments.values()].find((p) => p.id === a.orderId);
      if (!p) fail("ORDER", "Order not found.");
      if (
        p!.addressId !== a.addressId || p!.lat !== a.lat || p!.lng !== a.lng ||
        a.cartId && p!.cartId !== a.cartId
      ) fail("CONFIRM_CONTEXT", "Echo the exact checkout context.");
      this.confirm(p!);
      return { orderId: p!.id, result: "success", orderStatus: "CONFIRMED" };
    }
    if (name === "get_food_orders") {
      return {
        orders: [...this.orders.values()].map((p) => ({
          orderId: p.id,
          restaurantId: p.cart.restaurant.id,
          restaurantName: p.cart.restaurant.name,
          orderTotal: String(p.cart.pricing.to_pay),
          orderStatus: "CONFIRMED",
          orderType: "FOOD",
          orderedItems: p.cart.items.map((i: Json) =>
            `${i.quantity} × ${i.name}`
          ).join(", "),
          orderedTime: new Date(p.created).toISOString(),
          isActiveOrder: true,
          actions: [],
        })),
      };
    }
    if (name === "get_food_order_details") {
      const p = this.orders.get(a.orderId);
      if (!p) fail("ORDER", "No confirmed order with this ID.");
      return {
        order: {
          order_id: Number(p!.id),
          restaurant_id: p!.cart.restaurant.id,
          restaurant_name: p!.cart.restaurant.name,
          order_items: p!.cart.items.map((i: Json) => ({
            item_id: i.menu_item_id,
            name: i.name,
            quantity: String(i.quantity),
            total: String(i.total),
            subtotal: String(i.subtotal),
            final_price: String(i.final_price),
            is_veg: String(i.is_veg),
          })),
          order_status: "CONFIRMED",
          order_total: p!.cart.pricing.to_pay,
          item_total: p!.cart.pricing.item_total,
          order_tax: p!.cart.pricing.taxes_and_charges,
          order_discount: p!.cart.offers.coupon_discount,
          coupon_discount: p!.cart.offers.coupon_discount,
          order_delivery_charge: 29,
          delivery_address: { address: this.address(p!.addressId).addressLine },
          charges: { total: String(p!.cart.pricing.to_pay) },
          payment_method: p!.paymentMethod,
        },
      };
    }
    if (name === "get_food_delivery_status") {
      if (!this.orders.has(a.orderId)) {
        fail("ORDER", "Only confirmed orders can be tracked.");
      }
      return {
        orderId: a.orderId,
        deliveryBy: Date.now() + 20 * 60000,
        serverNow: Date.now(),
        pollIntervalSec: 15,
        etaText: "20–25 min · simulated",
        statusText: "Kitchen is preparing your meal · simulated",
        cancelled: false,
        delivered: false,
      };
    }
    if (name === "track_food_order") {
      return {
        orders: [...this.orders.values()].filter((p) =>
          !a.orderId || a.orderId === p.id
        ).map((p) => ({
          orderId: p.id,
          title: "Preparing your meal",
          subtitle: p.cart.restaurant.name,
          etaText: "20–25 min · simulated",
          orderStatus: "CONFIRMED",
          progressPercentage: "25",
          pollingDuration: "15",
        })),
      };
    }
    if (name === "report_error") {
      this.reports.push(a);
      return {
        reported: true,
        message: "Report stored in the local simulator only.",
      };
    }
    return fail("TOOL", "Unsupported simulator tool.");
  }
}
// Legacy test helper. Each mockProvider uses its own MockFood instance.
export function fixture(name: string, a: Json) {
  return new MockFood().call(name, a);
}
