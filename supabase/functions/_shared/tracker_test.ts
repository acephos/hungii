import {trackerState} from './tracker.ts';
import {HungiiError} from './errors.ts';
const preferences={displayName:'Test',taste:'spicy',query:'healthy bowls',highProtein:true,vegOnly:false,budgetOnly:true,fast:false};
const state={day:'2026-10-02',calorieGoal:2200,proteinGoal:140,carbGoal:250,fatGoal:70,allowance:350,spent:80,opportunities:2,intake:{calories:[300,350],protein:[20,25],carbs:[30,40],fat:[8,12]}};
Deno.test('Profile sync round-trips entered preferences and accepts existing tracker-only records',()=>{
  const copy=trackerState({...state,preferences});
  if(copy.preferences.displayName!=='Test'||copy.preferences.taste!=='spicy')throw new Error('Preferences lost');
  copy.preferences.displayName='Changed';if(preferences.displayName!=='Test')throw new Error('Input mutated');
  if(trackerState(state).preferences!==undefined)throw new Error('Legacy profile invented');
});
Deno.test('Cloud profile rejects provider payloads and malformed preferences',()=>{
  for(const bad of [{...preferences,menu:{id:'forbidden'}},{...preferences,vegOnly:'yes'},{...preferences,taste:'invented'},{...preferences,displayName:'a'.repeat(61)},{...preferences,query:''}]) {
    try{trackerState({...state,preferences:bad});throw new Error('Unsafe profile accepted');}
    catch(e){if(!(e instanceof HungiiError))throw e;}
  }
});
