const subjects=[['English','english'],['Hindi','hindi'],['Mathematics','mathematics'],['Physics','physics'],['Chemistry','chemistry'],['Biology','biology'],['Science','science'],['Computer Science','computer_science'],['Social Science','social_science'],['Economics','economics']];
const levels=[['Class 3','CLASS_3'],['Class 4','CLASS_4'],['Class 5','CLASS_5'],['Class 6','CLASS_6'],['Class 7','CLASS_7'],['Class 8','CLASS_8'],['Class 9','CLASS_9'],['Class 10','CLASS_10'],['Class 11','CLASS_11'],['Class 12','CLASS_12']];
const esc=v=>String(v??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\"/g,'&quot;').replace(/'/g,'&#039;');
const readContext=()=>{try{return JSON.parse(sessionStorage.getItem('examContext')||'{}')}catch{return {}}};
const readPaper=()=>{try{return JSON.parse(sessionStorage.getItem('examPaper')||'null')}catch{return null}};
const savePaper=p=>sessionStorage.setItem('examPaper',JSON.stringify(p));
const apiJson=async r=>{const d=await r.json().catch(()=>({}));if(!r.ok){console.error('API request failed',r.status,d);throw new Error((d.message||d.error||d.errorMessage||'Request failed')+' | Diagnostics: /diagnostics.html');}return d};
function alertBox(msg,error=false){const b=document.getElementById('alertBox');if(!b)return;b.textContent=msg;b.className=`mb-5 rounded-2xl border px-4 py-3 text-sm font-semibold ${error?'border-rose-200 bg-rose-50 text-rose-700':'border-emerald-200 bg-emerald-50 text-emerald-700'}`;b.classList.remove('hidden');window.scrollTo({top:0,behavior:'smooth'})}
function navigateWorkflow(t){location.href=t;return false}
function initMobileWorkflow(){document.getElementById('mobileWorkflow')?.addEventListener('click',e=>{if(e.target.closest('a'))e.currentTarget.open=false})}
function setLevels(el,values){const s=new Set((values||[]).map(x=>String(x).toUpperCase()));[...el.options].forEach(o=>o.selected=s.has(o.value.toUpperCase()))}
function initIngestPage(){
 initMobileWorkflow();
 const subject=document.getElementById('ingestSubject'), levelsEl=document.getElementById('ingestLevel'), type=document.getElementById('ingestSourceType');
 if(!subject)return;
 const ctx=readContext();
 if(ctx.subject)subject.value=ctx.subject;
 if(ctx.targetLevels||ctx.level)setLevels(levelsEl,ctx.targetLevels||String(ctx.level).split(','));
 const pyqFields=document.getElementById('pyqFields');
 const teacherFields=document.getElementById('genericFields');
 const help=document.getElementById('sourceHelp');
 function refresh(){
   const v=type.value;
   const isPyq=v==='PREVIOUS_YEAR_QUESTION_PAPER';
   if(pyqFields)pyqFields.classList.toggle('hidden',!isPyq);
   if(teacherFields)teacherFields.classList.toggle('hidden',isPyq);
   const messages={
     SYLLABUS:'Defines the curriculum scope used while generating questions.',
     TEACHER_NOTES:'Teacher or coaching notes provide factual and explanatory knowledge.',
     PREVIOUS_YEAR_QUESTION_PAPER:'Previous-year papers provide examination pattern and coverage evidence. Generated questions will not copy them.',
     OTHER:'Other reference material provides additional factual knowledge.'
   };
   if(help)help.textContent=messages[v]||'';
 }
 type.addEventListener('change',refresh); refresh();
 document.getElementById('ingestForm').addEventListener('submit',async function(e){
   e.preventDefault();
   const isPyq=type.value==='PREVIOUS_YEAR_QUESTION_PAPER'; const file=document.getElementById(isPyq?'ingestFilePyq':'ingestFile').files[0], button=document.getElementById('ingestBtn');
   const selected=[...levelsEl.selectedOptions].map(o=>o.value).filter(Boolean);
   if(!subject.value){alertBox('Select a subject.',true);return}
   if(!selected.length){alertBox('Select at least one target level.',true);return}
   if(!file){alertBox('Select a PDF first.',true);return}
   button.disabled=true; button.textContent='Indexing…';
   try{
     let response;
     if(isPyq){
       const examId=document.getElementById('pyqExamId').value.trim();
       const year=Number(document.getElementById('pyqYear').value);
       if(!examId){alertBox('Exam ID is required for a previous-year paper.',true);return}
       if(!year||year<1900||year>2100){alertBox('Enter a valid previous-year paper year.',true);return}
       const fd=new FormData();
       fd.append('file',file);fd.append('examId',examId);fd.append('year',String(year));
       fd.append('subject',subject.value);fd.append('targetLevel',selected.join(','));
       fd.append('paperName',document.getElementById('pyqPaperName').value.trim());
       response=await fetch('/api/ingest/pyq',{method:'POST',body:fd});
     }else{
       const map={SYLLABUS:'SYLLABUS',TEACHER_NOTES:'STUDY_NOTES',OTHER:'REFERENCE'};
       const fd=new FormData();
       fd.append('file',file);fd.append('subject',subject.value);fd.append('targetLevel',selected.join(','));
       fd.append('sourceType',map[type.value]); response=await fetch('/api/ingest/pdf',{method:'POST',body:fd});
     }
     const d=await apiJson(response);
     sessionStorage.setItem('examContext',JSON.stringify({...readContext(),subject:subject.value,targetLevels:selected,level:selected.join(','),sourceType:type.value}));
     alertBox(type.value==='PREVIOUS_YEAR_QUESTION_PAPER'
       ? 'Previous-year paper indexed successfully. You can now select it as a generation source.'
       : 'Source indexed successfully. You can now select it as a generation source.');
   }catch(x){alertBox(x.message,true)}
   finally{button.disabled=false;button.textContent='Upload & Index →'}
 });
}
async function getProfiles(){return apiJson(await fetch('/api/exam-profiles'))}
function profileLevelValues(p){try{const v=JSON.parse(p.targetLevelsJson||'[]');return Array.isArray(v)?v.map(x=>String(x).toUpperCase()):[]}catch{return[]}}
function profileMatches(p,subject,levelValues,source){const pSource=String(p.knowledgeSource||'NCERT').toUpperCase(),pLevels=profileLevelValues(p);return pSource===String(source||'NCERT').toUpperCase()&&(!p.subject||String(p.subject).toLowerCase()===String(subject||'').toLowerCase())&&(levelValues.length===0||(pLevels.length===levelValues.length&&levelValues.every(x=>pLevels.includes(String(x).toUpperCase()))))}
async function getBooks(subject,level,version){const q=new URLSearchParams({subject:subject||'',targetLevel:level||''});if(version)q.set('corpusVersion',version);return apiJson(await fetch('/api/admin/ncert/catalog/books?'+q))}
async function getChapters(book,level,version){if(!book)return[];const q=new URLSearchParams({bookCode:book,targetLevel:level||''});if(version)q.set('corpusVersion',version);return apiJson(await fetch('/api/admin/ncert/catalog/chapters?'+q))}
async function initBlueprintPage(){
 initMobileWorkflow();
 const form=document.getElementById('assembleForm'); if(!form)return;
 const ctx=readContext(), subject=document.getElementById('blueprintSubject'), levelsEl=document.getElementById('blueprintLevels'), profile=document.getElementById('examProfile');
 if(ctx.subject)subject.value=ctx.subject;
 setLevels(levelsEl,ctx.targetLevels||ctx.level?.split(',')||[]);
 const container=document.getElementById('sectionsContainer'), mobile=document.getElementById('mobileSectionsContainer');
 const sourceIds=['SYLLABUS','TEACHER_NOTES','PREVIOUS_YEAR_QUESTION_PAPER','OTHER'];
 function selectedSources(){return sourceIds.filter(function(v){const e=document.getElementById('source-'+v);return e&&e.checked})}
 function applySourceContext(){const saved=ctx.knowledgeSources||[];sourceIds.forEach(function(v){const e=document.getElementById('source-'+v);if(e)e.checked=saved.includes(v)});if(!saved.length){const e=document.getElementById('source-TEACHER_NOTES');if(e)e.checked=true}}
 function add(name,type,count,marks,neg,diff,topic){
   name=name||'Section A: MCQs';type=type||'MCQ';count=count||3;marks=marks||4;neg=neg==null?1:neg;diff=diff||'EASY';topic=topic||'';
   const id='s'+Math.random().toString(36).slice(2);
   const row=document.createElement('div');row.dataset.id=id;row.className='grid grid-cols-12 gap-2 border-t border-slate-100 px-4 py-4 items-center';
   row.innerHTML='<input class="sec-name col-span-3 field rounded-xl border px-3 py-2.5 text-xs" value="'+esc(name)+'" required><select class="sec-type col-span-2 field rounded-xl border px-3 py-2.5 text-xs"><option>MCQ</option><option>NUMERICAL</option><option>ASSERTION_REASON</option><option>SHORT_ANSWER</option></select><input class="sec-count field rounded-xl border px-2 py-2.5 text-xs" type="number" min="1" value="'+count+'"><input class="sec-marks field rounded-xl border px-2 py-2.5 text-xs" type="number" min="1" value="'+marks+'"><input class="sec-neg field rounded-xl border px-2 py-2.5 text-xs" type="number" min="0" step=".25" value="'+neg+'"><select class="sec-diff field rounded-xl border px-2 py-2.5 text-xs"><option>EASY</option><option>MEDIUM</option><option>HARD</option></select><input class="sec-topic col-span-2 field rounded-xl border px-3 py-2.5 text-xs" placeholder="Topic" value="'+esc(topic)+'"><button type="button" class="remove rounded-xl border px-2 text-xs">✕</button>';
   row.querySelector('.sec-type').value=type;row.querySelector('.sec-diff').value=diff;
   row.querySelector('.remove').onclick=function(){row.remove();document.querySelector('[data-mobile-id="'+id+'"]')?.remove()};container.appendChild(row);
   const card=document.createElement('article');card.dataset.mobileId=id;card.className='mobile-section-card rounded-2xl border p-4';
   card.innerHTML='<div class="mb-3 flex justify-between"><b>'+esc(name)+'</b><button type="button" class="section-remove text-rose-600">Remove</button></div><input class="mobile-sec-name field w-full rounded-xl border px-3 py-3 text-sm mb-2" value="'+esc(name)+'"><select class="mobile-sec-type field w-full rounded-xl border px-3 py-3 text-sm mb-2"><option>MCQ</option><option>NUMERICAL</option><option>ASSERTION_REASON</option><option>SHORT_ANSWER</option></select><div class="grid grid-cols-2 gap-2"><input class="mobile-sec-count field rounded-xl border px-3 py-3" type="number" min="1" value="'+count+'"><input class="mobile-sec-marks field rounded-xl border px-3 py-3" type="number" min="1" value="'+marks+'"><input class="mobile-sec-neg field rounded-xl border px-3 py-3" type="number" min="0" step=".25" value="'+neg+'"><select class="mobile-sec-diff field rounded-xl border px-3 py-3"><option>EASY</option><option>MEDIUM</option><option>HARD</option></select></div><input class="mobile-sec-topic field w-full rounded-xl border px-3 py-3 mt-2" placeholder="Topic" value="'+esc(topic)+'">';
   card.querySelector('.mobile-sec-type').value=type;card.querySelector('.mobile-sec-diff').value=diff;
   const sync=function(){row.querySelector('.sec-name').value=card.querySelector('.mobile-sec-name').value;row.querySelector('.sec-type').value=card.querySelector('.mobile-sec-type').value;row.querySelector('.sec-count').value=card.querySelector('.mobile-sec-count').value;row.querySelector('.sec-marks').value=card.querySelector('.mobile-sec-marks').value;row.querySelector('.sec-neg').value=card.querySelector('.mobile-sec-neg').value;row.querySelector('.sec-diff').value=card.querySelector('.mobile-sec-diff').value;row.querySelector('.sec-topic').value=card.querySelector('.mobile-sec-topic').value};
   card.querySelectorAll('input,select').forEach(function(x){x.oninput=sync;x.onchange=sync});
   card.querySelector('.section-remove').onclick=function(){row.remove();card.remove()};mobile?.appendChild(card);sync();
 }
 add();add('Section B: Numerical','NUMERICAL',2,4,0,'MEDIUM');
 document.getElementById('addSection').onclick=function(){add()};
 applySourceContext();
 (async function(){
   try{
     const ps=await getProfiles();profile.innerHTML='<option value="">Manual blueprint</option>';
     ps.forEach(function(p){const o=document.createElement('option');o.value=p.examId;o.textContent=p.examName+' · '+p.examId;profile.appendChild(o)});
     if(ctx.examId){const selected=ps.find(function(p){return p.examId===ctx.examId});if(selected){profile.value=selected.examId;applyProfile(selected)}}
   }catch(e){alertBox('Exam profile service is unavailable. You can still generate using the selected knowledge sources.',true)}
 })();
 async function applyProfile(p){
   if(!p)return;
   subject.value=p.subject||subject.value;setLevels(levelsEl,profileLevelValues(p));
   document.getElementById('examTitle').value=p.paperName||p.examName||document.getElementById('examTitle').value;
   document.getElementById('examDuration').value=p.durationMinutes||45;
   sessionStorage.setItem('examContext',JSON.stringify({...readContext(),examId:p.examId,targetLevels:profileLevelValues(p)}));
 }
 profile.addEventListener('change',async function(){if(!profile.value)return;try{await applyProfile(await apiJson(await fetch('/api/exam-profiles/'+encodeURIComponent(profile.value))))}catch(e){alertBox(e.message,true)}});
 form.addEventListener('submit',async function(e){
   e.preventDefault();
   const sources=selectedSources();
   if(!sources.length){alertBox('Select at least one knowledge source: Syllabus, Teacher Notes, Previous Year Question Paper, or Other.',true);return}
   const rows=[...container.children].map(function(r){return{sectionName:r.querySelector('.sec-name').value,questionType:r.querySelector('.sec-type').value,questionCount:+r.querySelector('.sec-count').value,marksPerQuestion:+r.querySelector('.sec-marks').value,negativeMarks:+r.querySelector('.sec-neg').value,difficulty:r.querySelector('.sec-diff').value,topic:r.querySelector('.sec-topic').value||null}});
   if(!rows.length)return;
   const b=document.getElementById('assembleBtn');b.disabled=true;b.textContent='Generating questions…';
   const payload={examTitle:document.getElementById('examTitle').value,subject:subject.value,targetLevels:[...levelsEl.selectedOptions].map(o=>o.value),durationMinutes:+document.getElementById('examDuration').value,sections:rows,examId:profile.value||null,knowledgeSources:sources};
   try{savePaper(await apiJson(await fetch('/api/exam-papers/assemble',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)})));location.href='review.html'}
   catch(e){alertBox(e.message,true)}finally{b.disabled=false;b.textContent='Generate exam paper →'}
 });
}
function citations(q){if(Array.isArray(q.sourceCitations))return q.sourceCitations;try{return JSON.parse(q.sourceCitationsJson||'[]')}catch{return[]}}
function initReviewPage(){initMobileWorkflow();const root=document.getElementById('reviewSections');if(!root)return;const paper=readPaper();if(!paper){document.getElementById('paperTitle').textContent='Expert question review.';document.getElementById('paperMeta').textContent='Generate a paper from Blueprint to populate this workspace.';root.innerHTML='<section class="rounded-3xl border border-amber-200 bg-amber-50 p-8"><b>Review workspace is ready</b><p class="mt-2 text-sm">Complete Blueprint first.</p></section>';return}document.getElementById('paperTitle').textContent=paper.examTitle||'Exam Paper';render();function find(id){return(paper.sections||[]).flatMap(s=>s.questions||[]).find(q=>String(q.id)===String(id))}function render(){root.innerHTML=(paper.sections||[]).map((s,si)=>`<section class="overflow-hidden rounded-3xl border bg-white shadow-card"><div class="border-b bg-slate-50 px-5 py-4"><b>Section ${si+1} · ${esc(s.sectionName)}</b></div><div class="divide-y">${(s.questions||[]).map((q,i)=>{const inc=q.includedInPaper===true,cs=citations(q);return `<article data-qid="${q.id}" data-included="${inc}" class="question-card p-5"><div class="flex justify-between gap-3"><div><div class="text-[10px] font-black text-slate-400">Question ${i+1} · ${esc(q.moderationStatus||'PENDING_REVIEW')}</div><div class="mt-2 text-sm font-extrabold">${esc(q.questionText)}</div></div><div class="flex gap-2"><button class="in rounded-xl px-3 py-2 text-xs">✓ Opt in</button><button class="out rounded-xl px-3 py-2 text-xs">✕ Opt out</button></div></div><div class="mt-3 flex gap-2"><button class="approve rounded-xl bg-emerald-50 px-3 py-2 text-xs text-emerald-700">Approve</button><button class="reject rounded-xl bg-rose-50 px-3 py-2 text-xs text-rose-700">Reject</button></div><details class="mt-3 rounded-2xl bg-slate-50 p-4 text-xs leading-6"><summary class="cursor-pointer font-bold text-indigo-600">Details</summary><div class="mt-2"><b>Answer:</b> ${esc(q.correctAnswer)}<br><b>Marks:</b> ${q.marks??''}<br><b>Difficulty:</b> ${esc(q.difficulty)}<br><b>Explanation:</b> ${esc(q.explanation)}${cs.length?`<br><b>Retrieval sources:</b><ul class="list-disc pl-5">${cs.map(x=>`<li>${esc(x)}</li>`).join('')}</ul>`:''}</div></details></article>`}).join('')}</div></section>`).join('');bind();updateSummary()}
 function bind(){root.querySelectorAll('.in,.out').forEach(b=>b.onclick=()=>{const c=b.closest('[data-qid]');c.dataset.included=b.classList.contains('in');find(c.dataset.qid).includedInPaper=c.dataset.included==='true';paint(c);updateSummary()});root.querySelectorAll('.approve,.reject').forEach(b=>b.onclick=async()=>{const q=find(b.closest('[data-qid]').dataset.qid);try{const d=await apiJson(await fetch('/api/moderation/review',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({questionId:q.id,reviewerId:sessionStorage.getItem('reviewerId')||'EXPERT',approved:b.classList.contains('approve'),comments:''})}));q.moderationStatus=d.moderationStatus;render()}catch(e){alertBox(e.message,true)}});root.querySelectorAll('[data-qid]').forEach(paint)}
 function paint(c){const inc=c.dataset.included==='true',q=find(c.dataset.qid);c.className=`question-card p-5 ${inc?'bg-emerald-50/40':'bg-rose-50/20'}`;c.querySelector('.in').className=`in rounded-xl px-3 py-2 text-xs ${inc?'bg-emerald-600 text-white':'border border-emerald-200 text-emerald-700'}`;c.querySelector('.out').className=`out rounded-xl px-3 py-2 text-xs ${!inc?'bg-rose-600 text-white':'border border-rose-200 text-rose-700'}`;c.querySelector('.approve').disabled=q.moderationStatus==='APPROVED';c.querySelector('.reject').disabled=q.moderationStatus==='REJECTED'}
 document.getElementById('selectAll').onclick=()=>{root.querySelectorAll('[data-qid]').forEach(c=>{c.dataset.included='true';find(c.dataset.qid).includedInPaper=true;paint(c)});updateSummary()};document.getElementById('clearAll').onclick=()=>{root.querySelectorAll('[data-qid]').forEach(c=>{c.dataset.included='false';find(c.dataset.qid).includedInPaper=false;paint(c)});updateSummary()};
 async function save(sol,btn){const ids=[...root.querySelectorAll('[data-qid]')].filter(c=>c.dataset.included==='true').map(c=>c.dataset.qid);if(!ids.length){alertBox('Select at least one question.',true);return}btn.disabled=true;try{Object.assign(paper,await apiJson(await fetch(`/api/exam-papers/${paper.id}/selection`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({includedQuestionIds:ids})})));savePaper(paper);window.open(`/api/exam-papers/${paper.id}/export/pdf?includeSolutions=${sol}`,'_blank')}catch(e){alertBox(e.message,true)}finally{btn.disabled=false}}
 document.getElementById('saveStudent').onclick=e=>save(false,e.currentTarget);document.getElementById('saveSolutions').onclick=e=>save(true,e.currentTarget);
 function updateSummary(){const all=[...root.querySelectorAll('[data-qid]')],n=all.filter(c=>c.dataset.included==='true').length,a=all.filter(c=>find(c.dataset.qid).moderationStatus==='APPROVED').length;document.getElementById('summary').textContent=`${n} selected · ${a} approved`}
}
