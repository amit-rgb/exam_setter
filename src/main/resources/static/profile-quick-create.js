(function(){
  const originalFetch=window.fetch.bind(window);
  const context=()=>{try{return JSON.parse(sessionStorage.getItem('examContext')||'{}')}catch{return{}}};
  const saveContext=value=>sessionStorage.setItem('examContext',JSON.stringify(value));
  const normalize=value=>String(value||'').trim().toUpperCase();
  const levelLabel=level=>String(level||'').replace(/^CLASS_/,'Class ');
  const profileLevels=profile=>{try{const parsed=JSON.parse(profile.targetLevelsJson||'[]');return Array.isArray(parsed)?parsed.map(normalize):[]}catch{return[]}};
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
      const matches=profiles.filter(p=>normalize(p.knowledgeSource||'NCERT')===source&&(!p.subject||normalize(p.subject)===subject));
      const exact=matches.find(p=>{
        const pLevels=profileLevels(p);
        return levels.length===0 || (pLevels.length===levels.length && levels.every(level=>pLevels.includes(level)));
      });
      const compatible=exact||matches.find(p=>{
        const pLevels=profileLevels(p);
        return !pLevels.length || levels.some(level=>pLevels.includes(level));
      });
      if(compatible){
        saveContext({...ctx,examId:compatible.examId,knowledgeSource:source,targetLevels:levels});
        return new Response(JSON.stringify(profiles),{status:200,headers:{'Content-Type':'application/json'}});
      }
      if(!subject||!levels.length)return new Response(JSON.stringify(profiles),{status:200,headers:{'Content-Type':'application/json'}});
      const slug=(subject+'-'+levels.join('-')).replace(/[^A-Z0-9_-]+/g,'-');
      const prefix=source==='NCERT'?'NCERT':source==='MIXED'?'MIXED':'UPLOAD';
      const examId=prefix+'-'+slug.slice(0,150);
      const readableSource=source==='NCERT'?'NCERT':source==='MIXED'?'Mixed':'Uploaded Sources';
      const body={
        examId,
        examName:`${subject.replaceAll('_',' ')} · ${levels.map(levelLabel).join(', ')} · ${readableSource}`,
        paperName:`${readableSource} Assessment`,
        subject:subject.toLowerCase(),
        knowledgeSource:source,
        corpusVersion:document.getElementById('corpusVersion')?.value||ctx.corpusVersion||'2026',
        targetLevels:levels,
        questionCount:null,
        marksPerQuestion:null,
        durationMinutes:null,
        questionTypes:[],
        difficultyDistribution:{},
        questionTypeDistribution:{},
        topicDistribution:{},
        instructions:source==='NCERT'
          ?'Use the Blueprint sections as the examination pattern. NCERT is the authoritative knowledge source.'
          :source==='MIXED'
            ?'Use the Blueprint sections as the examination pattern. NCERT is authoritative where present; classified uploaded study/reference material is supplementary.'
            :'Use the Blueprint sections as the examination pattern. Use classified uploaded study/reference material as knowledge. Question banks and syllabi are evidence only, not factual authority.',
        previousYearRange:null
      };
      if(source!=='NCERT'){body.ncertBookCode=null;body.ncertChapterNumber=null;}
      const created=await originalFetch('/api/exam-profiles',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
      if(!created.ok)return new Response(JSON.stringify(profiles),{status:200,headers:{'Content-Type':'application/json'}});
      const profile=await created.json();
      saveContext({...ctx,examId:profile.examId,knowledgeSource:source,targetLevels:levels});
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