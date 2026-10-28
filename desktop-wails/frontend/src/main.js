import './style.css';
import './sync.css';
import './records.css';
import './agenda.css';
import QRCode from 'qrcode';
import { createIcons, BedDouble, Utensils, Droplets, NotebookTabs, ChartNoAxesCombined, LockKeyhole, Wifi, Copy, Moon, Sun, Clock3, CheckCircle2, CircleX, RefreshCw, CalendarDays, Plus, Pencil, Trash2, Target, ChevronLeft, ChevronRight, GripVertical, ClockArrowRight, Flag, Sparkles, FileUp, Library, Star, QrCode } from 'lucide';
import { GetState } from '../wailsjs/go/main/App';
import { EventsOn } from '../wailsjs/runtime/runtime';
import { bindRecordActions } from './records.js';
import { renderAgendaScreen, bindAgendaActions } from './agenda.js';
import { buildSyncQrPayload } from './sync-qr.js';

const icons = { BedDouble, Utensils, Droplets, NotebookTabs, ChartNoAxesCombined, LockKeyhole, Wifi, Copy, Moon, Sun, Clock3, CheckCircle2, CircleX, RefreshCw, CalendarDays, Plus, Pencil, Trash2, Target, ChevronLeft, ChevronRight, GripVertical, ClockArrowRight, Flag, Sparkles, FileUp, Library, Star, QrCode };
const tabs = [
  ['sleep', 'Uyku', 'BedDouble'], ['nutrition', 'Beslenme', 'Utensils'], ['hydration', 'Su', 'Droplets'],
  ['agenda', 'Ajanda', 'NotebookTabs'], ['private', 'Özel', 'LockKeyhole'], ['stats', 'İstatistik', 'ChartNoAxesCombined'],
];
let state = { pairingCode: '--------', addresses: [], status: 'Başlatılıyor', snapshot: null };
let activeTab = 'sleep';
let selectedAddress = '';
let renderedQrPayload = '';

document.querySelector('#app').innerHTML = `
  <div class="app-shell">
    <aside class="nav-rail">
      <div class="brand"><span class="brand-mark">G</span><div><strong>Günlük Takip</strong><small>PC</small></div></div>
      <nav id="navigation" aria-label="Ana menü"></nav>
      <div class="local-badge"><i data-lucide="Wifi"></i><span>Yerel bağlantı</span></div>
    </aside>
    <main class="workspace">
      <header class="topbar">
        <div><span class="eyebrow">GÜNLÜK TAKİP</span><h1 id="page-title">Uyku</h1><p id="page-date"></p></div>
        <div class="sync-status"><span id="status-dot"></span><span id="status-text"></span></div>
      </header>
      <div class="content-grid">
        <section id="screen" class="screen" aria-live="polite"></section>
        <aside class="pair-panel">
          <div class="pair-heading"><i data-lucide="RefreshCw"></i><div><strong>Telefon senkronu</strong><small>Şifreli, doğrudan bağlantı</small></div></div>
          <div class="qr-heading"><i data-lucide="QrCode"></i><span>Telefondan tarat</span></div>
          <div class="qr-frame"><canvas id="sync-qr" width="200" height="200" aria-label="Telefon eşleştirme QR kodu"></canvas><p id="qr-error" hidden>QR kodu hazırlanamadı.</p></div>
          <label>PC ADRESİ</label><div class="copy-row"><select id="address-select" aria-label="QR kodunda kullanılacak PC adresi"></select><button id="copy-address" class="icon-button" title="Adresi kopyala"><i data-lucide="Copy"></i></button></div>
          <label>EŞLEŞTİRME KODU</label><div class="copy-row code-row"><code id="pair-code">--------</code><button id="copy-code" class="icon-button" title="Kodu kopyala"><i data-lucide="Copy"></i></button></div>
          <div class="steps"><span>1</span><p>Telefonda <b>İstatistikler</b> bölümünü aç.</p><span>2</span><p><b>PC ile senkronize et</b> ve ardından <b>QR kodunu tara</b>.</p><span>3</span><p>Kod okununca iki yönlü aktarım otomatik başlar.</p></div>
          <p id="last-sync" class="last-sync">Henüz senkron yok</p>
          <p id="automation-status" class="last-sync" role="status"></p>
        </aside>
      </div>
    </main>
  </div><div id="modal-layer"></div>`;

function renderNavigation() {
  document.querySelector('#navigation').innerHTML = tabs.map(([id, label, icon]) => `
    <button class="nav-item ${activeTab === id ? 'active' : ''}" data-tab="${id}"><i data-lucide="${icon}"></i><span>${label}</span></button>`).join('');
  document.querySelectorAll('.nav-item').forEach(button => button.addEventListener('click', () => { activeTab = button.dataset.tab; render(); }));
}

function render() {
  renderNavigation();
  const selected = tabs.find(item => item[0] === activeTab);
  document.querySelector('#page-title').textContent = selected[1];
  document.querySelector('#page-date').textContent = new Intl.DateTimeFormat('tr-TR', { dateStyle: 'full' }).format(new Date());
  document.querySelector('#status-text').textContent = state.status;
  document.querySelector('#status-dot').className = state.status.includes('bekleniyor') || state.status.includes('tamamlandı') ? 'online' : 'warning';
  const addresses = state.addresses?.length ? state.addresses : [];
  if (!addresses.includes(selectedAddress)) selectedAddress = addresses[0] || '';
  const addressSelect = document.querySelector('#address-select');
  addressSelect.innerHTML = addresses.length ? addresses.map(address => `<option value="${escapeHtml(address)}">${escapeHtml(address)}</option>`).join('') : '<option value="">Adres bulunamadı</option>';
  addressSelect.value = selectedAddress;
  addressSelect.disabled = !addresses.length;
  document.querySelector('#pair-code').textContent = state.pairingCode;
  document.querySelector('#last-sync').textContent = state.snapshot ? `Son senkron ${dateTime(state.snapshot.generatedAtMillis)}` : 'Henüz senkron yok';
  document.querySelector('#automation-status').textContent = state.automationStatus || 'Günlük analiz bekleniyor';
  document.querySelector('#screen').innerHTML = renderScreen();
  createIcons({ icons, attrs: { 'stroke-width': 1.8 } });
  if (state.snapshot) bindRecordActions(state.snapshot);
  if (state.snapshot && activeTab === 'agenda') bindAgendaActions(state.snapshot, render);
  updateSyncQr();
}

async function updateSyncQr() {
  const canvas = document.querySelector('#sync-qr');
  const error = document.querySelector('#qr-error');
  if (!canvas || !selectedAddress || !/^\d{8}$/.test(state.pairingCode)) {
    if (canvas) canvas.hidden = true;
    if (error) error.hidden = true;
    return;
  }
  try {
    const payload = buildSyncQrPayload(selectedAddress, state.pairingCode, state.serviceId);
    if (payload !== renderedQrPayload) {
      await QRCode.toCanvas(canvas, payload, { width: 200, margin: 1, errorCorrectionLevel: 'M', color: { dark: '#080d14', light: '#ffffff' } });
      renderedQrPayload = payload;
    }
    canvas.hidden = false;
    error.hidden = true;
  } catch (reason) {
    canvas.hidden = true;
    error.textContent = `QR kodu hazırlanamadı: ${reason?.message || reason}`;
    error.hidden = false;
  }
}

function renderScreen() {
  if (!state.snapshot) return `<div class="empty"><i data-lucide="RefreshCw"></i><h2>Telefon verisi bekleniyor</h2><p>İlk senkrondan sonra kayıtların burada görünecek.</p></div>`;
  switch (activeTab) {
    case 'nutrition': return nutritionScreen();
    case 'hydration': return hydrationScreen();
    case 'agenda': return renderAgendaScreen(state.snapshot);
    case 'private': return privateScreen();
    case 'stats': return statsScreen();
    default: return sleepScreen();
  }
}

function sleepScreen() {
  const s = state.snapshot; const active = s.activeSleepStartMillis;
  const total = s.sleeps.reduce((sum, item) => sum + item.endMillis - item.startMillis, 0);
  const week = s.sleeps.filter(item => item.endMillis > Date.now() - 7 * 86400000).reduce((sum, item) => sum + item.endMillis - item.startMillis, 0);
  return `<article class="live-card sleep-border">
      <div class="card-label"><i data-lucide="${active ? 'Moon' : 'Clock3'}"></i>${active ? 'Uyku takibi devam ediyor' : 'Takip hazır'}</div>
      <div class="live-time" data-active="${active || ''}">${active ? duration(Date.now() - active, true) : time(Date.now())}</div>
      <p>${active ? `Başlangıç ${time(active)}` : 'Takibi PC veya telefondan başlatabilirsin'}</p>
      <div class="action-row"><button data-action="start-sleep" ${active?'disabled':''}><i data-lucide="Moon"></i>Başlat</button><button class="tonal" data-action="finish-sleep" ${active?'':'disabled'}><i data-lucide="Sun"></i>Uyandım</button></div>
    </article>
    <div class="metrics"><div><span>Son uyku</span><strong class="sleep">${s.sleeps[0] ? duration(s.sleeps[0].endMillis-s.sleeps[0].startMillis) : '-'}</strong></div><div><span>Ortalama</span><strong class="warm">${s.sleeps.length ? duration(total/s.sleeps.length) : '-'}</strong></div><div><span>Son 7 gün</span><strong class="water">${duration(week)}</strong></div></div>
    ${sectionHeader('Uyku geçmişi',s.sleeps.length,'add-sleep')}
    <div class="record-list">${s.sleeps.slice(0,30).map(item => `<div class="record"><div><strong>${shortDate(item.startMillis)}</strong><span>${time(item.startMillis)} → ${time(item.endMillis)}</span></div><b class="sleep">${duration(item.endMillis-item.startMillis)}</b>${rowActions('sleep',item.id,'edit-sleep')}</div>`).join('') || emptyRow('Henüz uyku kaydı yok')}</div>`;
}

function nutritionScreen() {
  const meals = state.snapshot.meals;
  return `<div class="metrics"><div><span>Toplam öğün</span><strong class="meal">${meals.length}</strong></div><div><span>Bugün</span><strong class="warm">${meals.filter(i=>sameDay(i.startMillis)).length}</strong></div><div><span>Son öğün</span><strong>${meals[0] ? time(meals[0].startMillis) : '-'}</strong></div></div>
    ${sectionHeader('Beslenme geçmişi',meals.length,'add-meal')}<div class="record-list">${meals.map(item => `<div class="record meal-record"><div><strong>${mealName(item.type)} · ${shortDate(item.startMillis)}</strong><span>${time(item.startMillis)} · ${foods(item.itemsJson)}</span></div><b class="meal">${item.quick ? 'Hızlı kayıt' : duration(item.endMillis-item.startMillis)}</b>${rowActions('meal',item.id,'edit-meal')}</div>`).join('') || emptyRow('Henüz öğün kaydı yok')}</div>`;
}

function hydrationScreen() {
  const water = state.snapshot.water; const today = water.filter(i=>sameDay(i.timestampMillis)).reduce((sum,i)=>sum+i.amountMl,0); const goal=state.snapshot.waterGoalMl;
  return `<article class="live-card water-border"><div class="card-label water"><i data-lucide="Droplets"></i>BUGÜNKÜ SU</div><div class="hydration-value">${today}<small> / ${goal} ml</small></div><div class="progress"><span style="width:${Math.min(100,today/goal*100)}%"></span></div><p>Günlük hedefin %${Math.round(Math.min(1,today/goal)*100)} tamamlandı</p><div class="quick-actions"><button data-action="add-water" data-amount="125">125 ml</button><button data-action="add-water" data-amount="250">250 ml</button><button data-action="add-water" data-amount="500">500 ml</button><button data-action="custom-water">Özel</button><button data-action="water-goal"><i data-lucide="Target"></i> Hedef</button></div></article>
    ${sectionHeader('Su geçmişi',water.length,'custom-water')}<div class="record-list compact">${water.map(item=>`<div class="record"><div><strong>${shortDate(item.timestampMillis)}</strong><span>${time(item.timestampMillis)}</span></div><b class="water">${item.amountMl} ml</b>${rowActions('water',item.id,'edit-water')}</div>`).join('') || emptyRow('Henüz su kaydı yok')}</div>`;
}

function privateScreen() {
  const items=state.snapshot.privateActivities;
  return `<article class="privacy-note"><i data-lucide="LockKeyhole"></i><div><strong>Özel alan</strong><p>Bu kayıtlar yalnızca telefonun ve bu bilgisayarda saklanır.</p></div></article>${sectionHeader('Geçmiş',items.length,'add-private')}<div class="record-list">${items.map(i=>`<div class="record"><div><strong>${shortDate(i.startMillis)}</strong><span>${time(i.startMillis)}</span></div><b class="warm">${i.endMillis?duration(i.endMillis-i.startMillis):'Devam ediyor'}</b>${rowActions('private',i.id,'edit-private')}</div>`).join('') || emptyRow('Henüz kayıt yok')}</div>`;
}

function statsScreen() {
  const s=state.snapshot; const totalSleep=s.sleeps.reduce((x,i)=>x+i.endMillis-i.startMillis,0); const water=s.water.reduce((x,i)=>x+i.amountMl,0); const done=s.agenda.filter(i=>i.task&&i.completed).length;
  return `<div class="metrics two-row"><div><span>Uyku kaydı</span><strong class="sleep">${s.sleeps.length}</strong></div><div><span>Toplam uyku</span><strong class="sleep">${duration(totalSleep)}</strong></div><div><span>Öğün</span><strong class="meal">${s.meals.length}</strong></div><div><span>İçilen su</span><strong class="water">${water} ml</strong></div><div><span>Tamamlanan görev</span><strong class="warm">${done}</strong></div><div><span>Toplam kayıt</span><strong>${s.sleeps.length+s.meals.length+s.water.length+s.agenda.length}</strong></div></div>`;
}

function duration(ms, timer=false){const total=Math.max(0,Math.floor(ms/1000)), h=Math.floor(total/3600),m=Math.floor(total%3600/60),s=total%60;return timer?`${pad(h)}:${pad(m)}:${pad(s)}`:`${h} sa ${m} dk`;}
function dateTime(ms){return new Intl.DateTimeFormat('tr-TR',{dateStyle:'medium',timeStyle:'short'}).format(new Date(ms));}
function shortDate(ms){return new Intl.DateTimeFormat('tr-TR',{day:'numeric',month:'short',weekday:'short'}).format(new Date(ms));}
function time(ms){return new Intl.DateTimeFormat('tr-TR',{hour:'2-digit',minute:'2-digit'}).format(new Date(ms));}
function sameDay(ms){return isoDay(new Date(ms))===isoDay(new Date());} function isoDay(d){return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;} function pad(v){return String(v).padStart(2,'0');}
function mealName(v){return {BREAKFAST:'Kahvaltı',LUNCH:'Öğle',DINNER:'Akşam',SNACK:'Atıştırma'}[v]||'Öğün';}
function foods(raw){try{return JSON.parse(raw).map(i=>i.name).join(', ')||'-';}catch{return '-';}}
function emptyRow(text){return `<div class="empty-row">${text}</div>`;} function escapeHtml(v){const e=document.createElement('div');e.textContent=v;return e.innerHTML;}
function sectionHeader(title,count,action){return `<div class="section-title"><h2>${title}</h2><div><span>${count} kayıt</span><button class="small-action" data-action="${action}" title="Kayıt ekle"><i data-lucide="Plus"></i></button></div></div>`;}
function rowActions(kind,id,edit){return `<div class="row-actions"><button class="icon-button" data-action="${edit}" data-id="${id}" title="Düzenle"><i data-lucide="Pencil"></i></button><button class="icon-button danger" data-action="delete" data-kind="${kind}" data-id="${id}" title="Sil"><i data-lucide="Trash2"></i></button></div>`;}

document.querySelector('#address-select').addEventListener('change',event=>{selectedAddress=event.target.value;renderedQrPayload='';updateSyncQr();});
document.querySelector('#copy-address').addEventListener('click',()=>navigator.clipboard.writeText(selectedAddress));
document.querySelector('#copy-code').addEventListener('click',()=>navigator.clipboard.writeText(state.pairingCode));
if (window.runtime) {
  EventsOn('sync:updated', value => { state=value; render(); });
  EventsOn('sync:status', value => { state=value; render(); });
}
async function initialise() {
  try { state = await GetState(); }
  catch (error) {
    if (!import.meta.env.DEV) throw error;
    const now=Date.now();
    state={pairingCode:'00000000',addresses:['192.168.1.10'],status:'Telefon bekleniyor',snapshot:{generatedAtMillis:now-120000,activeSleepStartMillis:null,waterGoalMl:2500,
      sleeps:[{id:1,startMillis:now-9*3600000,endMillis:now-3600000},{id:2,startMillis:now-33*3600000,endMillis:now-25*3600000}],
      meals:[{id:1,type:'BREAKFAST',startMillis:now-5*3600000,endMillis:now-5*3600000+1200000,itemsJson:'[{"name":"Yumurta"},{"name":"Ekmek"}]',quick:false}],
      water:[{id:1,amountMl:250,timestampMillis:now-3600000},{id:2,amountMl:125,timestampMillis:now-7200000}],
      agenda:[{id:1,day:isoDay(new Date()),text:'Yürüyüş yap',task:true,completed:false,notDone:false,repeatDaily:false,generalGoal:false,streak:0,notifyEnabled:true,notifyHour:20,notifyMinute:0},{id:2,day:isoDay(new Date()),text:'Kahvaltıyı hazırladım ve evi toparladım.',task:false,completed:false,notDone:false,repeatDaily:false,generalGoal:false,streak:0,notifyEnabled:false},{id:3,day:isoDay(new Date()),text:'Valizi sil',task:true,completed:false,notDone:false,repeatDaily:false,generalGoal:true,streak:0,notifyEnabled:false}],privateActivities:[]}};
  }
  render();
}
initialise();
setInterval(()=>{const timer=document.querySelector('.live-time[data-active]:not([data-active=""])');if(timer)timer.textContent=duration(Date.now()-Number(timer.dataset.active),true);},1000);
