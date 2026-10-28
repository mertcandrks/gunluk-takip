import test from 'node:test';
import assert from 'node:assert/strict';
import { renderAgendaScreen } from './agenda.js';

test('recurring tasks show the selected daily count and remain available to increment', () => {
  const date = new Date();
  const day = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  const task={id:7,day,text:'Brush teeth',task:true,repeatDaily:true,completed:true,dailyCountsJson:JSON.stringify({[day]:2})};
  const html=renderAgendaScreen({agenda:[task]});
  assert.match(html,/Günlük tekrarlar/);
  assert.match(html,/Toplam yapılan gün: 1/);
  assert.match(html,/aria-label="Seçili gün 2 kez">2<\/span>/);
  assert.match(html,/data-agenda-action="increment" data-id="7"><i data-lucide="Check"><\/i>Yaptım<\/button>/);
  assert.match(html,/data-agenda-action="decrement"/);
  const tomorrow=renderAgendaScreen({agenda:[{...task,dailyCountsJson:'{"2020-01-01":4}'}]});
  assert.match(tomorrow,/aria-label="Seçili gün 0 kez">0<\/span>/);
  assert.doesNotMatch(tomorrow,/data-agenda-action="decrement"/);
  assert.match(tomorrow,/Toplam yapılan gün: 1/);
  const history=renderAgendaScreen({agenda:[{...task,dailyCountsJson:JSON.stringify({[day]:3,'2020-01-01':5,'2020-01-02':0})}]});
  assert.match(history,/Toplam yapılan gün: 2/);
});

test('ordinary tasks expose the subtask editor without existing parts', () => {
  const date = new Date();
  const day = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
  const task = { id: 1, day, text: 'Daily task', task: true };
  for (const state of [{}, {completed:true}, {notDone:true}, {generalGoal:true}]) {
    assert.match(renderAgendaScreen({agenda:[{...task,...state}]}), /data-agenda-action="parts" data-id="1"/);
  }
  for (const state of [{task:false}, {repeatDaily:true}]) {
    assert.doesNotMatch(renderAgendaScreen({agenda:[{...task,...state}]}), /data-agenda-action="parts" data-id="1"/);
  }
});
