import { Server } from "npm:@modelcontextprotocol/sdk@1.31.0/server/index.js";
import { WebStandardStreamableHTTPServerTransport } from "npm:@modelcontextprotocol/sdk@1.31.0/server/webStandardStreamableHttp.js";
import {
  CallToolRequestSchema,
  ErrorCode,
  ListToolsRequestSchema,
  McpError,
} from "npm:@modelcontextprotocol/sdk@1.31.0/types.js";
import { Ajv } from "ajv";
import { MockFailure, MockFood, schemas } from "./catalogue.ts";
import { readJsonObject } from "../supabase/functions/_shared/http.ts";
import { HungiiError } from "../supabase/functions/_shared/errors.ts";
export function mockProvider(jsonResponses = true) {
  const sessions = new Map<
    string,
    { transport: WebStandardStreamableHTTPServerTransport; token: string }
  >();
  const users = new Map<string, MockFood>();
  const servers = new Set<Server>();
  const stats = {
    initializations: 0,
    calls: [] as string[],
    get activeServers() {
      return servers.size;
    },
  };
  const ajv = new Ajv({ strict: false });
  const getState = (token: string) => {
    let state = users.get(token);
    if (!state) {
      if (users.size >= 128) throw new Error("Simulator capacity reached");
      state = new MockFood();
      users.set(token, state);
    }
    return state;
  };
  const handler = async (request: Request): Promise<Response> => {
    if (new URL(request.url).pathname !== "/food") {
      return new Response("not found", { status: 404 });
    }
    const token =
      request.headers.get("authorization")?.replace(/^Bearer /, "") ?? "";
    if (
      !/^hungii-local-demo-(?:not-provider|[A-Za-z0-9_-]{16,96})$/.test(token)
    ) return new Response("Synthetic demo session required", { status: 401 });
    const id = request.headers.get("mcp-session-id");
    const session = id ? sessions.get(id) : undefined;
    if (id && (!session || session.token !== token)) {
      return new Response("expired or unowned session", { status: 404 });
    }
    let body;
    if (request.method === "POST") {
      try {
        body = await readJsonObject(request, 100_000);
      } catch (error) {
        return new Response("invalid MCP request", {
          status: error instanceof HungiiError ? error.status : 400,
        });
      }
    }
    let transport = session?.transport;
    let startedServer: Server | undefined;
    if (!transport) {
      if (request.method !== "POST") {
        return new Response("method", { status: 405 });
      }
      if (body?.method !== "initialize") {
        return new Response("initialize first", { status: 400 });
      }
      if (sessions.size >= 128 || users.size >= 128 && !users.has(token)) {
        return new Response("capacity", {
          status: 429,
          headers: { "Retry-After": "30" },
        });
      }
      const state = getState(token);
      stats.initializations++;
      transport = new WebStandardStreamableHTTPServerTransport({
        sessionIdGenerator: () => crypto.randomUUID(),
        enableJsonResponse: jsonResponses,
        onsessioninitialized: (id: string) =>
          sessions.set(id, { transport: transport!, token }),
        onsessionclosed: (id: string) => sessions.delete(id),
      });
      const server = new Server({
        name: "hungii-synthetic-swiggy-food",
        version: "0.7.0",
      }, { capabilities: { tools: {} } });
      servers.add(server);
      startedServer = server;
      server.onclose = () => {
        servers.delete(server);
      };
      server.setRequestHandler(
        ListToolsRequestSchema,
        () => ({ tools: schemas }),
      );
      server.setRequestHandler(CallToolRequestSchema, async (request: any) => {
        const tool = schemas.find((t) => t.name === request.params.name);
        const args = request.params.arguments ?? {};
        if (!tool || !ajv.compile(tool.inputSchema)(args)) {
          throw new McpError(
            ErrorCode.InvalidParams,
            "Arguments differ from the documented Food input schema.",
          );
        }
        stats.calls.push(tool.name);
        stats.calls = stats.calls.slice(-300);
        try {
          const envelope = {
            success: true,
            data: await state.call(tool.name, args),
          };
          return {
            content: [{ type: "text", text: JSON.stringify(envelope) }],
            structuredContent: envelope,
          };
        } catch (error) {
          const known = error instanceof MockFailure;
          const envelope = {
            success: false,
            error: {
              code: known ? error.code : "HUNGII_MOCK_FAILURE",
              message: known ? error.message : "Synthetic tool call failed.",
            },
          };
          return {
            isError: true,
            content: [{ type: "text", text: JSON.stringify(envelope) }],
            structuredContent: envelope,
          };
        }
      });
      await server.connect(transport);
    }
    try {
      return await transport.handleRequest(request, { parsedBody: body });
    } finally {
      if (startedServer && !transport.sessionId) await startedServer.close();
    }
  };
  return {
    handler,
    stats,
    getState,
    reset: (token: string) => users.set(token, new MockFood()),
    close: async () => {
      await Promise.all([...servers].map((s) => s.close()));
      sessions.clear();
      users.clear();
    },
  };
}
if (import.meta.main) {
  const provider = mockProvider();
  console.info("Hungii synthetic MCP · 20 Food tools · loopback only");
  Deno.serve({ hostname: "127.0.0.1", port: 8890 }, provider.handler);
}
