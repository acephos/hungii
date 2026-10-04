import type { SupabaseClient } from "@supabase/supabase-js";
import type { AttemptStore } from "./ordering.ts";
import { HungiiError, type Json } from "./errors.ts";
import { seal, unseal } from "./secrets.ts";
export class CheckoutStore implements AttemptStore {
  constructor(
    private db: SupabaseClient,
    private user: string,
    private secret: string,
  ) {}
  private decode(row: Json | null): Promise<Json | null> {
    return row
      ? unseal(
        row.ciphertext,
        `${this.user}:checkout:${row.request_id}`,
        this.secret,
      ).then(JSON.parse)
      : Promise.resolve(null);
  }
  private check(error: unknown) {
    if (error) {
      throw new HungiiError(
        "HUNGII_CHECKOUT_STORAGE",
        "Checkout could not be saved. Check status before trying again.",
        503,
      );
    }
  }
  async latest() {
    const { data, error } = await this.db.from("hungii_checkouts").select(
      "request_id,ciphertext",
    ).eq("user_id", this.user).order("created_at", { ascending: false }).limit(
      1,
    ).maybeSingle();
    this.check(error);
    return await this.decode(data);
  }
  async get(id: string) {
    const { data, error } = await this.db.from("hungii_checkouts").select(
      "request_id,ciphertext",
    ).eq("user_id", this.user).eq("request_id", id).maybeSingle();
    this.check(error);
    return await this.decode(data);
  }
  async begin(id: string, data: Json) {
    const result = await this.db.rpc("begin_hungii_checkout", {
      owner_id: this.user,
      checkout_id: id,
      encrypted_attempt: await seal(
        JSON.stringify(data),
        `${this.user}:checkout:${id}`,
        this.secret,
      ),
    });
    this.check(result.error);
    return result.data === true;
  }
  async save(id: string, data: Json) {
    const result = await this.db.from("hungii_checkouts").update({
      phase: data.phase,
      ciphertext: await seal(
        JSON.stringify(data),
        `${this.user}:checkout:${id}`,
        this.secret,
      ),
    }).eq("user_id", this.user).eq("request_id", id).select("request_id")
      .single();
    this.check(result.error);
    if (!result.data) this.check(true);
  }
}
