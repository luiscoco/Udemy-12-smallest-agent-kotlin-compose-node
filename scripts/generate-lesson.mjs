import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const kotlin='android/app/src/main/java/com/luiscoco/smallestagent/compose/';
const files={state:kotlin+'LabViewModel.kt',client:kotlin+'AgentApi.kt',render:kotlin+'LessonComponents.kt',server:'backend/src/server.ts',handlers:'backend/src/handlers.ts',service:'backend/src/agent-service.ts'};
const read=path=>readFileSync(resolve(root,path),'utf8');
const svg=read('scripts/workflow-template.svg'),puml=read('scripts/workflow-template.puml');
if(/<style\b|\bclass=/.test(svg))throw Error('Use explicit SVG fill/stroke/font attributes.');
const labels=[...svg.matchAll(/<text[^>]*>(\d+)\. ([^<]+)<\/text>/g)].map(m=>[Number(m[1]),m[2].replaceAll('&amp;','&').replaceAll('&gt;','>')]);
const uml=puml.split('\n').filter(line=>/\s(?:->|-->)\s/.test(line)).map(line=>line.split(':').slice(1).join(':').trim());
if(labels.length!==16||labels.some(([n,title],i)=>n!==i+1||title!==uml[i])||uml.length!==16)throw Error('SVG/PlantUML must share sixteen numbered labels.');
const definitions=[
 ['state','val trimmed = mutable.value.prompt.trim()',10,'Trim native input and lock before asynchronous work.','The activity-scoped ViewModel retains prompt, result and timeline across lesson changes and configuration changes. The Node API independently validates input.'],
 ['client','suspend fun stream(prompt:',14,'POST JSON through a real native HTTP client.','OkHttp performs the request off the main thread. The configured public base URL belongs to Android; the Claude API key stays in backend/.env.'],
 ['handlers','const prompt = validatePrompt(body)',5,'Validate untrusted JSON on the Node server.','The HTTP adapter bounds actual body bytes and checks configured Origins. The handler verifies media type, JSON syntax and a trimmed 1–4000 character prompt.'],
 ['server','const response = await',8,'Delegate a real Node route to the SDK service.','The Node process can run the CLI-backed TypeScript Agent SDK. Request disconnects abort owned SDK work and response writes respect backpressure.'],
 ['service','const conversation = queryFn(',7,'Invoke the actual TypeScript Claude Agent SDK.','The SDK owns the model transport and CLI runtime. This source is the application boundary that initiates inference, rather than an invented provider HTTP call.'],
 ['service','for await (const message of conversation)',7,'Observe SDK messages from the model exchange.','The same async iterator powers simple and stream modes. Tools, settings, skills, MCP, plugins and persisted sessions are explicitly disabled.'],
 ['service','yield { type, summary, data }',4,'Project only safe SDK presentation fields.','Text, subtype/model and final metrics are allowlisted. Thinking, paths, tool arguments, session IDs and raw configuration are excluded; exact key occurrences are redacted.'],
 ['handlers','const sendEvent =',8,'Encode UTF-8 SSE messages.','Heartbeat comments and a three-minute deadline bound the stream. Request cancellation and reader cancellation clean up timers and abort execution.'],
 ['state','val rows = if (old.messages.size < 500)',6,'Update native StateFlow with timestamped events.','The display cap does not prevent final result processing. LazyColumn renders timeline entries without mounting all rows at once.'],
 ['service','function resultData(',12,'Normalize final answer and metrics.','Non-success SDK subtypes remain errors even when is_error=false. Cost may be null, which the native screen labels unavailable.'],
 ['client','val message = AgentMessage.fromJson(json)',6,'Validate and dispatch the final ResultMessage.','Kotlin checks public message/result contracts before updating the UI. ResultMessage is a safe projection label rather than a raw SDK class name.'],
 ['render','@Composable fun MarkdownAnswer',14,'Render model content as native text and colored code.','Compose never executes model HTML. Supported headings, bold, standalone HTTP(S) links and fenced code become native components; unsupported markup remains text.'],
 ['client','if (!completed || finalResult == null)',3,'Require final result and done before resolving.','Okio and the SSE reader handle split UTF-8, CRLF, comments and multiline data. A done event never overrides the final error flag.'],
 ['handlers',"} catch { sendEvent('error'",4,'Emit a safe SSE failure without done.','Simple-mode failures return 502. Raw provider errors and submitted values do not enter public error messages.'],
 ['state','} catch (error: Exception)',8,'Show a safe native error and snackbar.','Cancellation aborts OkHttp, which disconnects the server request. Finally clears the clock and unlocks controls.'],
 ['handlers',"if (!config.apiKeyConfigured) { sendEvent",4,'Reject missing credentials before calling the SDK.','Simple mode returns 503. Streaming sends error without done. Invalid input and missing keys never spend model credits.'],
];
const steps=definitions.map(([key,marker,count,text,explanation],i)=>{
 const source=files[key],content=read(source),lines=content.split(/\r?\n/);const positions=[];let normalized='';
 for(let n=0;n<content.length;n++)if(!/[\s"'()]/.test(content[n])){normalized+=content[n];positions.push(n);}
 const found=normalized.indexOf(marker.replace(/[\s"'()]/g,''));if(found<0)throw Error(`Missing source marker ${source}: ${marker}`);
 const start=content.slice(0,positions[found]).split(/\r?\n/).length-1,selected=lines.slice(start,start+count),indent=Math.min(...selected.filter(l=>l.trim()).map(l=>l.search(/\S/)));
 return {number:i+1,title:labels[i][1],source,language:source.endsWith('.kt')?'kotlin':'typescript',text,explanation,code:selected.map(l=>l.slice(indent)).join('\n')};
});
for(const folder of ['android/app/src/main/assets','public']){mkdirSync(resolve(root,folder),{recursive:true});writeFileSync(resolve(root,folder,'workflow.json'),JSON.stringify(steps,null,2)+'\n');writeFileSync(resolve(root,folder,'workflow.svg'),svg);writeFileSync(resolve(root,folder,'workflow.puml'),puml);}
console.log('Generated sixteen native Kotlin/TypeScript source cards and matching UML assets.');
