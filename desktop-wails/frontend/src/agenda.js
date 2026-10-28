import { parseAgendaText } from './smart-agenda.js';
import { readParts, progressPercent, appendPendingPart } from './agenda-parts.js';

const backend = name => (...args) => window.go.main.App[name](...args);
const api = {
  save:backend('SaveAgenda'), remove:backend('DeleteRecord'), complete:backend('ToggleAgendaCompleted'), notDone:backend('ToggleAgendaNotDone'),
  toGeneral:backend('MoveAgendaToGeneral'), toDay:backend('MoveAgendaToDay'), postpone:backend('PostponeAgenda'),
  count:backend('AdjustAgendaDailyCount'),
  saveTemplate:backend('SaveAgendaTemplate'), favorite:backend('SetAgendaTemplateFavorite'), removeTemplate:backend('RemoveAgendaTemplate'),
  saveCategory:backend('SaveAgendaCategory'), removeCategory:backend('RemoveAgendaCategory'), hideSuggestion:backend('HideAgendaSuggestion'),
};
const pad=value=>String(value).padStart(2,'0');
const isoDay=date=>`${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}`;
const shiftDay=(day,amount)=>{const date=new Date(`${day}T12:00:00`);date.setDate(date.getDate()+amount);return isoDay(date);};
const escape=value=>String(value??'').replace(/[&<>'"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
const key=(text,category,subcategory)=>[text,category,subcategory].map(v=>String(v||'').trim()).join('\u001f');
let selectedDay=isoDay(new Date());

function labelDay(day){return new Intl.DateTimeFormat('tr-TR',{dateStyle:'full'}).format(new Date(`${day}T12:00:00`));}
function meta(item){return [item.category,item.subcategory].filter(Boolean).join(' · ')||(item.notifyEnabled?`${pad(item.notifyHour)}:${pad(item.notifyMinute)} bildirim`:item.repeatDaily?'Her gün tekrarlanır':item.day);}
function actionButton(action,id,icon,title,className=''){return `<button class="icon-button ${className}" data-agenda-action="${action}" data-id="${id}" title="${title}"><i data-lucide="${icon}"></i></button>`;}
function row(item, general=false){
  const parts=readParts(item.projectPartsJson), progress=progressPercent(parts);
  const status=item.completed?'CheckCircle2':item.notDone?'CircleX':item.task?'Clock3':'NotebookTabs';
  const totalDays=item.repeatDaily?`<span>Toplam yapılan gün: ${item.totalCompletedDays}</span>`:'';
  return `<div class="agenda-row agenda-record" draggable="false" data-agenda-id="${item.id}" data-general="${general}">
    ${item.task?`<button class="drag-handle" aria-label="Taşımak için tutup sürükle" title="Taşımak için tutup sürükle"><i data-lucide="GripVertical"></i></button>`:'<span class="drag-space"></span>'}
    ${item.repeatDaily?`<span class="daily-count" aria-label="Seçili gün ${item.dailyCount} kez">${item.dailyCount}</span>`:`<button class="status-button" data-agenda-action="${parts.length?'parts':'complete'}" data-id="${item.id}" ${item.task?'':'disabled'} title="${parts.length?'Alt görevleri düzenle':item.completed?'Tamamlanmadı yap':'Tamamlandı yap'}"><i data-lucide="${status}"></i></button>`}
    <div><strong class="${item.completed?'done-text':''}">${escape(item.text)}</strong><span>${escape(meta(item))}</span>${totalDays}${parts.length?`<span>%${progress} · ${parts.filter(part=>part.progress===100).length}/${parts.length} alt görev</span><progress class="project-progress" value="${progress}" max="100" aria-label="Proje ilerlemesi"></progress>${parts.slice(0,3).map(part=>`<span>${escape(part.title)} · %${part.progress}</span>`).join('')}`:''}</div>
    ${item.streak?`<b>${item.streak} günlük seri</b>`:''}
    <div class="agenda-inline-actions">
      ${item.repeatDaily?`<button class="daily-done" data-agenda-action="increment" data-id="${item.id}"><i data-lucide="Check"></i>Yaptım</button>`:''}
      ${item.repeatDaily&&item.dailyCount>0?actionButton('decrement',item.id,'Minus','Günlük adedi azalt'):''}
      ${item.task&&!item.repeatDaily&&!general&&!parts.length?actionButton('not-done',item.id,'CircleX',item.notDone?'Yapılmadı işaretini kaldır':'Yapılmadı olarak işaretle'):''}
      ${item.task&&!general&&!item.repeatDaily?actionButton('postpone',item.id,'ClockArrowRight','Yarına ertele'):''}
      ${item.task&&!general?actionButton('to-general',item.id,'Flag','Genel hedeflere taşı'):''}
      ${item.task&&general?actionButton('to-day',item.id,'CalendarDays','Seçili güne taşı'):''}
      ${item.task&&!item.repeatDaily?actionButton('parts',item.id,'Target','Alt görevler'):''}
      ${actionButton('edit',item.id,'Pencil','Düzenle')}${actionButton('delete',item.id,'Trash2','Sil','danger')}
    </div>
  </div>`;
}
function group(title,items,empty,general=false,tone=''){return `<section class="agenda-section"><div class="section-title"><h2>${title}</h2><span class="${tone}">${items.length}</span></div><div class="record-list agenda-list">${items.map(item=>row(item,general)).join('')||`<div class="empty-row">${empty}</div>`}</div></section>`;}

export function renderAgendaScreen(snapshot){
  const items=(snapshot.agenda||[]).map(item=>{
    if (!item.repeatDaily) return item;
    const counts=JSON.parse(item.dailyCountsJson||'{}'), count=counts[selectedDay]||0;
    return {...item,dailyCount:count,totalCompletedDays:Object.values(counts).filter(value=>value>0).length,completed:false,notDone:false};
  }), visible=items.filter(item=>!item.generalGoal&&(item.day===selectedDay||item.repeatDaily&&item.day<=selectedDay));
  const daily=visible.filter(item=>item.task&&item.repeatDaily), pending=visible.filter(item=>item.task&&!item.repeatDaily&&!item.completed&&!item.notDone), notDone=visible.filter(item=>item.task&&!item.repeatDaily&&item.notDone), done=visible.filter(item=>item.task&&!item.repeatDaily&&item.completed&&!item.notDone), notes=visible.filter(item=>!item.task), goals=items.filter(item=>item.generalGoal&&!item.completed);
  return `<div class="agenda-datebar"><button class="icon-button" data-agenda-action="previous" title="Önceki gün"><i data-lucide="ChevronLeft"></i></button><input id="agenda-day" type="date" value="${selectedDay}" aria-label="Ajanda tarihi"><button class="icon-button" data-agenda-action="next" title="Sonraki gün"><i data-lucide="ChevronRight"></i></button><button class="secondary-action" data-agenda-action="today">Bugün</button><strong>${escape(labelDay(selectedDay))}</strong></div>
    <div class="agenda-toolbar"><button data-agenda-action="add-task"><i data-lucide="Plus"></i>Görev</button><button class="secondary-action" data-agenda-action="add-note"><i data-lucide="NotebookTabs"></i>Not</button><button class="secondary-action" data-agenda-action="add-goal"><i data-lucide="Flag"></i>Genel hedef</button><button class="tonal-action" data-agenda-action="smart"><i data-lucide="Sparkles"></i>Metni ayır</button><button class="secondary-action" data-agenda-action="import"><i data-lucide="FileUp"></i>İçe aktar</button><button class="secondary-action" data-agenda-action="library"><i data-lucide="Library"></i>Hazır görevler</button><input id="agenda-import" type="file" accept=".txt,.csv,.json,text/*" hidden></div>
    <div class="agenda-drop-zone top" data-drop="day"><i data-lucide="CalendarDays"></i>Günlük görevlere bırak</div>
    ${group('Yaptıklarım',done,'Bu gün tamamlanan görev yok')}
    ${group('Yapılmadı',notDone,'İşaretlenmiş yapılmadı kaydı yok',false,'danger-text')}
    ${group('Yapacaklarım',pending,'Bu gün için bekleyen görev yok',false,'warm')}
    ${daily.length?group('Günlük tekrarlar',daily,''):''}
    ${group('Notlar',notes,'Bu gün için not yok')}
    ${group('Genel hedefler',goals,'Bekleyen genel hedef yok',true,'warm')}
    ${items.some(item=>item.generalGoal&&item.completed&&readParts(item.projectPartsJson).length)?group('Tamamlanan projeler',items.filter(item=>item.generalGoal&&item.completed&&readParts(item.projectPartsJson).length),'',true):''}
    <div class="agenda-drop-zone bottom" data-drop="general"><i data-lucide="Flag"></i>Genel hedeflere bırak</div>`;
}

function modal(title,body,footer=''){
  const layer=document.querySelector('#modal-layer');
  layer.innerHTML=`<div class="modal-backdrop"><section class="editor-modal agenda-modal"><header><h2>${title}</h2><button type="button" class="icon-button" data-close aria-label="Kapat">×</button></header>${body}${footer}</section></div>`;
  const close=()=>{layer.innerHTML='';}; layer.querySelectorAll('[data-close]').forEach(button=>button.addEventListener('click',close)); return {layer,close};
}

function partsEditor(item,refresh){
  let parts=readParts(item.projectPartsJson).map(part=>({...part}));
  const {layer,close}=modal('Alt görevler',`<form id="project-editor"><div class="project-form"><p>${escape(item.text)}</p><strong id="project-summary"></strong><progress class="project-progress" id="project-progress" max="100"></progress><div id="project-parts-list"></div><div class="project-add"><input id="new-part" maxlength="200" aria-label="Yeni alt görev" placeholder="Yeni alt görev"><button type="button" id="add-part" aria-label="Alt görev ekle">+</button></div><p class="form-error" hidden></p></div><footer><button type="button" class="secondary" data-close>Vazgeç</button><button type="submit">Kaydet</button></footer></form>`);
  const summary=()=>{layer.querySelector('#project-summary').textContent=`%${progressPercent(parts)} · ${parts.filter(part=>part.progress===100).length}/${parts.length} tamamlandı`;layer.querySelector('#project-progress').value=progressPercent(parts);};
  const render=()=>{
    layer.querySelector('#project-parts-list').innerHTML=parts.map((part,index)=>`<div class="project-part" data-index="${index}"><input type="checkbox" data-done ${part.progress===100?'checked':''} aria-label="Alt görev tamamlandı"><input data-title value="${escape(part.title)}" maxlength="200" required aria-label="Alt görev adı"><button type="button" data-remove aria-label="Alt görevi sil">×</button><input type="range" data-progress min="0" max="100" step="1" value="${part.progress}" aria-label="Alt görev ilerlemesi"><output>%${part.progress}</output></div>`).join('');
    layer.querySelectorAll('.project-part').forEach(row=>{
      const part=parts[Number(row.dataset.index)],slider=row.querySelector('[data-progress]'),done=row.querySelector('[data-done]');
      const update=value=>{part.progress=value;slider.value=value;done.checked=value===100;row.querySelector('output').textContent=`%${value}`;summary();};
      row.querySelector('[data-title]').addEventListener('input',event=>part.title=event.target.value);
      slider.addEventListener('input',event=>update(Number(event.target.value)));
      done.addEventListener('change',event=>update(event.target.checked?100:0));
      row.querySelector('[data-remove]').addEventListener('click',()=>{parts=parts.filter(value=>value!==part);render();});
    });
    layer.querySelector('#add-part').disabled=parts.length>=100;
    layer.querySelector('#new-part').disabled=parts.length>=100;
    summary();
  };
  const addPending=()=>{const input=layer.querySelector('#new-part');parts=appendPendingPart(parts,input.value);input.value='';render();};
  layer.querySelector('#add-part').addEventListener('click',addPending);
  layer.querySelector('form').addEventListener('submit',async event=>{event.preventDefault();const button=event.currentTarget.querySelector('[type=submit]');button.disabled=true;try{addPending();readParts(JSON.stringify(parts));await api.save({...item,projectPartsJson:JSON.stringify(parts),completed:parts.length>0&&parts.every(part=>part.progress===100),notDone:false});close();refresh();}catch(error){const message=layer.querySelector('.form-error');message.textContent=String(error);message.hidden=false;button.disabled=false;}});
  render();
}
function catalog(snapshot){
  const templates=(snapshot.agendaTemplates||[]).filter(item=>!item.hidden).sort((a,b)=>Number(b.favorite)-Number(a.favorite)||b.updatedAtMillis-a.updatedAtMillis);
  const categories=(snapshot.agendaCategories||[]).filter(item=>!item.hidden).sort((a,b)=>`${a.category}\u0000${a.subcategory}`.localeCompare(`${b.category}\u0000${b.subcategory}`,'tr'));
  const hidden=new Set((snapshot.hiddenAgendaSuggestions||[]).map(item=>item.key));
  const templateKeys=new Set(templates.map(item=>key(item.text,item.category,item.subcategory)));
  const history=[];
  [...(snapshot.agenda||[])].sort((a,b)=>b.updatedAtMillis-a.updatedAtMillis).forEach(item=>{const itemKey=key(item.text,item.category,item.subcategory);if(item.task&&!hidden.has(itemKey)&&!templateKeys.has(itemKey)&&!history.some(old=>key(old.text,old.category,old.subcategory)===itemKey))history.push(item);});
  return {templates,categories,history:history.slice(0,100)};
}
function editor(snapshot,item,refresh){
  const data=catalog(snapshot), suggestions=[...data.templates,...data.history].slice(0,30), title=item.generalGoal?'Genel hedef':item.task?'Görev':'Günlük not';
  const {layer,close}=modal(title,`<form id="agenda-editor"><div class="form-grid agenda-form"><label class="wide">${item.task?'Yapılacak iş':'Notun'}<textarea name="text" maxlength="4000" required>${escape(item.text)}</textarea></label><label>Tarih<input name="day" type="date" value="${item.day}" required></label><label>Tür<select name="kind"><option value="task" ${item.task&&!item.generalGoal?'selected':''}>Görev</option><option value="note" ${!item.task?'selected':''}>Not</option><option value="goal" ${item.generalGoal?'selected':''}>Genel hedef</option></select></label><label>Kategori<input name="category" value="${escape(item.category||'')}" maxlength="60" list="agenda-categories"></label><label>Alt kategori<input name="subcategory" value="${escape(item.subcategory||'')}" maxlength="60" list="agenda-subcategories"></label><datalist id="agenda-categories">${[...new Set(data.categories.map(v=>v.category))].map(v=>`<option value="${escape(v)}">`).join('')}</datalist><datalist id="agenda-subcategories">${[...new Set(data.categories.map(v=>v.subcategory).filter(Boolean))].map(v=>`<option value="${escape(v)}">`).join('')}</datalist>${item.id?'' : '<label class="check-field"><input type="checkbox" name="repeat"><span>Her gün tekrarla</span></label><label class="check-field"><input type="checkbox" name="notify"><span>Bildirim</span></label><label>Bildirim saati<input name="notifyTime" type="time" value="20:00"></label>'}<div class="wide suggestion-strip">${suggestions.slice(0,8).map((value,index)=>`<button type="button" data-suggestion="${index}">${escape(value.text)}</button>`).join('')}</div></div><p class="form-error" hidden></p><footer><button type="button" class="secondary" data-close>Vazgeç</button><button type="submit">Kaydet</button></footer></form>`);
  layer.querySelectorAll('[data-suggestion]').forEach(button=>button.addEventListener('click',()=>{const value=suggestions[Number(button.dataset.suggestion)],form=layer.querySelector('form');form.text.value=value.text;form.category.value=value.category||'';form.subcategory.value=value.subcategory||'';}));
  layer.querySelector('form').addEventListener('submit',async event=>{event.preventDefault();const form=event.currentTarget,dataForm=new FormData(form),kind=dataForm.get('kind'),parts=String(dataForm.get('notifyTime')||'20:00').split(':');try{await api.save({...item,text:dataForm.get('text'),day:dataForm.get('day'),task:kind!=='note',generalGoal:kind==='goal',category:dataForm.get('category'),subcategory:dataForm.get('subcategory'),repeatDaily:dataForm.has('repeat')&&kind==='task',notifyEnabled:dataForm.has('notify')&&kind==='task',notifyHour:Number(parts[0]),notifyMinute:Number(parts[1])});close();refresh();}catch(error){const message=form.querySelector('.form-error');message.textContent=String(error);message.hidden=false;}});
}
function library(snapshot,refresh){
  const data=catalog(snapshot), render=()=>{const {layer,close}=modal('Görev kitaplığı',`<div class="library-tabs"><button data-tab="tasks" class="active">Görevler</button><button data-tab="categories">Kategoriler</button></div><div class="library-pane" data-pane="tasks"><div class="library-form"><input data-field="template-text" placeholder="Yeni hazır görev"><input data-field="template-category" placeholder="Kategori"><input data-field="template-subcategory" placeholder="Alt kategori"><label><input data-field="template-favorite" type="checkbox"> Favori</label><button data-library="add-template">Ekle</button></div><div class="library-list">${data.templates.map(item=>`<div><button data-library="select" data-id="${escape(item.id)}"><strong>${escape(item.text)}</strong><small>${escape([item.category,item.subcategory].filter(Boolean).join(' · '))}</small></button><button data-library="favorite" data-id="${escape(item.id)}" title="Favori"><i data-lucide="Star"></i></button><button data-library="remove-template" data-id="${escape(item.id)}" title="Kaldır">×</button></div>`).join('')||'<p>Hazır görev yok.</p>'}<h3>Önceki görevler</h3>${data.history.slice(0,12).map((item,index)=>`<div><button data-library="select-history" data-index="${index}"><strong>${escape(item.text)}</strong><small>${escape([item.category,item.subcategory].filter(Boolean).join(' · '))}</small></button><button data-library="save-history" data-index="${index}" title="Hazır görevlere ekle"><i data-lucide="Star"></i></button><button data-library="hide-history" data-index="${index}" title="Önerilerden kaldır">×</button></div>`).join('')}</div></div><div class="library-pane" data-pane="categories" hidden><div class="library-form"><input data-field="category" placeholder="Kategori"><input data-field="subcategory" placeholder="Alt kategori"><button data-library="add-category">Ekle</button></div><div class="library-list">${data.categories.map(item=>`<div><button disabled><strong>${escape(item.category)}</strong><small>${escape(item.subcategory)}</small></button><button data-library="remove-category" data-id="${item.id}">×</button></div>`).join('')||'<p>Kategori yok.</p>'}</div></div>`,`<footer><button type="button" class="secondary" data-close>Kapat</button></footer>`);
    layer.querySelectorAll('[data-tab]').forEach(button=>button.addEventListener('click',()=>{layer.querySelectorAll('[data-tab]').forEach(v=>v.classList.toggle('active',v===button));layer.querySelectorAll('[data-pane]').forEach(v=>v.hidden=v.dataset.pane!==button.dataset.tab);}));
    layer.querySelectorAll('[data-library]').forEach(button=>button.addEventListener('click',async()=>{const action=button.dataset.library,id=button.dataset.id,index=Number(button.dataset.index),field=name=>layer.querySelector(`[data-field="${name}"]`);if(action==='add-template')await api.saveTemplate({id:`pc-${Date.now()}-${Math.random().toString(16).slice(2)}`,text:field('template-text').value,category:field('template-category').value,subcategory:field('template-subcategory').value,favorite:field('template-favorite').checked,builtIn:false,hidden:false,updatedAtMillis:0});if(action==='favorite'){const item=data.templates.find(v=>v.id===id);await api.favorite(id,!item.favorite);}if(action==='remove-template')await api.removeTemplate(id);if(action==='add-category')await api.saveCategory({id:0,category:field('category').value,subcategory:field('subcategory').value,hidden:false,updatedAtMillis:0});if(action==='remove-category')await api.removeCategory(Number(id));if(action==='save-history'){const item=data.history[index];await api.saveTemplate({id:`pc-${Date.now()}-${Math.random().toString(16).slice(2)}`,text:item.text,category:item.category||'',subcategory:item.subcategory||'',favorite:true,builtIn:false,hidden:false,updatedAtMillis:0});}if(action==='hide-history')await api.hideSuggestion(key(data.history[index].text,data.history[index].category,data.history[index].subcategory));if(action==='select'||action==='select-history'){const item=action==='select'?data.templates.find(v=>v.id===id):data.history[index];close();editor(snapshot,{id:0,day:selectedDay,text:item.text,task:true,completed:false,notDone:false,repeatDaily:false,generalGoal:false,streak:0,notifyEnabled:false,notifyHour:20,notifyMinute:0,category:item.category||'',subcategory:item.subcategory||'',updatedAtMillis:0},refresh);return;}close();refresh();}));
  }; render();
}
function smartEditor(snapshot,input,refresh){
  const {layer,close}=modal('Akıllı ajanda',`<form id="smart-agenda"><div class="smart-modes"><label><input type="radio" name="intent" value="auto" checked>Karışık</label><label><input type="radio" name="intent" value="completed">Yaptıklarım</label><label><input type="radio" name="intent" value="planned">Yapacaklarım</label></div><textarea name="input" maxlength="8000" placeholder="Bugün yürüyüş yaptım. Yarın 14:30 dişçiye gideceğim.">${escape(input)}</textarea><div id="smart-preview"></div><p class="form-error" hidden></p><footer><button type="button" class="secondary" data-close>Vazgeç</button><button type="button" data-parse>Taslağı hazırla</button><button type="submit" disabled>Seçilenleri kaydet</button></footer></form>`);
  const form=layer.querySelector('form');let drafts=[];form.querySelector('[data-parse]').addEventListener('click',()=>{drafts=parseAgendaText(form.input.value,selectedDay,new FormData(form).get('intent'));form.querySelector('#smart-preview').innerHTML=drafts.map((item,index)=>`<label class="smart-draft"><input type="checkbox" data-draft="${index}" checked><span><strong>${escape(item.text)}</strong><small>${item.day} · ${item.completed?'Yapıldı':item.task?'Yapılacak':'Not'}${item.notifyEnabled?` · ${pad(item.notifyHour)}:${pad(item.notifyMinute)}`:''}</small></span></label>`).join('')||'<p>Kayıt bulunamadı.</p>';form.querySelector('[type=submit]').disabled=!drafts.length;});
  form.addEventListener('submit',async event=>{event.preventDefault();const selected=new Set([...form.querySelectorAll('[data-draft]:checked')].map(v=>Number(v.dataset.draft)));try{for(const [index,item] of drafts.entries())if(selected.has(index))await api.save({id:0,...item,notDone:false,repeatDaily:false,generalGoal:false,streak:item.completed?1:0,category:'',subcategory:'',updatedAtMillis:0});close();refresh();}catch(error){const message=form.querySelector('.form-error');message.textContent=String(error);message.hidden=false;}});
  if(input.trim()) form.querySelector('[data-parse]').click();
}

export function bindAgendaActions(snapshot,refresh){
  document.querySelectorAll('[data-agenda-action="increment"],[data-agenda-action="decrement"]').forEach(button=>button.addEventListener('click',async event=>{
    event.stopImmediatePropagation();
    button.disabled=true;
    try { await api.count(Number(button.dataset.id),selectedDay,button.dataset.agendaAction==='increment'?1:-1); refresh(); }
    catch(error) { button.disabled=false; alert(String(error)); }
  }));
  document.querySelectorAll('[data-agenda-action="parts"]').forEach(button=>button.addEventListener('click',event=>{event.stopImmediatePropagation();partsEditor((snapshot.agenda||[]).find(item=>item.id===Number(button.dataset.id)),refresh);}));
  document.querySelector('#agenda-day')?.addEventListener('change',event=>{selectedDay=event.target.value;refresh();});
  document.querySelectorAll('[data-agenda-action]').forEach(button=>button.addEventListener('click',async()=>{const action=button.dataset.agendaAction,id=Number(button.dataset.id||0),item=(snapshot.agenda||[]).find(v=>v.id===id),blank={id:0,day:selectedDay,text:'',task:true,completed:false,notDone:false,repeatDaily:false,generalGoal:false,streak:0,notifyEnabled:false,notifyHour:20,notifyMinute:0,category:'',subcategory:'',updatedAtMillis:0};if(action==='previous'){selectedDay=shiftDay(selectedDay,-1);return refresh();}if(action==='next'){selectedDay=shiftDay(selectedDay,1);return refresh();}if(action==='today'){selectedDay=isoDay(new Date());return refresh();}if(action==='add-task')return editor(snapshot,blank,refresh);if(action==='add-note')return editor(snapshot,{...blank,task:false},refresh);if(action==='add-goal')return editor(snapshot,{...blank,generalGoal:true},refresh);if(action==='edit')return editor(snapshot,item,refresh);if(action==='library')return library(snapshot,refresh);if(action==='smart')return smartEditor(snapshot,'',refresh);if(action==='import')return document.querySelector('#agenda-import').click();if(action==='complete')await api.complete(id,selectedDay);if(action==='not-done')await api.notDone(id);if(action==='postpone')await api.postpone(id,shiftDay(selectedDay,1));if(action==='to-general')await api.toGeneral(id);if(action==='to-day')await api.toDay(id,selectedDay);if(action==='delete'&&confirm('Bu kayıt silinsin mi?'))await api.remove('agenda',id);}));
  const input=document.querySelector('#agenda-import');input?.addEventListener('change',async()=>{const file=input.files?.[0];if(file)smartEditor(snapshot,(await file.text()).slice(0,8000),refresh);});
  document.querySelectorAll('.agenda-record .drag-handle').forEach(handle=>{const row=handle.closest('.agenda-record');handle.addEventListener('pointerdown',()=>row.draggable=true);row.addEventListener('dragstart',event=>{event.dataTransfer.setData('text/plain',row.dataset.agendaId);event.dataTransfer.setData('application/x-agenda-general',row.dataset.general);document.body.classList.add('agenda-dragging',row.dataset.general==='true'?'dragging-general':'dragging-daily');});row.addEventListener('dragend',()=>{row.draggable=false;document.body.classList.remove('agenda-dragging','dragging-general','dragging-daily');});});
  document.querySelectorAll('[data-drop]').forEach(zone=>{zone.addEventListener('dragover',event=>{event.preventDefault();zone.classList.add('active');});zone.addEventListener('dragleave',()=>zone.classList.remove('active'));zone.addEventListener('drop',async event=>{event.preventDefault();const id=Number(event.dataTransfer.getData('text/plain')),target=zone.dataset.drop;zone.classList.remove('active');if(target==='general')await api.toGeneral(id);else await api.toDay(id,selectedDay);document.body.classList.remove('agenda-dragging','dragging-general','dragging-daily');});});
}
