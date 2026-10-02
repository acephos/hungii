"""Google ADK + Groq Free. No local model, paid fallback or payment-capable agent."""
import asyncio,json,os,re,time,logging
from pathlib import Path
from collections import deque
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from fastapi import FastAPI,Request
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
REQUESTS=deque();LOCK=asyncio.Lock()
SYSTEM='''You are Hungii, a concise meal-planning companion. Speak naturally, one short paragraph, no lectures. Help with cravings, daily calorie/macros/rupee allowance, meals remaining, saved meals, app navigation, cart and checkout. User context is authoritative. All provider data is synthetic; never claim real Swiggy availability, nutrition or payment. For meal requests use get_addresses first, then search_restaurants with that returned addressId and a specific dish/craving query. Do not guess IDs. When needed search_menu scoped to a returned restaurant. Budget and nutrition ranges in context are estimates. The app handles ranking. Changes are ALWAYS proposals, never completed changes. Even after propose_app_action, say "Review the proposed change below" and never "updated", "now", or "set" as if already applied. Propose one app action when helpful; the app asks confirmation before changing totals, saving, creating a cart or starting checkout. Never claim food eaten just because ordered. Never claim order placed: you cannot place or pay. Limit tool use to 3 calls, avoid giant enumerations, ask one question if necessary.''' 
@asynccontextmanager
async def lifespan(app):
 yield

async def strip_reasoning(callback_context, llm_request):
 # Groq rejects reasoning_content on assistant history. Preserve tool calls and
 # answers while dropping internal reasoning before ADK serializes the request.
 for content in llm_request.contents:
  if content.parts: content.parts=[p for p in content.parts if not p.thought]
 return None
async def compact_mcp(tool,args,tool_context,tool_response):
 # Keep agent-visible provider facts compact enough for the free token budget.
 if tool.name=='propose_app_action':return None
 value=tool_response
 if hasattr(value,'model_dump'):value=value.model_dump(exclude_none=True)
 if isinstance(value,dict):
  if value.get('structuredContent'):value=value['structuredContent']
  elif value.get('content'):
   try:value=json.loads(next(c['text'] for c in value['content'] if c.get('type')=='text'))
   except (StopIteration,KeyError,ValueError):pass
 def trim(v):
  if isinstance(v,list):return [trim(x) for x in v[:6]]
  if isinstance(v,dict):return {k:trim(x) for k,x in v.items() if k not in ['imageId','imageUrl','images','variantsV2','addons']}
  return v
 return trim(value)
app=FastAPI(lifespan=lifespan)
@app.get('/health')
async def health():return {'configured':bool(os.environ.get('GROQ_API_KEY')),'provider':'Groq Free','sdk':'Google ADK','localModel':False}
@app.post('/chat')
async def chat(request:Request):
 if not os.environ.get('GROQ_API_KEY'):return JSONResponse({'error':{'code':'HUNGII_AGENT_SETUP','message':'Configure the free Groq key on the local server. Manual discovery and mock checkout are ready.'}},503)
 body=await request.json();session=body.get('session','');message=str(body.get('message','')).strip()[:1500]
 if not re.fullmatch(r'(?:not-provider|[A-Za-z0-9_-]{16,96})',session) or not message:return JSONResponse({'error':{'code':'HUNGII_BAD_INPUT','message':'Enter a message.'}},400)
 # This local demonstration deliberately admits one cloud turn at a time and
 # caps calls. Free quota exhaustion is surfaced; never switch to paid models.
 async with LOCK:
  now=time.monotonic()
  while REQUESTS and now-REQUESTS[0]>60:REQUESTS.popleft()
  if len(REQUESTS)>=4:return JSONResponse({'error':{'code':'HUNGII_AGENT_QUOTA','message':'Free inference is busy. Wait a minute or use manual filters.'}},429,headers={'Retry-After':'60'})
  REQUESTS.append(now);actions=[];tools=[]
  async def propose_app_action(action:str,value:str='')->dict:
   """Offer an app action for user review. action: search, set_allowance, set_opportunities, set_calorie_goal, set_protein_goal, set_carb_goal, set_fat_goal, log_calories, log_protein, log_carbs, log_fat, log_spending, set_taste, navigate_home, navigate_day, navigate_saved, review_cart, save_winner. value is query, integer, or taste. Does not execute it."""
   allowed={'search','set_allowance','set_opportunities','set_calorie_goal','set_protein_goal','set_carb_goal','set_fat_goal','log_calories','log_protein','log_carbs','log_fat','log_spending','set_taste','navigate_home','navigate_day','navigate_saved','review_cart','save_winner'}
   if action not in allowed:return {'proposed':False,'reason':'Unsupported app action'}
   actions.append({'action':action,'value':value[:150]});return {'proposed':True,'awaitingUserConfirmation':True}
  context=body.get('context',{})
  # Only known user-entered tracker totals and synthetic candidate descriptions.
  context={k:v for k,v in context.items() if k in ['caloriesLeft','proteinLeft','carbsLeft','fatLeft','moneyLeft','allowance','spent','goals','opportunities','query','taste','screen','winner','cart','candidates']}
  executed=0
  async def tool_budget(tool,args,tool_context):
   nonlocal executed
   executed+=1
   if executed>4:return {'error':'This turn reached its tool budget. Ask the user to continue in the app.'}
   return None
  toolset=McpToolset(connection_params=StreamableHTTPConnectionParams(url='http://127.0.0.1:8890/food',headers={'Authorization':'Bearer hungii-local-demo-'+session},timeout=10),tool_filter=READ_TOOLS)
  agent=LlmAgent(name='hungii_assistant',model=LiteLlm(model='groq/openai/gpt-oss-20b',api_key=os.environ['GROQ_API_KEY'],reasoning_effort='low',parallel_tool_calls=False,max_completion_tokens=450,num_retries=0,timeout=30),instruction=SYSTEM,tools=[toolset,propose_app_action],before_model_callback=strip_reasoning,before_tool_callback=tool_budget,after_tool_callback=compact_mcp,generate_content_config=types.GenerateContentConfig(temperature=.3))
  runner=InMemoryRunner(agent=agent,app_name='hungii_demo')
  sid=await runner.session_service.create_session(app_name='hungii_demo',user_id=session)
  # Conversation context remains on this server only for the duration of a turn.
  history=[{'role':h.get('role'),'text':str(h.get('text',''))[:1500]} for h in body.get('history',[])[-4:] if isinstance(h,dict) and h.get('role') in ['user','assistant']];prefix='Previous conversation: '+json.dumps(history,ensure_ascii=False)+'\n' if history else ''
  content=types.Content(role='user',parts=[types.Part(text=prefix+'Current tracker/app context: '+json.dumps(context,ensure_ascii=False)+'\nUser: '+message)])
  text=[]
  async def run():
   async for event in runner.run_async(user_id=session,session_id=sid.id,new_message=content,run_config=RunConfig(max_llm_calls=5)):
    if event.content:
     for part in event.content.parts or []:
      if part.function_call:tools.append(part.function_call.name)
      if event.is_final_response() and part.text and not part.thought:text.append(part.text)
  try:
   await asyncio.wait_for(run(),timeout=60)
   if not text:raise RuntimeError('No final reply')
   return {'reply':('I’ve prepared the following change for review: '+actions[0]['action'].replace('_',' ')+((' → '+actions[0]['value']) if actions[0]['value'] else '')+'. Apply it using the review button below.' if actions else '\n'.join(text)),'actions':actions[:3],'tools':tools,'provider':'Groq Free · GPT OSS 20B','sdk':'Google ADK','inference':'cloud'}
  except Exception as error:
   logging.getLogger('hungii').error('Agent turn failed: %s', type(error).__name__)
   code=getattr(error,'status_code',None)
   return JSONResponse({'error':{'code':'HUNGII_AGENT_QUOTA' if code==429 else 'HUNGII_AGENT_UNAVAILABLE','message':'Free inference is temporarily unavailable. Use manual meal filters and try the assistant again shortly.'}},429 if code==429 else 503)
  finally:
   await toolset.close()
if __name__=='__main__':
 import uvicorn
 uvicorn.run(app,host='127.0.0.1',port=8789,access_log=False,log_level='warning')
