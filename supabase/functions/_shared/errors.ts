export class HungiiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status = 400,
    public retryAfter?: number,
  ) {
    super(message);
  }
}
/** Provider and request payloads are validated at use; this is their loose shape. */
// deno-lint-ignore no-explicit-any
export type Json = Record<string, any>;
