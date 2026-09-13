function initNcertHomePage(){
  const main=document.querySelector('.app-main');
  const ingestSection=document.querySelector('.app-main > section.surface');
  if(!main||!ingestSection)return;
  const panel=document.createElement('section');
  panel.className='surface mb-6 overflow-hidden rounded-[28px] border border-indigo-100 bg-white shadow-sm';
  panel.innerHTML=`
    <div class="border-b border-slate-100 bg-gradient-to-r from-indigo-50 via-white to-cyan-50 px-6 py-6 md:px-8">
      <div class="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div class="mb-2 inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-white px-3 py-1 text-[11px] font-black uppercase tracking-wider text-indigo-700"><span>📚</span> NCERT textbook library</div>
          <h2 class="text-2xl font-black tracking-tight">Build an exam from NCERT</h2>
          <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-600">Use the indexed NCERT corpus as the authoritative knowledge source. Select the academic scope here, then continue to Blueprint for exam pattern and question generation.</p>
        </div>
        <a href="admin.html" class="shrink-0 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-extrabold text-slate-700 shadow-sm hover:border-indigo-200 hover:text-indigo-700">Corpus Control Center →</a>
      </div>
    </div>
    <div class="grid gap-6 p-6 md:p-8 lg:grid-cols-[1fr_320px]">
      <div>
        <div class="grid gap-4 md:grid-cols-2">
          <div><label for="ncertHomeSubject" class="mb-2 block text-xs font-bold text-slate-700">Subject</label><select id="ncertHomeSubject" class="field w-full rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-3 text-sm"><option value="">Select subject</option><option value="english">English</option><option value="hindi">Hindi</option><option value="mathematics">Mathematics</option><option value="physics">Physics</option><option value="chemistry">Chemistry</option><option value="biology">Biology</option><option value="science">Science</option><option value="computer_science">Computer Science</option><option value="social_science">Social Science</option><option value="economics">Economics</option></select></div>
          <div><label for="ncertHomeLevel" class="mb-2 block text-xs font-bold text-slate-700">Target level</label><select id="ncertHomeLevel" class="field w-full rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-3 text-sm"><option value="">Select class</option><option value="CLASS_3">Class 3</option><option value="CLASS_4">Class 4</option><option value="CLASS_5">Class 5</option><option value="CLASS_6">Class 6</option><option value="CLASS_7">Class 7</option><option value="CLASS_8">Class 8</option><option value="CLASS_9">Class 9</option><option value="CLASS_10">Class 10</option><option value="CLASS_11">Class 11</option><option value="CLASS_12">Class 12</option></select></div>
          <div><label for="ncertHomeBook" class="mb-2 block text-xs font-bold text-slate-700">NCERT book</label><select id="ncertHomeBook" disabled class="field w-full rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-3 text-sm"><option value="">Select subject and class first</option></select></div>
          <div><label for="ncertHomeChapter" class="mb-2 block text-xs font-bold text-slate-700">Chapter <span class="font-medium text-slate-400">· optional</span></label><select id="ncertHomeChapter" disabled class="field w-full rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-3 text-sm"><option value="">All chapters</option></select></div>
        </div>
        <div class="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center">
          <button id="ncertHomeContinue" type="button" disabled class="primary-action rounded-2xl bg-indigo-600 px-6 py-3.5 text-sm font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-40">Use NCERT & Continue →</button>
          <span id="ncertHomeMessage" class="text-xs text-slate-500">Select a subject and class to browse the indexed corpus.</span>
        </div>
      </div>
      <aside class="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
        <div class="mb-4 flex items-center justify-between"><div><div class="text-[10px] font-black uppercase tracking-wider text-slate-400">Corpus status</div><div class="mt-1 text-sm font-extrabold">NCERT 2026</div></div><span id="ncertHomeStatus" class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[10px] font-bold text-slate-500">Checking…</span></div>
        <div id="ncertHomeStats" class="grid grid-cols-3 gap-2"><div class="rounded-xl border bg-white p-3 text-center"><b class="block text-lg">—</b><span class="text-[10px] text-slate-400">Books</span></div><div class="rounded-xl border bg-white p-3 text-center"><b class="block text-lg">—</b><span class="text-[10px] text-slate-400">Chapters</span></div><div class="rounded-xl border bg-white p-3 text-center"><b class="block text-lg">—</b><span class="text-[10px] text-slate-400">Chunks</span></div></div>
        <p class="mt-4 text-[11px] leading-5 text-slate-500">Questions generated through this path are grounded in the selected NCERT scope. PYQs are used separately as examination-pattern evidence.</p>
      </aside>
    </div>`;
  main.insertBefore(panel,ingestSection);
  const subject=document.getElementById('ncertHomeSubject'),level=document.getElementById('ncertHomeLevel'),book=document.getElementById('ncertHomeBook'),chapter=document.getElementById('ncertHomeChapter'),button=document.getElementById('ncertHomeContinue'),message=document.getElementById('ncertHomeMessage');
  const version='2026';
  const get=async url=>{const r=await fetch(url);const d=await r.json().catch(()=>({}));if(!r.ok)throw new Error(d.message||d.error||'NCERT catalog request failed');return d};
  const setMessage=(text,error=false)=>{message.textContent=text;message.className=`text-xs ${error?'text-rose-600':'text-slate-500'}`};
  async function loadBooks(){
    book.innerHTML='<option value="">Loading books…</option>';book.disabled=true;chapter.innerHTML='<option value="">All chapters</option>';chapter.disabled=true;button.disabled=true;
    if(!subject.value||!level.value){book.innerHTML='<option value="">Select subject and class first</option>';setMessage('Select a subject and class to browse the indexed corpus.');return}
    try{const q=new URLSearchParams({subject:subject.value,targetLevel:level.value,corpusVersion:version});const books=await get('/api/admin/ncert/catalog/books?'+q);book.innerHTML='<option value="">All NCERT books</option>';books.forEach(x=>{const o=document.createElement('option');o.value=x.bookCode;o.textContent=`${x.bookTitle||x.bookCode} · ${x.chapters} chapters`;book.appendChild(o)});book.disabled=books.length===0;setMessage(books.length?`${books.length} NCERT book${books.length===1?'':'s'} available for this scope.`:'No indexed NCERT books found for this scope.',!books.length);if(books.length)button.disabled=false}catch(e){book.innerHTML='<option value="">NCERT catalog unavailable</option>';setMessage(e.message,true)}}
  async function loadChapters(){
    chapter.innerHTML='<option value="">Loading chapters…</option>';chapter.disabled=true;
    if(!book.value){chapter.innerHTML='<option value="">All chapters</option>';chapter.disabled=true;return}
    try{const q=new URLSearchParams({bookCode:book.value,targetLevel:level.value,corpusVersion:version});const chapters=await get('/api/admin/ncert/catalog/chapters?'+q);chapter.innerHTML='<option value="">All chapters</option>';chapters.forEach(x=>{const o=document.createElement('option');o.value=x.chapterNumber??'';o.textContent=`${x.chapterNumber??'—'} · ${x.chapterTitle||'Untitled'}`;chapter.appendChild(o)});chapter.disabled=false}catch(e){chapter.innerHTML='<option value="">Unable to load chapters</option>';setMessage(e.message,true)}}
  async function loadSummary(){
    try{const s=await get('/api/admin/ncert/catalog/summary?corpusVersion='+encodeURIComponent(version));const ready=Number(s.completed||0)>0;document.getElementById('ncertHomeStatus').textContent=ready?'Ready':'Not indexed';document.getElementById('ncertHomeStatus').className=`rounded-full border px-2.5 py-1 text-[10px] font-bold ${ready?'border-emerald-200 bg-emerald-50 text-emerald-700':'border-amber-200 bg-amber-50 text-amber-700'}`;document.getElementById('ncertHomeStats').innerHTML=`<div class="rounded-xl border bg-white p-3 text-center"><b class="block text-lg">${s.completed??0}</b><span class="text-[10px] text-slate-400">Documents</span></div><div class="rounded-xl border bg-white p-3 text-center"><b class="block text-lg">${s.documents??0}</b><span class="text-[10px] text-slate-400">Tracked</span></div><div class="rounded-xl border bg-white p-3 text-center"><b class="block text-lg">${s.chunks??0}</b><span class="text-[10px] text-slate-400">Chunks</span></div>`}catch(e){document.getElementById('ncertHomeStatus').textContent='Unavailable';setMessage('NCERT catalog is unavailable. Check the Control Center.',true)}}
  subject.addEventListener('change',loadBooks);level.addEventListener('change',loadBooks);book.addEventListener('change',loadChapters);
  button.addEventListener('click',()=>{sessionStorage.setItem('examContext',JSON.stringify({...readContext(),subject:subject.value,targetLevels:[level.value],level:level.value,knowledgeSource:'NCERT',corpusVersion:version,ncertBookCode:book.value||null,ncertChapterNumber:chapter.value?Number(chapter.value):null}));sessionStorage.removeItem('examPaper');location.href='blueprint.html'});
  loadSummary();
  const ctx=readContext();if(ctx.subject)subject.value=ctx.subject;if(ctx.targetLevels?.[0])level.value=ctx.targetLevels[0];else if(ctx.level)level.value=ctx.level;loadBooks();
  const original=ingestSection.querySelector('h2');if(original)original.textContent='Upload your own source PDF';const kicker=ingestSection.querySelector('.text-indigo-600');if(kicker)kicker.textContent='User source material';const copy=ingestSection.querySelector('p');if(copy)copy.textContent='Index a teacher-provided PDF when you need a source outside the NCERT corpus.';
}
