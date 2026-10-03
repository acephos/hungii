"""Google ADK + Groq Free. No local model, paid fallback or payment-capable agent."""
import asyncio
import json
import logging
import os
import re
import time
from pathlib import Path
from collections import deque
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from google.adk.agents import LlmAgent
from google.adk.models.lite_llm import LiteLlm
from google.adk.runners import InMemoryRunner
from google.adk.agents.run_config import RunConfig
from google.adk.tools.mcp_tool import McpToolset
from google.adk.tools.mcp_tool.mcp_session_manager import StreamableHTTPConnectionParams
from google.genai import types
load_dotenv(Path(__file__).with_name('.env.agent'))
logging.getLogger('LiteLLM').setLevel(logging.CRITICAL)
logging.getLogger('google.adk').setLevel(logging.CRITICAL)
# Only the authored synthetic MCP server is reachable; tool list cannot place/pay.
READ_TOOLS=['get_addresses','search_restaurants','search_menu','get_food_cart','fetch_food_coupons']
REQUESTS = deque()
LOCK = asyncio.Lock()
SYSTEM='''You are Hungii, a concise meal-planning companion. Speak naturally, one short paragraph, no lectures. Help with cravings, daily calorie/macros/rupee allowance, meals remaining, saved meals, app navigation, cart and checkout. User context is authoritative. All provider data is synthetic; never claim real Swiggy availability, nutrition or payment. For meal requests use get_addresses first, then search_restaurants with that returned addressId and a specific dish/craving query. Do not guess IDs. When needed search_menu scoped to a returned restaurant. Budget and nutrition ranges in context are estimates. The app handles ranking. Changes are ALWAYS proposals, never completed changes. Even after propose_app_action, say "Review the proposed change below" and never "updated", "now", or "set" as if already applied. Propose one app action when helpful; the app asks confirmation before changing totals, saving, creating a cart or starting checkout. Never claim food eaten just because ordered. Never claim order placed: you cannot place or pay. Limit tool use to 3 calls, avoid giant enumerations, ask one question if necessary.''' 
async def strip_reasoning(callback_context, llm_request):
    # Groq rejects reasoning_content on assistant history. Keep tool calls and
    # public answers while removing internal reasoning before serialization.
    for content in llm_request.contents:
        if content.parts:
            content.parts = [part for part in content.parts if not part.thought]
    return None


async def compact_mcp(tool, args, tool_context, tool_response):
    # Keep fixture responses small enough for the free inference token budget.
    if tool.name == 'propose_app_action':
        return None
    value = tool_response
    if hasattr(value, 'model_dump'):
        value = value.model_dump(exclude_none=True)
    if isinstance(value, dict):
        if value.get('structuredContent'):
            value = value['structuredContent']
        elif value.get('content'):
            try:
                value = json.loads(next(block['text'] for block in value['content'] if block.get('type') == 'text'))
            except (StopIteration, KeyError, ValueError):
                pass

    def trim(value):
        if isinstance(value, list):
            return [trim(item) for item in value[:6]]
        if isinstance(value, dict):
            return {key: trim(item) for key, item in value.items()
                    if key not in ['imageId', 'imageUrl', 'images', 'variantsV2', 'addons']}
        return value
    return trim(value)


app = FastAPI()


@app.get('/health')
async def health():
    return {'configured': bool(os.environ.get('GROQ_API_KEY')),
            'provider': 'Groq Free', 'sdk': 'Google ADK', 'localModel': False}


MAX_BODY_BYTES = 50_000
CONTEXT_KEYS = {
    'caloriesLeft', 'proteinLeft', 'carbsLeft', 'fatLeft', 'moneyLeft',
    'allowance', 'spent', 'goals', 'opportunities', 'query', 'taste',
    'screen', 'winner', 'cart', 'candidates',
}


def failure(code, message, status, retry_after=None):
    headers = {'Retry-After': str(retry_after)} if retry_after else None
    return JSONResponse({'error': {'code': code, 'message': message}}, status, headers=headers)


async def read_chat(request):
    raw = bytearray()
    async for chunk in request.stream():
        if len(raw) + len(chunk) > MAX_BODY_BYTES:
            raise OverflowError('Request too large')
        raw.extend(chunk)
    body = json.loads(raw)
    if not isinstance(body, dict):
        raise ValueError('Expected object')
    session = body.get('session')
    message = body.get('message')
    context = body.get('context', {})
    history = body.get('history', [])
    if (not isinstance(session, str)
            or not re.fullmatch(r'(?:not-provider|[A-Za-z0-9_-]{16,96})', session)
            or not isinstance(message, str) or not message.strip() or len(message) > 1500
            or not isinstance(context, dict) or not isinstance(history, list)):
        raise ValueError('Invalid chat input')
    context = {k: v for k, v in context.items() if k in CONTEXT_KEYS}
    if len(json.dumps(context).encode()) > 16_000:
        raise ValueError('Context too large')
    normalized = []
    for item in history[-4:]:
        if not isinstance(item, dict) or item.get('role') not in ['user', 'assistant'] or not isinstance(item.get('text'), str):
            raise ValueError('Invalid history')
        normalized.append({'role': item['role'], 'text': item['text'][:1500]})
    return session, message.strip(), context, normalized


async def run_turn(session, message, context, history):
    actions, tools, text = [], [], []
    toolset, runner = None, None
    try:
        async def propose_app_action(action: str, value: str = '') -> dict:
            """Offer an app action for review. action: search, set_allowance, set_opportunities, set_calorie_goal, set_protein_goal, set_carb_goal, set_fat_goal, log_calories, log_protein, log_carbs, log_fat, log_spending, set_taste, navigate_home, navigate_day, navigate_saved, review_cart, save_winner. value is query, integer, or taste. Does not execute it."""
            allowed = {'search', 'set_allowance', 'set_opportunities', 'set_calorie_goal', 'set_protein_goal', 'set_carb_goal', 'set_fat_goal', 'log_calories', 'log_protein', 'log_carbs', 'log_fat', 'log_spending', 'set_taste', 'navigate_home', 'navigate_day', 'navigate_saved', 'review_cart', 'save_winner'}
            if action not in allowed:
                return {'proposed': False, 'reason': 'Unsupported app action'}
            actions.append({'action': action, 'value': value[:150]})
            return {'proposed': True, 'awaitingUserConfirmation': True}

        executed = 0
        async def tool_budget(tool, args, tool_context):
            nonlocal executed
            executed += 1
            if executed > 4:
                return {'error': 'This turn reached its tool budget. Ask the user to continue in the app.'}
            return None

        toolset = McpToolset(connection_params=StreamableHTTPConnectionParams(
            url='http://127.0.0.1:8890/food',
            headers={'Authorization': 'Bearer hungii-local-demo-' + session}, timeout=10), tool_filter=READ_TOOLS)
        agent = LlmAgent(name='hungii_assistant', model=LiteLlm(
            model='groq/openai/gpt-oss-20b', api_key=os.environ['GROQ_API_KEY'], reasoning_effort='low',
            parallel_tool_calls=False, max_completion_tokens=450, num_retries=0, timeout=30),
            instruction=SYSTEM, tools=[toolset, propose_app_action], before_model_callback=strip_reasoning,
            before_tool_callback=tool_budget, after_tool_callback=compact_mcp,
            generate_content_config=types.GenerateContentConfig(temperature=.3))
        runner = InMemoryRunner(agent=agent, app_name='hungii_demo')
        sid = await runner.session_service.create_session(app_name='hungii_demo', user_id=session)
        prefix = 'Previous conversation: ' + json.dumps(history, ensure_ascii=False) + '\n' if history else ''
        content = types.Content(role='user', parts=[types.Part(text=prefix + 'Current tracker/app context: '
            + json.dumps(context, ensure_ascii=False) + '\nUser: ' + message)])
        async for event in runner.run_async(user_id=session, session_id=sid.id, new_message=content,
                run_config=RunConfig(max_llm_calls=5)):
            if event.content:
                for part in event.content.parts or []:
                    if part.function_call:
                        tools.append(part.function_call.name)
                    if event.is_final_response() and part.text and not part.thought:
                        text.append(part.text)
        if not text:
            raise RuntimeError('No final reply')
        reply = '\n'.join(text)
        if actions:
            action = actions[0]
            reply = ('I’ve prepared the following change for review: ' + action['action'].replace('_', ' ')
                + ((' → ' + action['value']) if action['value'] else '') + '. Apply it using the review button below.')
        return {'reply': reply, 'actions': actions[:3], 'tools': tools,
            'provider': 'Groq Free · GPT OSS 20B', 'sdk': 'Google ADK', 'inference': 'cloud'}
    finally:
        # Close in the same task that opened MCP's async context. Runner closes
        # its toolsets too; cover partial construction before a runner exists.
        try:
            async with asyncio.timeout(5):
                if runner is not None:
                    await runner.close()
                elif toolset is not None:
                    await toolset.close()
        except Exception as error:
            logging.getLogger('hungii').error('Agent cleanup failed: %s', type(error).__name__)


@app.post('/chat')
async def chat(request: Request):
    try:
        session, message, context, history = await read_chat(request)
    except OverflowError:
        return failure('HUNGII_BAD_INPUT', 'This request is too large.', 413)
    except (ValueError, TypeError, UnicodeDecodeError, RecursionError):
        return failure('HUNGII_BAD_INPUT', 'Enter a valid message and tracker context.', 400)
    if not os.environ.get('GROQ_API_KEY'):
        return failure('HUNGII_AGENT_SETUP', 'Configure the free Groq key on the local server. Manual discovery and mock checkout are ready.', 503)
    # Reject overlapping turns immediately; waiting behind a 60-second turn
    # would outlive the Android request and spend quota after it timed out.
    if LOCK.locked():
        return failure('HUNGII_AGENT_QUOTA', 'Free inference is busy. Wait a minute or use manual filters.', 429, 60)
    async with LOCK:
        now = time.monotonic()
        while REQUESTS and now - REQUESTS[0] > 60:
            REQUESTS.popleft()
        if len(REQUESTS) >= 4:
            return failure('HUNGII_AGENT_QUOTA', 'Free inference is busy. Wait a minute or use manual filters.', 429, 60)
        REQUESTS.append(now)
        try:
            return await asyncio.wait_for(run_turn(session, message, context, history), timeout=55)
        except Exception as error:
            logging.getLogger('hungii').error('Agent turn failed: %s', type(error).__name__)
            code = getattr(error, 'status_code', None)
            return failure('HUNGII_AGENT_QUOTA' if code == 429 else 'HUNGII_AGENT_UNAVAILABLE',
                'Free inference is temporarily unavailable. Use manual meal filters and try the assistant again shortly.',
                429 if code == 429 else 503, 60 if code == 429 else None)


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='127.0.0.1', port=8789, access_log=False, log_level='warning')
