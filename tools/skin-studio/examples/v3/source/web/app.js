(() => { 'use strict';
  let bridge = {postMessage:m=>console.log('mock-host',m)};
  window.addEventListener('message', event => { if(event.data==='powercal-port' && event.ports[0]) { bridge=event.ports[0]; bridge.onmessage=e=>window.PowerCalSkin.receive(e.data); send('ready',{minimumBridgeVersion:1}); } });
  const send = (type,payload={}) => bridge.postMessage(JSON.stringify({version:1,type,payload}));
  document.addEventListener('click', e => {
    const target=e.target.closest('[data-action]'); if(!target)return;
    const name=target.dataset.action;
    if(name==='openEvent') send('action',{name,eventSeq:Number(target.dataset.eventSeq),expectedRevision:Number(target.dataset.revision)});
    else send('action',{name});
  });
  window.PowerCalSkin = { receive(message) {
    const data=typeof message==='string'?JSON.parse(message):message;
    if(data.version!==1 || data.type!=='state')return;
    const root=document.getElementById('events'); root.replaceChildren();
    for(const item of data.payload.events||[]){
      const button=document.createElement('button'); button.dataset.action='openEvent';
      button.dataset.eventSeq=String(item.eventSeq); button.dataset.revision=String(item.revision);
      button.textContent=item.title; root.appendChild(button);
    }
    if(!root.children.length)root.textContent='No events';
  } };
})();
