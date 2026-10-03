"""Adapter regressions; no inference, key or network request is used."""
import unittest
import asyncio
import json
import os
from unittest.mock import patch
from types import SimpleNamespace
from starlette.requests import Request
from google.genai import types
from demo.agent import strip_reasoning,compact_mcp,chat,LOCK,REQUESTS

def request_for(body):
 raw=body if isinstance(body,bytes) else json.dumps(body).encode()
 async def receive():return {'type':'http.request','body':raw,'more_body':False}
 return Request({'type':'http','method':'POST','path':'/chat','headers':[]},receive)

class ChatBoundaryTest(unittest.IsolatedAsyncioTestCase):
 async def asyncSetUp(self):
  REQUESTS.clear()
  self.key=patch.dict(os.environ,{'GROQ_API_KEY':'synthetic-test-key'})
  self.key.start()
  self.no_model=patch('demo.agent.McpToolset',side_effect=AssertionError('Invalid or busy input reached model setup'))
  self.no_model.start()
 async def asyncTearDown(self):self.no_model.stop();self.key.stop();REQUESTS.clear()
 async def test_invalid_payloads_are_rejected_before_model_setup(self):
  for body in [b'{',None,[],{'session':42,'message':'Hi'},{'session':'not-provider','message':'Hi','context':[]},{'session':'not-provider','message':'Hi','history':None}]:
   with self.subTest(body=body):
    response=await chat(request_for(body))
    self.assertEqual(response.status_code,400)
    self.assertEqual(json.loads(response.body)['error']['code'],'HUNGII_BAD_INPUT')
 async def test_oversized_body_is_rejected_by_actual_bytes(self):
  response=await chat(request_for({'session':'not-provider','message':'Hi','padding':'é'*26000}))
  self.assertEqual(response.status_code,413)
 async def test_busy_turn_is_rejected_without_waiting(self):
  await LOCK.acquire()
  try:
   response=await asyncio.wait_for(chat(request_for({'session':'not-provider','message':'Hi'})),timeout=.1)
   self.assertEqual(response.status_code,429)
   self.assertEqual(response.headers['Retry-After'],'60')
  finally:LOCK.release()
 async def test_model_setup_failure_returns_recoverable_error(self):
  with patch('demo.agent.McpToolset',side_effect=RuntimeError('Synthetic setup failure')):
   response=await chat(request_for({'session':'not-provider','message':'Hi'}))
  self.assertEqual(response.status_code,503)
  self.assertEqual(json.loads(response.body)['error']['code'],'HUNGII_AGENT_UNAVAILABLE')
  self.assertNotIn(b'Synthetic setup failure',response.body)
  self.assertFalse(LOCK.locked())
class AdapterTest(unittest.IsolatedAsyncioTestCase):
 async def test_reasoning_is_not_replayed_but_tool_calls_survive(self):
  call=types.Part(function_call=types.FunctionCall(name='get_addresses',args={}))
  answer=types.Part(text='Public reply')
  request=SimpleNamespace(contents=[types.Content(role='model',parts=[types.Part(text='Internal reasoning',thought=True),call,answer])])
  await strip_reasoning(None,request)
  self.assertEqual(len(request.contents[0].parts),2)
  self.assertEqual(request.contents[0].parts[0].function_call.name,'get_addresses')
  self.assertEqual(request.contents[0].parts[1].text,'Public reply')
 async def test_mcp_envelope_uses_one_compact_representation(self):
  source={'success':True,'data':{'items':[{'menu_item_id':str(i),'price':125,'imageUrl':'synthetic','variantsV2':[{}]} for i in range(20)]}}
  value=await compact_mcp(SimpleNamespace(name='search_menu'),{},None,{'content':[{'type':'text','text':'duplicate payload'}],'structuredContent':source})
  self.assertEqual(len(value['data']['items']),6)
  self.assertEqual(value['data']['items'][0]['menu_item_id'],'0')
  self.assertEqual(value['data']['items'][0]['price'],125)
  self.assertNotIn('imageUrl',value['data']['items'][0])
  self.assertNotIn('content',value)
 async def test_own_proposals_are_not_compacted(self):
  self.assertIsNone(await compact_mcp(SimpleNamespace(name='propose_app_action'),{},None,{'proposed':True,'awaitingUserConfirmation':True}))
if __name__=='__main__':unittest.main()
