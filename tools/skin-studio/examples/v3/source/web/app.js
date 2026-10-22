(() => { 'use strict';
  let bridge = {postMessage:m=>console.log('mock-host',m)};
  window.addEventListener('message', event => { if(event.data==='powercal-port' && event.ports[0]) { bridge=event.ports[0]; bridge.onmessage=e=>window.PowerCalSkin.receive(e.data); send('ready',{minimumBridgeVersion:1}); } });
  const send = (type,payload={}) => bridge.postMessage(JSON.stringify({version:1,type,payload}));
  document.addEventListener('click', e => { const action=e.target.closest('[data-action]')?.dataset.action; if(action) send('action',{name:action}); });
  window.PowerCalSkin = { receive(message) { const data=typeof message==='string'?JSON.parse(message):message; if(data.type==='state'){ document.getElementById('events').textContent=(data.payload.events||[]).map(x=>x.title).join('\n') || 'No events'; } } };
})();
