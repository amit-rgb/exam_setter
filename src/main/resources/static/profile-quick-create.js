(function(){
  const originalFetch=window.fetch.bind(window);
  const context=()=>{try{return JSON.parse(sessionStorage.getItem('examContext')||'{}')}catch{return{}}};
  const saveContext=value=>sessionStorage.setItem('examContext',JSON.stringify(value));
  const normalize=value=>String(value||'').trim().toUpperCase();
  const levelLabel=level=>String(level||'').replace(/^CLASS_/,'Class ');
  let profileRequest;

  async function ensureProfile(){
    if(profileRequest)return profileRequest;
    profileRequest=(async()=>{
      const ctx=context();
      const response=await originalFetch('/api/exam-profiles');
      if(!response.ok)return response;
      const profiles=await response.clone().json().catch(()=>[]);
      const subject=normalize(document.getElementById('blueprintSubject')?.value||ctx.subject);
      const levels=[...(document.getElementById('blueprintLevels')?.selectedOptions||[])].map(o=>normalize(o.value)).filter(Boolean);
      const source=normalize(document.getElementById('knowledgeSource')?.value||ctx.knowledgeSource||'NCERT');
      const matching=profiles.find(p=>normalize(p.knowledgeSource||'NCERT')===source&&(!p.subject||normalize(p.subject)===subject)&&(!p.targetLevelsJson||JSON.parse(p.targetLevelsJson||'[]').some(level=>levels.includes(normalize(level)))));
      if(matching){saveContext({...ctx,examId:matching.examId});return new Response(JSON.stringify(profiles),{status:200,headers:{'Content-Type':'application/json'}})}
      if(source!=='NCERT'||!subject||!levels.length)return new Response(JSON.stringify(profiles),{status:200,headers:{'Content-Type':'application/json'}});
      const slug=(subject+'-'+levels.join('-')).replace(/[^A-Z0-9_-]+/g,'-');
      const examId='NCERT-'+slug.slice(0,160);
      const body={examId,examName:`${subject.replaceAll('_',' ')} · ${levels.map(levelLabel).join(', ') } · NCERT`,paperName:'NCERT Assessment',subject:subject.toLowerCase(),knowledgeSource:'NCERT',corpusVersion:document.getElementById('corpusVersion')?.value||'2026',targetLevels:levels,questionCount:null,marksPerQuestion:null,durationMinutes:null,questionTypes:[],difficultyDistribution:{},questionTypeDistribution:{},topicDistribution:{},instructions:'Use the Blueprint sections as the examination pattern. NCERT is the authoritative knowledge source.',previousYearRange:null};
      const created=await originalFetch('/api/exam-profiles',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
      if(!created.ok)return new Response(JSON.stringify(profiles),{status:200,headers:{'Content-Type':'application/json'}});
      const profile=await created.json();
      saveContext({...ctx,examId:profile.examId});
      return new Response(JSON.stringify([...profiles,profile]),{status:200,headers:{'Content-Type':'application/json'}});
    })().finally(()=>{profileRequest=null});
    return profileRequest;
  }

  window.fetch=async function(input,init){
    const url=typeof input==='string'?input:(input&&input.url)||'';
    if(url==='/api/exam-profiles'&&(!init||!init.method||init.method.toUpperCase()==='GET'))return ensureProfile();
    return originalFetch(input,init);
  };
})();
