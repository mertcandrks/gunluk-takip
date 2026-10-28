const completedWords = new Set(['yaptim','ettim','gittim','geldim','aldim','odedim','bitirdim','tamamladim','okudum','ictim','yedim','uyudum','calistim','temizledim','toparladim','hazirladim','aradim','yazdim','izledim','yurudum','kostum','pisirdim','yikadim','duzenledim','basladim','kalktim','uyandim','gezdim','dinlendim','ogrendim','gonderdim']);
const plannedWords = new Set(['yapacagim','yapicam','yapcam','edecegim','edicem','gidecegim','gidicem','gitcem','gelecegim','gelicem','alacagim','alicam','alcam','odeyecegim','odicem','bitirecegim','bitiricem','okuyacagim','okuycam','calisacagim','calisicam','hazirlayacagim','hazirlicam','arayacagim','arayacam','yazacagim','yazicam','izleyecegim','izlicem','uyuyacagim','uyuycam','kosacagim','kosucam','temizleyecegim','temizlicem','toplayacagim','toplicam','gerekiyor','lazim','unutma','planliyorum','yapilacak','gidecem','yapacam']);
const weekdays = {pazartesi:1,sali:2,carsamba:3,persembe:4,cuma:5,cumartesi:6,pazar:0};

const normalize = value => value.toLocaleLowerCase('tr-TR').replaceAll('ı','i').replaceAll('ş','s').replaceAll('ğ','g').replaceAll('ç','c').replaceAll('ö','o').replaceAll('ü','u').normalize('NFD').replace(/\p{M}+/gu,'').replace(/([a-z])\1{2,}/g,'$1$1');
const isoDay = date => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
const addDays = (day, count) => { const value=new Date(`${day}T12:00:00`); value.setDate(value.getDate()+count); return isoDay(value); };

function distance(left, right, stopAfter) {
  let previous=Array.from({length:right.length+1},(_,i)=>i);
  for(let i=0;i<left.length;i++){
    const current=[i+1]; let rowMin=current[0];
    for(let j=0;j<right.length;j++){
      current[j+1]=Math.min(current[j]+1,previous[j+1]+1,previous[j]+(left[i]===right[j]?0:1));
      rowMin=Math.min(rowMin,current[j+1]);
    }
    if(rowMin>stopAfter) return stopAfter+1;
    previous=current;
  }
  return previous[right.length];
}
function fuzzy(value,target,max){ return value===target || value.length>=3 && Math.abs(value.length-target.length)<=max && distance(value,target,max)<=max; }
function hasDictionaryWord(words,dictionary){ return words.some(word=>[...dictionary].some(target=>fuzzy(word,target,target.length>=9?2:target.length>=5?1:0))); }
function words(value){ return normalize(value).split(/[^a-z0-9]+/).filter(word=>word.length>=3); }

function resolveDay(raw, baseDay) {
  const value=normalize(raw), tokens=value.split(/[^a-z0-9]+/).filter(Boolean);
  if(/\bobur\s+gun\b/.test(value)) return addDays(baseDay,2);
  if(tokens.some(token=>fuzzy(token,'bugun',1))) return baseDay;
  if(tokens.some(token=>fuzzy(token,'yarin',1))) return addDays(baseDay,1);
  if(tokens.some(token=>fuzzy(token,'dun',0))) return addDays(baseDay,-1);
  const numeric=value.match(/(?:^|\D)(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?(?!\d)/);
  if(numeric){ const base=new Date(`${baseDay}T12:00:00`), year=numeric[3]?(Number(numeric[3])<100?2000+Number(numeric[3]):Number(numeric[3])):base.getFullYear(), result=new Date(year,Number(numeric[2])-1,Number(numeric[1]),12); if(result.getDate()===Number(numeric[1])) return isoDay(result); }
  for(const [name,target] of Object.entries(weekdays)) if(tokens.some(token=>fuzzy(token,name,name.length>5?2:1))){ const date=new Date(`${baseDay}T12:00:00`), delta=(target-date.getDay()+7)%7; date.setDate(date.getDate()+delta); return isoDay(date); }
  return baseDay;
}
function resolveTime(raw) {
  const value=normalize(raw), clock=value.match(/(?:^|\D)(?:sa+t\s*)?([01]?\d|2[0-3])[:.]([0-5]\d)(?!\d)/), hour=value.match(/\bsa+t\s+([01]?\d|2[0-3])(?!\d)/);
  if(clock) return [Number(clock[1]),Number(clock[2]),true];
  if(hour) return [Number(hour[1]),0,true];
  const tokens=words(value);
  if(tokens.some(v=>fuzzy(v,'sabah',1))) return [9,0,true];
  if(tokens.some(v=>fuzzy(v,'oglen',1))) return [12,30,true];
  if(tokens.some(v=>fuzzy(v,'aksam',1))) return [19,0,true];
  if(tokens.some(v=>fuzzy(v,'gece',1))) return [22,0,true];
  return [20,0,false];
}
function cleanText(raw) {
  const value=raw.replace(/^\s*(?:[-*•]|\d+[.)])\s*/,'').replace(/\b(bugün|bugun|bugn|bgun|yarın|yarin|yarn|yrin|dün|dun|öbür\s+gün|obur\s+gun)\b/gi,' ').replace(/(?:^|\D)\d{1,2}[./-]\d{1,2}(?:[./-]\d{2,4})?(?!\d)/g,' ').replace(/(?:^|\D)(?:sa+t\s*)?([01]?\d|2[0-3])[:.]([0-5]\d)(?!\d)/gi,' ').replace(/\bsa+t\s+([01]?\d|2[0-3])\b/gi,' ').replace(/\b(sabah|öğlen|oglen|akşam|aksam|gece)\b/gi,' ').replace(/\s+/g,' ').trim().replace(/^[.,;:\-–\s]+|[.,;:\-–\s]+$/g,'');
  return value ? value[0].toLocaleUpperCase('tr-TR')+value.slice(1) : '';
}

export function parseAgendaText(input, baseDay, forcedIntent='auto') {
  let intent=forcedIntent;
  const prepared=input.replace(/(?<=\p{L})\s+(?=(?:bug[uü]n|yar[ıi]n|d[uü]n|[oö]b[uü]r\s+g[uü]n)\b)/giu,'. ');
  const result=[];
  for(let raw of prepared.split(/(?:[\r\n]+|(?<=[.!?;])\s+)/).map(v=>v.trim()).filter(Boolean)){
    const header=raw.split(':',2), normalizedHeader=normalize(header[0]).trim();
    if(['yaptiklarim','yapilanlar','tamamlananlar','tamamladiklarim','bugun yaptim'].some(v=>fuzzy(normalizedHeader,v,2))){ if(forcedIntent==='auto') intent='completed'; raw=header[1]||''; }
    else if(['yapacaklarim','yapilacaklar','planlarim','planlananlar','bugun yapacagim'].some(v=>fuzzy(normalizedHeader,v,2))){ if(forcedIntent==='auto') intent='planned'; raw=header[1]||''; }
    if(!raw.trim()) continue;
    const parts=raw.split(/,\s+|\s+(?:ve|sonra|ardindan|ardından)\s+/i).map(v=>v.trim()).filter(Boolean), clauses=parts.length>1&&parts.every(part=>hasDictionaryWord(words(part),completedWords)||hasDictionaryWord(words(part),plannedWords))?parts:[raw];
    for(const clause of clauses){
      const tokens=words(clause), detectedCompleted=hasDictionaryWord(tokens,completedWords), detectedPlanned=hasDictionaryWord(tokens,plannedWords), completed=intent==='completed'||intent==='auto'&&detectedCompleted&&!detectedPlanned, task=intent!=='auto'||detectedCompleted||detectedPlanned, [hour,minute,explicit]=resolveTime(clause), text=cleanText(clause);
      if(text) result.push({text,day:resolveDay(clause,baseDay),task,completed,notifyEnabled:task&&!completed&&explicit,notifyHour:hour,notifyMinute:minute,selected:true});
      if(result.length>=50) return result;
    }
  }
  return result;
}
