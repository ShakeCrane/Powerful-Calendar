/* Exercises the packaged Clear Web runtime against a deterministic read-only Host fixture. */
const {spawn} = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {pathToFileURL} = require("node:url");

const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.POWERCAL_CHAT_HISTORY_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/native095-ai-history-paging-20261106"));
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
  fs.mkdirSync(reportDir, {recursive:true});
  const profile = fs.mkdtempSync(path.join(reportDir, "chrome-profile-"));
  const browser = spawn(chrome, ["--headless=new", "--disable-gpu", "--no-sandbox",
    "--allow-file-access-from-files", "--disable-background-networking", "--no-first-run",
    "--no-default-browser-check", "--remote-allow-origins=*", "--remote-debugging-port=0",
    "--user-data-dir=" + profile, "about:blank"], {stdio:"ignore"});
  let socket;
  try {
    const portFile = path.join(profile, "DevToolsActivePort");
    for (let i=0; i<200 && !fs.existsSync(portFile); i++) {
      if (browser.exitCode !== null) throw Error("Chrome exited " + browser.exitCode);
      await delay(100);
    }
    if (!fs.existsSync(portFile)) throw Error("Chrome DevTools startup timed out");
    const port = fs.readFileSync(portFile, "utf8").split(/\r?\n/)[0];
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`,
      {method:"PUT"})).json();
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {socket.onopen=resolve; socket.onerror=reject;});
    let nextId=0;
    const pending=new Map();
    socket.onmessage=event=>{
      const message=JSON.parse(event.data);
      if(!message.id||!pending.has(message.id))return;
      const {resolve,reject}=pending.get(message.id);pending.delete(message.id);
      message.error?reject(Error(message.error.message)):resolve(message.result);
    };
    const call=(method,params={})=>new Promise((resolve,reject)=>{
      const id=++nextId;pending.set(id,{resolve,reject});
      socket.send(JSON.stringify({id,method,params}));
    });
    const evaluate=async expression=>{
      const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
      if(result.exceptionDetails)throw Error(result.exceptionDetails.text||"page evaluation failed");
      return result.result.value;
    };
    await call("Runtime.enable");
    await call("Page.enable");
    await call("Emulation.setDeviceMetricsOverride",{
      width:412,height:915,deviceScaleFactor:1,mobile:true
    });
    await call("Page.navigate",{url:pathToFileURL(path.join(root,
      "tools/skin-studio/default-clear/source/web/index.html")).href});
    let ready=false;
    for(let i=0;i<200&&!ready;i++){
      ready=await evaluate("document.readyState==='complete'&&!!document.querySelector('#chatMessages')");
      if(!ready)await delay(50);
    }
    if(!ready)throw Error("Clear Web runtime did not load");

    const fixture = Array.from({length:82},(_,index)=>{
      const id="history-"+String(index).padStart(2,"0");
      let content="HISTORY_"+String(index).padStart(2,"0")+"_SENTINEL";
      if(index===81){
        const suffix="LONG_HISTORY_END_SENTINEL";
        content="LONG_HISTORY_START_SENTINEL"+"x".repeat(1945-"LONG_HISTORY_START_SENTINEL".length-suffix.length)+suffix;
      }
      return {messageId:id,role:index%2?"assistant":"user",content,proposalAvailable:false};
    });
    const fixtureJson=JSON.stringify(fixture);
    const boot=await evaluate(`(()=>{
      const all=${fixtureJson};
      const page=(start,end,revision=7,changeKind="UNCHANGED")=>({
        bridgeVersion:1,page:"AI",hostDataReady:true,hostNotice:null,pendingCount:0,memoCount:0,
        aiLocked:false,chatBusy:false,chatHistoryRevision:revision,chatHistoryChangeKind:changeKind,
        chatHistoryActionsAvailable:true,chatPrefillText:"",chatPrefillRevision:0,
        chatAttachmentAvailable:false,chatAttachmentBusy:false,chatHistoryHasOlder:start>0,
        chatHistoryPageStart:start,chatHistoryPageEnd:end,chatMessages:all.slice(start,end),
        aiBatch:{phase:"IDLE",canCancel:false,canRelease:false},aiProposalReview:null,
        reducedMotion:false,optionGroups:[],timeline:{anchorMillis:Date.now(),hours:24,zoneId:"UTC"}
      });
      const channel=new MessageChannel();let pageStart=42,pageEnd=82,revision=7;
      let changeKind="UNCHANGED",delayNextRequest=false,delayedRequest=null;
      const publish=()=>channel.port1.postMessage(JSON.stringify({version:1,type:"state",
        payload:page(pageStart,pageEnd,revision,changeKind)}));
      window.__powercalChatFixture=all;window.__powercalChatActions=[];
      window.__powercalDelayNextHistoryRequest=()=>{delayNextRequest=true};
      window.__powercalAppendWhileHistoryRequestPending=()=>{
        all.push({messageId:"history-82",role:"user",content:"APPENDED_82",proposalAvailable:false},
          {messageId:"history-83",role:"assistant",content:"APPENDED_83",proposalAvailable:false});
        revision=8;changeKind="APPEND";pageEnd=all.length;pageStart=Math.max(0,pageEnd-40);publish();
      };
      window.__powercalServerState=()=>({revision,pageStart,pageEnd,delayedRequest});
      window.__powercalResolveDelayedHistoryRequest=()=>{
        const request=delayedRequest;if(!request)throw Error("No delayed request");delayedRequest=null;
        if(request.historyRevision!==revision||request.endExclusive<1||request.endExclusive>all.length){
          publish();return {accepted:false,revision,pageStart,pageEnd};
        }
        pageEnd=request.endExclusive;pageStart=Math.max(0,pageEnd-40);changeKind="UNCHANGED";publish();
        return {accepted:true,revision,pageStart,pageEnd};
      };
      channel.port1.onmessage=event=>{
        let message;try{message=JSON.parse(event.data)}catch{return}
        if(message.type==="presentation")return;
        if(message.type!=="action")return;
        window.__powercalChatActions.push(message.payload.name);
        if(message.payload.name!=="loadOlderChatHistory")return;
        if(delayNextRequest){delayedRequest=message.payload;delayNextRequest=false;return}
        const request=message.payload;
        if(request.historyRevision!==revision||request.endExclusive<1||request.endExclusive>all.length){
          publish();return;
        }
        pageEnd=request.endExclusive;pageStart=Math.max(0,pageEnd-40);changeKind="UNCHANGED";publish();
      };
      window.__powercalChatSendState=payload=>channel.port1.postMessage(
        JSON.stringify({version:1,type:"state",payload}));
      window.postMessage("powercal-port","*",[channel.port2]);
      channel.port1.postMessage(JSON.stringify({version:1,type:"state",payload:page(pageStart,pageEnd)}));
      return true;
    })()`);
    if(boot!==true)throw Error("Could not install deterministic Host fixture");
    const waitFor=async(expression,label)=>{
      for(let i=0;i<200;i++){
        if(await evaluate(expression))return;
        await delay(25);
      }
      throw Error("Timed out waiting for " + label);
    };
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===40",
      "latest 40-message page");
    const initial=await evaluate(`(()=>({
      first:document.querySelector('#chatMessages .chat-bubble')?.dataset.messageId,
      oldVisible:document.body.innerText.includes('HISTORY_00_SENTINEL'),
      tail:document.querySelector('[data-message-id="history-81"] .chat-message-content')?.textContent
    }))()`);
    if(initial.first!=="history-42"||initial.oldVisible||initial.tail?.length!==1945||
      !initial.tail.endsWith("LONG_HISTORY_END_SENTINEL"))
      throw Error("Initial page did not preserve the expected tail and full long message: " + JSON.stringify(initial));

    const beforeAnchor=await evaluate(`(()=>{
      const list=document.querySelector('#chatMessages');list.scrollTop=600;
      const root=list.getBoundingClientRect();
      const item=[...list.querySelectorAll('.chat-bubble')].find(b=>b.getBoundingClientRect().bottom>root.top);
      return {id:item?.dataset.messageId,top:item?.getBoundingClientRect().top-root.top};
    })()`);
    await evaluate("document.querySelector('.chat-load-older').click()");
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===80",
      "first older page");
    const afterAnchor=await evaluate(`(()=>{
      const list=document.querySelector('#chatMessages'),item=list.querySelector('[data-message-id="${beforeAnchor.id}"]');
      const root=list.getBoundingClientRect();
      return {top:item?.getBoundingClientRect().top-root.top,first:list.querySelector('.chat-bubble')?.dataset.messageId,
        button:!!document.querySelector('.chat-load-older')};
    })()`);
    if(afterAnchor.first!=="history-02"||Math.abs(afterAnchor.top-beforeAnchor.top)>2||!afterAnchor.button)
      throw Error("First page prepend lost sequence or scroll anchor: " + JSON.stringify({beforeAnchor,afterAnchor}));

    await evaluate("window.__powercalDelayNextHistoryRequest()");
    await evaluate("document.querySelector('.chat-load-older').click()");
    await waitFor("window.__powercalServerState().delayedRequest!==null","delayed older-page request");
    const delayedRequest=await evaluate("window.__powercalServerState().delayedRequest");
    if(delayedRequest.historyRevision!==7||delayedRequest.endExclusive!==2)
      throw Error("Older-page request did not capture the visible revision/cursor: "+JSON.stringify(delayedRequest));
    await evaluate("window.__powercalAppendWhileHistoryRequestPending()");
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===82&&document.querySelector('#chatMessages .chat-bubble:last-of-type')?.dataset.messageId==='history-83'",
      "chat append while older-page request is pending");
    const staleResult=await evaluate("window.__powercalResolveDelayedHistoryRequest()");
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===82&&document.querySelector('#chatMessages .chat-bubble:last-of-type')?.dataset.messageId==='history-83'",
      "stale request returning the authoritative newer page");
    const afterStale=await evaluate(`(()=>{
      const ids=[...document.querySelectorAll('#chatMessages .chat-bubble')].map(item=>item.dataset.messageId);
      return {count:ids.length,unique:new Set(ids).size,first:ids[0],last:ids.at(-1),button:!!document.querySelector('.chat-load-older'),
        buttonDisabled:document.querySelector('.chat-load-older')?.disabled};
    })()`);
    if(staleResult.accepted||afterStale.count!==82||afterStale.unique!==82||afterStale.first!=="history-02"||
      afterStale.last!=="history-83"||!afterStale.button||afterStale.buttonDisabled)
      throw Error("Stale page request changed or hid newer cached messages: "+JSON.stringify({staleResult,afterStale}));
    await evaluate("document.querySelector('.chat-load-older').click()");
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===84",
      "remaining older page after stale request");
    const loaded=await evaluate(`(()=>{
      const bubbles=[...document.querySelectorAll('#chatMessages .chat-bubble')];
      const ids=bubbles.map(item=>item.dataset.messageId);
      return {count:ids.length,unique:new Set(ids).size,first:ids[0],last:ids.at(-1),
        ordered:ids.every((id,index)=>id==='history-'+String(index).padStart(2,'0')),
        button:!!document.querySelector('.chat-load-older'),
        longText:document.querySelector('[data-message-id="history-81"] .chat-message-content')?.textContent};
    })()`);
    if(loaded.count!==84||loaded.unique!==84||loaded.first!=="history-00"||loaded.last!=="history-83"||
      !loaded.ordered||loaded.button||loaded.longText?.length!==1945||
      !loaded.longText.endsWith("LONG_HISTORY_END_SENTINEL"))
      throw Error("Paged history was not complete, ordered, and lossless: " + JSON.stringify(loaded));
    await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:false}).then(result=>
      fs.writeFileSync(path.join(reportDir,"clear-chat-history-paged.png"),Buffer.from(result.data,"base64")));

    await evaluate(`(()=>{
      const list=document.querySelector('#chatMessages');list.scrollTop=list.scrollHeight;
      const all=window.__powercalChatFixture;
      all.push({messageId:'history-84',role:'user',content:'APPENDED_84',proposalAvailable:false},
        {messageId:'history-85',role:'assistant',content:'APPENDED_85',proposalAvailable:false});
      window.__powercalChatSendState({bridgeVersion:1,page:'AI',hostDataReady:true,hostNotice:null,
        pendingCount:0,memoCount:0,aiLocked:false,chatBusy:false,chatHistoryRevision:9,
        chatHistoryChangeKind:'APPEND',chatHistoryActionsAvailable:true,chatPrefillText:'',chatPrefillRevision:0,
        chatAttachmentAvailable:false,chatAttachmentBusy:false,chatHistoryHasOlder:true,
        chatHistoryPageStart:46,chatHistoryPageEnd:86,chatMessages:all.slice(46,86),
        aiBatch:{phase:'IDLE',canCancel:false,canRelease:false},aiProposalReview:null,
        reducedMotion:false,optionGroups:[],timeline:{anchorMillis:Date.now(),hours:24,zoneId:'UTC'}});
    })()`);
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===86",
      "append while at bottom");
    const followed=await evaluate(`(()=>{const e=document.querySelector('#chatMessages');
      return e.scrollHeight-e.clientHeight-e.scrollTop<=2})()`);
    if(!followed)throw Error("A new message did not follow the reader who was at the bottom");

    const noFollow=await evaluate(`(()=>{
      const list=document.querySelector('#chatMessages');list.scrollTop=400;
      const root=list.getBoundingClientRect();
      const item=[...list.querySelectorAll('.chat-bubble')].find(b=>b.getBoundingClientRect().bottom>root.top);
      return {id:item?.dataset.messageId,top:item?.getBoundingClientRect().top-root.top};
    })()`);
    await evaluate(`(()=>{
      const all=window.__powercalChatFixture;
      all.push({messageId:'history-86',role:'user',content:'APPENDED_86',proposalAvailable:false},
        {messageId:'history-87',role:'assistant',content:'APPENDED_87',proposalAvailable:false});
      window.__powercalChatSendState({bridgeVersion:1,page:'AI',hostDataReady:true,hostNotice:null,
        pendingCount:0,memoCount:0,aiLocked:false,chatBusy:false,chatHistoryRevision:10,
        chatHistoryChangeKind:'APPEND',chatHistoryActionsAvailable:true,chatPrefillText:'',chatPrefillRevision:0,
        chatAttachmentAvailable:false,chatAttachmentBusy:false,chatHistoryHasOlder:true,
        chatHistoryPageStart:48,chatHistoryPageEnd:88,chatMessages:all.slice(48,88),
        aiBatch:{phase:'IDLE',canCancel:false,canRelease:false},aiProposalReview:null,
        reducedMotion:false,optionGroups:[],timeline:{anchorMillis:Date.now(),hours:24,zoneId:'UTC'}});
    })()`);
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===88",
      "append while reading history");
    const noFollowAfter=await evaluate(`(()=>{
      const list=document.querySelector('#chatMessages'),item=list.querySelector('[data-message-id="${noFollow.id}"]');
      const root=list.getBoundingClientRect();
      return {top:item?.getBoundingClientRect().top-root.top,bottomGap:list.scrollHeight-list.clientHeight-list.scrollTop};
    })()`);
    if(Math.abs(noFollowAfter.top-noFollow.top)>2||noFollowAfter.bottomGap<=2)
      throw Error("Append while reading older history moved the viewport: " + JSON.stringify({noFollow,noFollowAfter}));

    await evaluate(`window.__powercalChatSendState({bridgeVersion:1,page:'AI',hostDataReady:true,hostNotice:null,
      pendingCount:0,memoCount:0,aiLocked:false,chatBusy:false,chatHistoryRevision:11,
      chatHistoryChangeKind:'RESET',chatHistoryActionsAvailable:false,chatPrefillText:'',chatPrefillRevision:0,
      chatAttachmentAvailable:false,chatAttachmentBusy:false,chatHistoryHasOlder:false,
      chatHistoryPageStart:0,chatHistoryPageEnd:0,chatMessages:[],aiBatch:{phase:'IDLE'},
      aiProposalReview:null,reducedMotion:false,optionGroups:[],timeline:{anchorMillis:Date.now(),hours:24,zoneId:'UTC'}})`);
    await waitFor("document.querySelectorAll('#chatMessages .chat-bubble').length===0",
      "history reset invalidating old cached messages");
    const actions=await evaluate("window.__powercalChatActions");
    if(actions.filter(name=>name==="loadOlderChatHistory").length!==3)
      throw Error("Unexpected Host page action count: " + JSON.stringify(actions));
    fs.writeFileSync(path.join(reportDir,"browser-e2e-result.json"),JSON.stringify({
      result:"PASS",viewport:"412x915",initialMessages:40,loadedMessages:84,staleRequestRejected:true,
      staleRequestReturnedAuthoritativePage:true,longMessageCharacters:loaded.longText.length,anchorDeltaPx:Math.abs(afterAnchor.top-beforeAnchor.top),
      appendFollowsBottom:followed,appendPreservesReadingAnchor:Math.abs(noFollowAfter.top-noFollow.top)<=2,
      resetInvalidatesHistory:true,hostActions:actions
    },null,2)+"\n");
    console.log(JSON.stringify({result:"PASS",loadedMessages:84,staleRequestRejected:true,
      staleRequestReturnedAuthoritativePage:true,anchorDeltaPx:
      Math.abs(afterAnchor.top-beforeAnchor.top),longMessageCharacters:loaded.longText.length,
      appendFollowsBottom:followed,appendPreservesReadingAnchor:true,resetInvalidatesHistory:true}));
  } finally {
    socket?.close();
    if(browser.exitCode===null){
      browser.kill();
      await new Promise(resolve=>{const timer=setTimeout(resolve,3000);browser.once("exit",()=>{clearTimeout(timer);resolve()})});
    }
  }
}

main().catch(error=>{console.error(error.stack||error);process.exitCode=1});
