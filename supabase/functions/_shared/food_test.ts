import { cartView, discover, discovery, HungiiError, menuView, money, payload } from "./food.ts";
import { base64, seal, unseal } from "./secrets.ts";

function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}
Deno.test("discovery excludes closed/unknown restaurants and sold-out dishes without inventing nutrition", () => {
  const result = discovery({ restaurants: [{ id: "open", name: "R", availabilityStatus: "OPEN" }, { id: "closed", name: "C", availabilityStatus: "CLOSED" }, { id: "unknown", name: "U" }],
    dishes: [{ id: "1", restaurantId: "open", name: "Available", price: 125 }, { id: "2", restaurantId: "closed", name: "Closed" }, { id: "3", restaurantId: "unknown", name: "Unknown" }, { id: "4", restaurantId: "open", name: "Sold out", inStock: false }] }, "rupees");
  equal(result.meals.length, 1); equal(result.meals[0].id, "open:1"); equal(result.meals[0].nutrition, null); equal(result.meals[0].etaMinutes, null); equal(result.meals[0].stockConfirmed, false);
});
Deno.test("IDs survive duplicate names and duplicate response entries are removed", () => {
  const result = discovery({ restaurants: [{ id: "r", name: "R", availabilityStatus: "OPEN" }], dishes: [{ id: "a", restaurantId: "r", name: "Bowl" }, { id: "b", restaurantId: "r", name: "Bowl" }, { id: "a", restaurantId: "r", name: "Bowl" }] }, "unverified");
  equal(result.meals.map((m: any) => m.id), ["r:a", "r:b"]); equal(result.meals[0].itemPrice, null);
});
Deno.test("money units are explicit and malformed values stay unavailable", () => {
  equal(money(10000, "unverified"), null); equal(money(10000, "paise"), 100); equal(money(100, "rupees"), 100);
  equal(money(-1, "rupees"), null); equal(money("100", "rupees"), null); equal(money(Infinity, "rupees"), null);
});
Deno.test("nested cart uses upstream to_pay and zero discount does not claim an applied coupon", () => {
  const result = cartView({ addressId: "address", data: { pricing: { item_total: 200, delivery_charge: 30, taxes_and_charges: 10, to_pay: 219 }, offers: { coupon_applied: "SUGGESTED", coupon_discount: 0 } } }, "rupees");
  equal(result.payable, 219); equal(result.appliedCoupon, null); equal(result.couponDiscount, 0);
});
Deno.test("menu preserves restaurant/item IDs and rejects unavailable items", () => {
  const result = menuView({ items: [{ menu_item_id: "m", restaurant_id: "r", restaurant_name: "R", name: "Dish", inStock: 1, price: 150 }, { menu_item_id: "out", restaurant_id: "r", inStock: 0 }] }, "rupees");
  equal(result.meals.length, 1); equal(result.meals[0].dishId, "m"); equal(result.meals[0].nutrition, null);
});
Deno.test("empty budget collection broadens once, without a repeated suggestion loop", async () => {
  const calls: unknown[] = [];
  const result = await discover(async (name, args) => { calls.push({ name, args: { ...args } }); return calls.length === 1 ? { dishes: [], restaurants: [] } : { dishes: [{ id: "live" }] }; }, "selected", "rice", "STORE_99");
  equal(calls, [{ name: "search_restaurants", args: { addressId: "selected", query: "rice", collection: "STORE_99" } }, { name: "search_restaurants", args: { addressId: "selected", query: "rice" } }]);
  equal(result.broadenedBudgetCollection, true);
});
Deno.test("MCP envelopes accept structured/text JSON and reject prose or upstream failure", () => {
  equal(payload({ structuredContent: { success: true, data: { items: [] } } }), { items: [] });
  equal(payload({ content: [{ type: "text", text: '{"success":true,"data":{"items":[]}}' }] }), { items: [] });
  for (const response of [{ content: [{ type: "text", text: "Here are some meals" }] }, { structuredContent: { success: false, error: { message: "Private upstream content" } } }]) {
    try { payload(response); throw new Error("Expected rejection"); } catch (e) { if (!(e instanceof HungiiError)) throw e; }
  }
});
Deno.test("token ciphertext cannot be replayed for another user or used after tampering", async () => {
  const key = base64(crypto.getRandomValues(new Uint8Array(32)));
  const encrypted = await seal("test-token", "user-a:swiggy", key);
  equal(await unseal(encrypted, "user-a:swiggy", key), "test-token");
  for (const [owner, value] of [["user-b:swiggy", encrypted], ["user-a:swiggy", encrypted.replace(/.$/, "!")]]) {
    let rejected = false; try { await unseal(value, owner, key); } catch { rejected = true; }
    equal(rejected, true);
  }
});
