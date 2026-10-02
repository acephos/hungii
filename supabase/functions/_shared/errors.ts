export class HungiiError extends Error {
  constructor(public code: string, message: string, public status = 400, public retryAfter?: number) { super(message); }
}
export type Json = Record<string, any>;
