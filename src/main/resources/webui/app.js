'use strict';
const $=id=>document.getElementById(id);
let token=new URLSearchParams(location.hash.slice(1)).get('token');
if(token){sessionStorage.setItem('utility-token',token);history.replaceState(null,'',location.pathname);}
token=token||sessionStorage.getItem('utility-token')||'';
let view={};try{view=JSON.parse(localStorage.getItem('utility-view')||'{}')||{};}catch{}
let state={modules:[]},category=view.category||'All modules',selected=view.selected||null,online=false,busy=false,detailSignature='',editingRevision=null,failedDraft=false,shownModule=null,connNotice=false,noticeTimer=0,catCounts=[];
const saveView=()=>{try{localStorage.setItem('utility-view',JSON.stringify({category,selected}));}catch{}};
const cards=new Map();
const PUA=0xE100;
const puaDec=x=>typeof x==='string'?[...x].map(c=>{const n=c.codePointAt(0);return n>=PUA&&n<PUA+256?String.fromCharCode(n-PUA):c;}).join(''):x;
const puaEnc=x=>[...x].map(c=>{const n=c.charCodeAt(0);return n<=255?String.fromCharCode(PUA+n):c;}).join('');
const deepDec=x=>{if(typeof x==='string')return puaDec(x);if(Array.isArray(x))return x.map(deepDec);if(x&&typeof x==='object'){const o={};for(const k in x)o[k]=deepDec(x[k]);return o;}return x;};
function el(tag,cls,text){const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;}
function notice(message,success=false,autoHide=false){clearTimeout(noticeTimer);connNotice=false;const n=$('notice');n.textContent=message;n.hidden=!message;n.className=success?'success':'';if(message&&autoHide)noticeTimer=setTimeout(()=>{if(n.textContent===message)notice('');},2600);}
function connection(ok){online=ok;$('connection').textContent=ok?'Connected to game':'Disconnected';$('dot').classList.toggle('live',ok);const badge=document.querySelector('.live-badge');if(badge){badge.textContent=ok?'Live':'Offline';badge.classList.toggle('off',!ok);}document.body.dataset.online=String(ok);document.querySelectorAll('[data-control]').forEach(n=>n.disabled=!ok||busy);}
async function api(path,body){const r=await fetch(path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,cache:'no-store',signal:AbortSignal.timeout(4500)});const data=deepDec(await r.json());if(!r.ok)throw Object.assign(new Error(puaDec(data.error||'Request failed')),{status:r.status});return data;}
function selectedModule(){return state.modules.find(m=>m.id===selected);}
async function update(m,setting,value,revision){if(!online||busy)return;
 let confirmed=false;if(!setting&&value&&m.confirm){confirmed=confirm('Enable '+m.name+'? This module performs an irreversible action.');if(!confirmed)return;}
 busy=true;connection(online);notice('Applying to Minecraft…',true);
 try{const wire=setting&&setting.type==='select'?puaEnc(value):setting&&setting.type==='multi'?value.map(v=>puaEnc(v)):value;state=await api('/api/change',{moduleId:m.id,...(setting?{settingId:setting.id}:{}),value:wire,revision:revision??m.revision,confirmed});failedDraft=false;editingRevision=null;detailSignature='';connection(true);notice('Applied to Minecraft.',true,true);}
 catch(e){failedDraft=!!setting;notice(e.message+(setting?' Your edited value is still shown. Select the module again to discard it, or Apply again to retry against the latest state.':''));if(!e.status||e.status>=500)connection(false);try{state=await api('/api/state');if(setting)editingRevision=selectedModule()?.revision;}catch{} }
 finally{busy=false;render();connection(online);}
}
function render(){
 $('counts').textContent=state.modules.filter(m=>m.enabled).length+' enabled / '+state.modules.length+' modules';
 const cats=['All modules',...new Set(state.modules.map(m=>m.category))];
 if(state.modules.length&&!cats.includes(category))category='All modules';
 const catSignature=cats.join('|')+'|'+category;
 if($('categories').dataset.signature!==catSignature){const hadFocus=$('categories').contains(document.activeElement);$('categories').replaceChildren();catCounts=[];for(const c of cats){const b=el('button',c===category?'selected':''),count=el('span','cat-count');b.append(el('span','cat-name',c),count);if(c===category)b.setAttribute('aria-current','true');b.onclick=()=>{category=c;saveView();render();};$('categories').append(b);catCounts.push([c,count]);}$('categories').dataset.signature=catSignature;if(hadFocus)$('categories').querySelector('.selected')?.focus();}
 for(const [c,count] of catCounts){const list=c==='All modules'?state.modules:state.modules.filter(m=>m.category===c),on=list.filter(m=>m.enabled).length;count.textContent=list.length?on+'/'+list.length:'';count.classList.toggle('active',on>0);}
 if(state.modules.length&&selected&&!state.modules.some(m=>m.id===selected))selected=null;
 $('categoryTitle').textContent=category;
 const q=$('search').value.trim().toLowerCase();
 const filtered=state.modules.filter(m=>(category==='All modules'||m.category===category)&&(!$('activeOnly').checked||m.enabled)&&[m.name,m.description,...m.settings.map(s=>s.name)].join(' ').toLowerCase().includes(q));
 const visible=new Set(filtered.map(m=>m.id));for(const [id,c]of cards){c.root.hidden=!visible.has(id);}
 for(const m of filtered){let c=cards.get(m.id);if(!c){const root=el('article','module'),icon=el('span','module-icon',m.name.slice(0,1)),name=el('button','module-name'),title=el('strong','',m.name),subtitle=el('small','',m.description||m.category),toggle=el('button','switch');name.append(title,subtitle);toggle.setAttribute('role','switch');toggle.setAttribute('aria-label','Toggle '+m.name);toggle.dataset.control='';name.onclick=()=>{selected=m.id;saveView();failedDraft=false;editingRevision=null;detailSignature='';render();};toggle.onclick=()=>{const latest=state.modules.find(x=>x.id===m.id);update(latest,null,!latest.enabled);};root.append(icon,name,toggle);$('modules').append(root);c={root,toggle};cards.set(m.id,c);}c.root.hidden=false;c.toggle.hidden=m.toggleable===false;c.root.classList.toggle('chosen',selected===m.id);c.root.classList.toggle('on',m.enabled);c.toggle.setAttribute('aria-checked',String(m.enabled));c.toggle.disabled=!online||busy;}
 $('total').textContent=filtered.length;$('empty').hidden=filtered.length!==0;
 if(!selected&&filtered.length)selected=filtered[0].id;
 renderDetail();
}
function controlled(n,s){n.dataset.control='';n.disabled=!online||busy;return n;}
function renderDetail(){const m=selectedModule();if(!m)return;
 const signature=JSON.stringify(m);
 if(signature===detailSignature)return;
 if(failedDraft||$('inspector').contains(document.activeElement))return;
 detailSignature=signature;editingRevision=null;$('inspector').dataset.fresh=String(shownModule!==m.id);shownModule=m.id;
 const head=el('div','inspector-head');head.append(el('p','eyebrow',m.category.toUpperCase()+' / '+m.settings.length+' SETTINGS'),el('h2','',m.name),el('p','',m.description));
 const settings=el('div','settings');$('settingCount').textContent=m.settings.length+' settings';
 let lastGroup=null;for(const s of m.settings){if(s.group&&s.group!==lastGroup)settings.append(el('h3','setting-group group-heading',s.group));lastGroup=s.group||null;const row=el('div','setting'+(!s.visible?' inactive':'')),label=el('label','setting-label',s.name),controlId='field-'+m.id+'-'+s.id;label.htmlFor=controlId;row.dataset.search=[s.name,s.group||'',s.description||''].join(' ').toLowerCase();row.append(label);
 let getValue=()=>s.value,first;
 const input=(type,value)=>{const n=controlled(el('input'),s);n.type=type;if(type==='checkbox')n.checked=!!value;else n.value=value??'';if(!first){first=n;n.id=controlId;}return n;};
 const send=()=>update(m,s,getValue(),editingRevision??m.revision);
 if(s.type==='boolean'){const n=input('checkbox',s.value);label.append(n);getValue=()=>n.checked;n.onchange=send;}
 else if(s.type==='key'){const n=input('number',s.value);n.min=s.min;n.max=s.max;n.step=1;const wrap=el('div','key-control'),bind=controlled(el('button','bind',keyName(s.value)),s),clear=controlled(el('button','apply','Clear'),s);bind.setAttribute('aria-label','Record '+s.name);bind.onblur=()=>{if(bind.onkeydown){bind.onkeydown=null;bind.textContent=keyName(Number(n.value));}};bind.onclick=()=>{bind.textContent='Press a key…';bind.onkeydown=event=>{event.preventDefault();event.stopPropagation();const code=glfwKey(event);if(code!==null){n.value=code;bind.textContent=keyName(code);bind.onkeydown=null;send();}};};clear.onclick=()=>{n.value=-1;send();};getValue=()=>Number(n.value);n.onchange=send;wrap.append(bind,clear);row.append(wrap);const advanced=el('details','key-code');advanced.append(el('summary','','Edit key code'),n,el('small','','GLFW code · -1 unbound · mouse buttons: -2147483648 through -2147483641'));row.append(advanced);}
 else if(s.type==='number'){const n=input('number',s.value);n.min=s.min;n.max=s.max;n.step=s.step>0?s.step:'any';row.append(n);getValue=()=>Number(n.value);const commit=()=>{if(n.value!==''&&n.checkValidity())send();else notice('Enter a value between '+s.min+' and '+s.max+'.');};n.onchange=commit;const apply=controlled(el('button','apply','Apply value'),s);apply.onclick=commit;row.append(apply);
 if(s.type==='number'){const slider=input('range',s.value);slider.min=s.min;slider.max=s.max;slider.step=s.step>0?s.step:'any';
 row.classList.add('slider-row');n.classList.add('slider-value');apply.hidden=true;label.append(n);
 const fill=()=>{const lo=Number(s.min),hi=Number(s.max),v=Number(slider.value);slider.style.setProperty('--p',(hi>lo?(v-lo)/(hi-lo)*100:0)+'%');};
 slider.oninput=()=>{n.value=slider.value;fill();};slider.onchange=send;n.addEventListener('input',()=>{slider.value=n.value;fill();});fill();row.insertBefore(slider,label.nextSibling);}}
 else if(s.type==='select'){const n=controlled(el('select'),s);first=n;n.id=controlId;for(const option of s.options){const o=el('option','',option.label);o.value=option.value;n.append(o);}n.value=s.value;getValue=()=>n.value;n.onchange=send;row.append(n);}
 else if(s.type==='multi'||s.type==='slots'){const wrap=el('div','checks'),inputs=[];const options=s.type==='slots'?Array.from({length:9},(_,i)=>({label:String(i+1),value:i})):s.options;for(const o of options){const lab=el('label','',o.label),n=input('checkbox',s.type==='slots'?s.value[o.value]:s.value.includes(o.value));lab.prepend(n);wrap.append(lab);inputs.push([n,o.value]);n.onchange=send;}getValue=()=>s.type==='slots'?inputs.map(([n])=>n.checked):inputs.filter(([n])=>n.checked).map(([,v])=>v);row.append(wrap);}
 else if(s.type==='range'){const wrap=el('div','control-row');const a=input('number',s.value[0]),b=input('number',s.value[1]);for(const n of[a,b]){n.min=s.min;n.max=s.max;n.step=s.step>0?s.step:'any';}a.setAttribute('aria-label',s.name+' minimum');b.setAttribute('aria-label',s.name+' maximum');wrap.append(a,el('span','','to'),b);getValue=()=>[Number(a.value),Number(b.value)];row.append(wrap);const apply=controlled(el('button','apply','Apply range'),s);apply.onclick=()=>{if(a.value&&b.value&&a.checkValidity()&&b.checkValidity())send();else notice('Enter a valid range.');};row.append(apply);}
 else if(['list','registry','text','color'].includes(s.type)){const list=s.type==='list'||s.type==='registry';const n=list?controlled(el('textarea'),s):input(s.private?'password':'text','');if(!first){first=n;n.id=controlId;}n.value=list?(s.value||[]).join('\n'):(s.value??'');if(s.maxLength&&!list)n.maxLength=s.maxLength;n.placeholder=s.private?'Private value — enter a replacement':'';getValue=()=>list?n.value.split('\n').map(x=>x.trim()).filter(Boolean):n.value;row.append(n);const apply=controlled(el('button','apply','Apply'),s);apply.onclick=send;row.append(apply);if(list)row.append(el('small','',s.type==='registry'?'One registry ID per line, e.g. minecraft:stone':'One entry per line'));if(s.type==='color'){const color=input('color','#'+(s.value||'ffb9a0ff').slice(2)),alpha=input('number',parseInt((s.value||'ffb9a0ff').slice(0,2),16)),palette=el('div','color-controls');color.setAttribute('aria-label',s.name+' color');alpha.setAttribute('aria-label',s.name+' opacity');alpha.min=0;alpha.max=255;alpha.step=1;const recolor=()=>{if(!alpha.checkValidity()||alpha.value==='')return;n.value=Number(alpha.value).toString(16).padStart(2,'0')+color.value.slice(1);send();};color.onchange=recolor;alpha.onchange=recolor;palette.append(color,el('span','','Opacity'),alpha);row.insertBefore(palette,n);row.append(el('small','','ARGB hex includes opacity.'));}}
 else row.append(el('small','','This setting has no editable value.'));
 if(s.description)row.append(el('small','',s.description));if(!s.visible)row.append(el('small','dependency','Conditional: editable now; used when its in-game condition is active.'));
 row.addEventListener('focusin',()=>{if(editingRevision===null)editingRevision=m.revision;});settings.append(row);}
 if(!m.settings.length)settings.append(el('p','empty','This module has no configurable settings.'));
 const scroll=$('inspector').querySelector('.settings')?.scrollTop||0;$('inspector').replaceChildren(head,settings);settings.scrollTop=scroll;filterSettings();
}
$('settingSearch').oninput=filterSettings;
$('search').oninput=render;$('activeOnly').onchange=render;
document.addEventListener('keydown',e=>{if(e.key==='/'&&!e.ctrlKey&&!e.metaKey&&!e.altKey&&!/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName||'')){e.preventDefault();$('search').focus();$('search').select();}});
$('search').addEventListener('keydown',e=>{if(e.key==='Escape'){$('search').value='';$('search').blur();render();}});
$('settingSearch').addEventListener('keydown',e=>{if(e.key==='Escape'){$('settingSearch').value='';$('settingSearch').blur();filterSettings();}});
$('inspector').addEventListener('focusout',()=>setTimeout(()=>{if(!$('inspector').contains(document.activeElement)&&!failedDraft){editingRevision=null;renderDetail();}},0));
async function poll(){try{if(!busy){const next=await api('/api/state');if(!busy){state=next;connection(true);if(connNotice)notice('');render();}}}catch(e){connection(false);notice(e.status===401?'Not paired. In Minecraft, turn Misc → Web UI off and on to open a paired browser.':'Minecraft is disconnected. Enable Web UI again to reconnect.');connNotice=true;}finally{setTimeout(poll,online?250:1500);}}
initAppearance();
poll();

function filterSettings(){const q=$('settingSearch').value.toLowerCase().trim();let shown=0,heading=null,headingHas=false;
 const flush=()=>{if(heading)heading.hidden=!headingHas;};
 for(const node of document.querySelectorAll('#inspector .settings>*')){
  if(node.classList.contains('group-heading')){flush();heading=node;headingHas=false;continue;}
  if(node.dataset.search===undefined)continue;
  node.hidden=!node.dataset.search.includes(q);if(!node.hidden){shown++;headingHas=true;}}
 flush();const count=selectedModule()?.settings.length||0;$('settingCount').textContent=shown+' / '+count+' settings';}
function keyName(n){if(n===-1)return 'Unbound';if(n<0)return 'Mouse '+((n&255)+1);if(n>=65&&n<=90||n>=48&&n<=57)return String.fromCharCode(n);if(n>=290&&n<=314)return 'F'+(n-289);return ({32:'Space',256:'Escape',257:'Enter',258:'Tab',259:'Backspace',260:'Insert',261:'Delete',262:'Right',263:'Left',264:'Down',265:'Up',340:'Left Shift',341:'Left Ctrl',342:'Left Alt',344:'Right Shift',345:'Right Ctrl',346:'Right Alt'})[n]||'Key '+n;}
function glfwKey(e){if(/^Key[A-Z]$/.test(e.code))return e.code.charCodeAt(3);if(/^Digit[0-9]$/.test(e.code))return e.code.charCodeAt(5);if(/^F[0-9]+$/.test(e.code))return 289+Number(e.code.slice(1));return ({Space:32,Escape:256,Enter:257,Tab:258,Backspace:259,Insert:260,Delete:261,ArrowRight:262,ArrowLeft:263,ArrowDown:264,ArrowUp:265,ShiftLeft:340,ControlLeft:341,AltLeft:342,MetaLeft:343,ShiftRight:344,ControlRight:345,AltRight:346,MetaRight:347,Comma:44,Period:46,Slash:47,Semicolon:59,Quote:39,BracketLeft:91,BracketRight:93,Backslash:92,Minus:45,Equal:61,Backquote:96})[e.code]??null;}
function initAppearance(){let prefs={accent:'#ededed',mode:'dark',density:'comfortable',scale:'100'};try{prefs={...prefs,...JSON.parse(localStorage.getItem('utility-appearance')||'{}')};}catch{}
 const apply=()=>{document.documentElement.style.setProperty('--accent',prefs.accent);document.documentElement.dataset.mode=prefs.mode;document.documentElement.dataset.density=prefs.density;document.documentElement.style.fontSize=prefs.scale+'%';localStorage.setItem('utility-appearance',JSON.stringify(prefs));};
 for(const id of ['accent','mode','density','scale']){const input=$('appearance-'+id);input.value=prefs[id];input.onchange=()=>{prefs[id]=input.value;apply();};}apply();}

// Ambient starfield painted into #bg; accent-tinted, pauses under reduced-motion.
(function starfield(){
 const cv=document.getElementById('bg');if(!cv)return;const cx=cv.getContext('2d');
 if(!cx||matchMedia('(prefers-reduced-motion: reduce)').matches)return;
 const N=64,ps=[];for(let i=0;i<N;i++)ps.push({x:Math.random(),y:Math.random(),d:.25+Math.random()*.75,ph:Math.random()*6.28});
 function hexRgb(h){h=(h||'#9bd3fa').trim().replace('#','');if(h.length===3)h=h.split('').map(c=>c+c).join('');const n=parseInt(h,16);return[(n>>16)&255,(n>>8)&255,n&255];}
 function draw(t){requestAnimationFrame(draw);
  const dpr=Math.min(2,devicePixelRatio||1),w=cv.clientWidth,h=cv.clientHeight;
  if(cv.width!==Math.round(w*dpr)){cv.width=Math.round(w*dpr);cv.height=Math.round(h*dpr);}
  cx.setTransform(dpr,0,0,dpr,0,0);cx.clearRect(0,0,w,h);
  const[r,g,b]=hexRgb(getComputedStyle(document.documentElement).getPropertyValue('--accent'));
  for(let k=0;k<N;k++){const p=ps[k];p.y+=.00018+p.d*.00045;if(p.y>1.03)p.y-=1.06;
   const sx=Math.sin(t/1000*(.3+p.d*.5)+p.ph)*.01*p.d;
   cx.globalAlpha=.05+p.d*.2;cx.beginPath();cx.arc((p.x+sx)*w,p.y*h,.6+p.d*1.4,0,6.3);
   cx.fillStyle='rgb('+r+','+g+','+b+')';cx.fill();}
 }
 requestAnimationFrame(draw);
})();
