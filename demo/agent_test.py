"""Adapter regressions; no inference, key or network request is used."""
import unittest
from types import SimpleNamespace
from google.genai import types
from demo.agent import strip_reasoning,compact_mcp
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
