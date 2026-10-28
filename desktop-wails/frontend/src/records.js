const backend = name => (...args) => window.go.main.App[name](...args);
const api = {
  startSleep: backend('StartSleep'), finishSleep: backend('FinishSleep'), saveSleep: backend('SaveSleep'),
  saveMeal: backend('SaveMeal'), saveWater: backend('SaveWater'), setWaterGoal: backend('SetWaterGoal'),
  saveAgenda: backend('SaveAgenda'), savePrivate: backend('SavePrivate'), deleteRecord: backend('DeleteRecord'),
};

const localValue = millis => {
  const date = new Date(millis ?? Date.now());
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
};
const millis = value => new Date(value).getTime();
const today = () => localValue(Date.now()).slice(0, 10);

function fieldHtml(field) {
  if (field.type === 'select') return `<label>${field.label}<select name="${field.name}">${field.options.map(([value,label]) => `<option value="${value}" ${value===field.value?'selected':''}>${label}</option>`).join('')}</select></label>`;
  if (field.type === 'checkbox') return `<label class="check-field"><input type="checkbox" name="${field.name}" ${field.value?'checked':''}><span>${field.label}</span></label>`;
  return `<label>${field.label}<input name="${field.name}" type="${field.type || 'text'}" value="${field.value ?? ''}" ${field.required===false?'':'required'} ${field.min?`min="${field.min}"`:''} ${field.max?`max="${field.max}"`:''}></label>`;
}

function openEditor(title, fields, onSubmit) {
  const layer = document.querySelector('#modal-layer');
  layer.innerHTML = `<div class="modal-backdrop"><form class="editor-modal"><header><h2>${title}</h2><button type="button" class="icon-button" data-close aria-label="Kapat">×</button></header><div class="form-grid">${fields.map(fieldHtml).join('')}</div><p class="form-error" hidden></p><footer><button type="button" class="secondary" data-close>Vazgeç</button><button type="submit">Kaydet</button></footer></form></div>`;
  const close = () => { layer.innerHTML = ''; };
  layer.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', close));
  layer.querySelector('.editor-modal').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, data = new FormData(form), submit = form.querySelector('[type=submit]'), error = form.querySelector('.form-error');
    submit.disabled = true; error.hidden = true;
    try { await onSubmit(data); close(); }
    catch (failure) { error.textContent = String(failure); error.hidden = false; submit.disabled = false; }
  });
}

function mealItems(value) {
  return JSON.stringify(value.split(',').map(name => name.trim()).filter(Boolean).map(name => ({name, quantity:1, unit:'PORTION', category:'OTHER'})));
}
function mealNames(raw) { try { return JSON.parse(raw).map(item => item.name).join(', '); } catch { return ''; } }

export function bindRecordActions(snapshot) {
  document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', async () => {
    const action = button.dataset.action, id = Number(button.dataset.id || 0);
    if (action === 'start-sleep') return api.startSleep(Date.now());
    if (action === 'finish-sleep') return api.finishSleep(Date.now());
    if (action === 'delete') {
      if (confirm('Bu kayıt silinsin mi?')) await api.deleteRecord(button.dataset.kind, id);
      return;
    }
    if (action === 'add-water') return api.saveWater({id:0, amountMl:Number(button.dataset.amount), timestampMillis:Date.now(), updatedAtMillis:0});
    if (action === 'toggle-agenda') {
      const item = snapshot.agenda.find(record => record.id === id);
      return api.saveAgenda({...item, completed:!item.completed, notDone:false});
    }
    if (action === 'water-goal') return openEditor('Su hedefi', [{name:'goal',label:'Günlük hedef (ml)',type:'number',value:snapshot.waterGoalMl,min:500,max:10000}], data => api.setWaterGoal(Number(data.get('goal'))));
    if (action === 'add-sleep' || action === 'edit-sleep') {
      const item = snapshot.sleeps.find(record => record.id === id) || {id:0,startMillis:Date.now()-8*3600000,endMillis:Date.now()};
      return openEditor(item.id?'Uyku kaydını düzenle':'Uyku kaydı ekle', [
        {name:'start',label:'Yatma',type:'datetime-local',value:localValue(item.startMillis)},
        {name:'end',label:'Uyanma',type:'datetime-local',value:localValue(item.endMillis)},
      ], data => api.saveSleep({...item,startMillis:millis(data.get('start')),endMillis:millis(data.get('end'))}));
    }
    if (action === 'custom-water' || action === 'edit-water') {
      const item = snapshot.water.find(record => record.id === id) || {id:0,amountMl:250,timestampMillis:Date.now(),updatedAtMillis:0};
      return openEditor(item.id?'Su kaydını düzenle':'Su ekle', [
        {name:'amount',label:'Miktar (ml)',type:'number',value:item.amountMl,min:10,max:5000},
        {name:'time',label:'Tarih ve saat',type:'datetime-local',value:localValue(item.timestampMillis)},
      ], data => api.saveWater({...item,amountMl:Number(data.get('amount')),timestampMillis:millis(data.get('time'))}));
    }
    if (action === 'add-meal' || action === 'edit-meal') {
      const item = snapshot.meals.find(record => record.id === id) || {id:0,type:'BREAKFAST',startMillis:Date.now()-1200000,endMillis:Date.now(),itemsJson:'[]',quick:false,updatedAtMillis:0};
      return openEditor(item.id?'Öğünü düzenle':'Öğün ekle', [
        {name:'type',label:'Öğün',type:'select',value:item.type,options:[['BREAKFAST','Kahvaltı'],['LUNCH','Öğle'],['DINNER','Akşam'],['SNACK','Atıştırma']]},
        {name:'foods',label:'Yiyecekler (virgülle ayır)',value:mealNames(item.itemsJson)},
        {name:'start',label:'Başlangıç',type:'datetime-local',value:localValue(item.startMillis)},
        {name:'end',label:'Bitiş',type:'datetime-local',value:localValue(item.endMillis)},
      ], data => api.saveMeal({...item,type:data.get('type'),itemsJson:mealItems(data.get('foods')),startMillis:millis(data.get('start')),endMillis:millis(data.get('end'))}));
    }
    if (action === 'add-agenda' || action === 'edit-agenda') {
      const item = snapshot.agenda.find(record => record.id === id) || {id:0,day:today(),text:'',task:true,completed:false,notDone:false,repeatDaily:false,generalGoal:false,streak:0,notifyEnabled:false,notifyHour:20,notifyMinute:0,category:'',subcategory:'',updatedAtMillis:0};
      return openEditor(item.id?'Ajanda kaydını düzenle':'Ajandaya ekle', [
        {name:'text',label:'Not veya görev',value:item.text}, {name:'day',label:'Tarih',type:'date',value:item.day},
        {name:'kind',label:'Tür',type:'select',value:item.generalGoal?'goal':item.task?'task':'note',options:[['task','Görev'],['note','Not'],['goal','Genel hedef']]},
        {name:'category',label:'Kategori',value:item.category || ''}, {name:'subcategory',label:'Alt kategori',value:item.subcategory || ''},
        {name:'repeat',label:'Her gün tekrarla',type:'checkbox',value:item.repeatDaily},
        {name:'notify',label:'Bildirim',type:'checkbox',value:item.notifyEnabled},
        {name:'notifyTime',label:'Bildirim saati',type:'time',value:`${String(item.notifyHour).padStart(2,'0')}:${String(item.notifyMinute).padStart(2,'0')}`},
      ], data => { const kind=data.get('kind'), parts=String(data.get('notifyTime')).split(':'); return api.saveAgenda({...item,text:data.get('text'),day:data.get('day'),task:kind!=='note',generalGoal:kind==='goal',category:data.get('category'),subcategory:data.get('subcategory'),repeatDaily:data.has('repeat')&&kind!=='goal',notifyEnabled:data.has('notify')&&kind!=='goal',notifyHour:Number(parts[0]),notifyMinute:Number(parts[1])}); });
    }
    if (action === 'add-private' || action === 'edit-private') {
      const item = snapshot.privateActivities.find(record => record.id === id) || {id:0,startMillis:Date.now()-600000,endMillis:Date.now(),updatedAtMillis:0};
      return openEditor(item.id?'Özel kaydı düzenle':'Özel kayıt ekle', [
        {name:'start',label:'Başlangıç',type:'datetime-local',value:localValue(item.startMillis)},
        {name:'end',label:'Bitiş',type:'datetime-local',value:localValue(item.endMillis || Date.now())},
      ], data => { const end=millis(data.get('end')); return api.savePrivate({...item,startMillis:millis(data.get('start')),endMillis:end}); });
    }
  }));
}
