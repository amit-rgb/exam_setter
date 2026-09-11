(function(){
  const SUBJECT_KEY='customSubjects';
  const LEVEL_KEY='customLevels';
  const CREATE_SUBJECT='__CREATE_SUBJECT__';
  const CREATE_LEVEL='__CREATE_LEVEL__';
  const read=(key)=>{try{return JSON.parse(sessionStorage.getItem(key)||'[]')}catch(e){return[]}};
  const write=(key,value)=>sessionStorage.setItem(key,JSON.stringify(value));
  const slug=(value)=>value.trim().toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'').slice(0,60)||('custom_'+Date.now());
  function addOption(select,label,value,selected){const o=document.createElement('option');o.value=value;o.textContent=label;o.dataset.custom='true';o.selected=!!selected;select.appendChild(o);return o;}
  function prepare(select,type){
    if(!select)return;
    const key=type==='subject'?SUBJECT_KEY:LEVEL_KEY;
    const create=type==='subject'?CREATE_SUBJECT:CREATE_LEVEL;
    read(key).forEach(item=>{if(![...select.options].some(o=>o.value===item.value))addOption(select,item.label,item.value,false)});
    if(![...select.options].some(o=>o.value===create))addOption(select,type==='subject'?'＋ Create new subject…':'＋ Create new target level…',create,false);
    select.dataset.createOption=create;
    select.addEventListener('change',function(){
      if(select.multiple){
        const picked=[...select.selectedOptions].filter(o=>o.value===create);
        if(picked.length){
          const label=window.prompt(type==='subject'?'Enter the new subject name:':'Enter the new target level name:','');
          picked.forEach(o=>o.selected=false);
          if(label&&label.trim()){
            const clean=label.trim();
            const value=type==='subject'?slug(clean):('CUSTOM_'+slug(clean).toUpperCase());
            const existing=[...select.options].find(o=>o.value===value);
            const option=existing||addOption(select,clean,value,false);
            option.textContent=clean;option.selected=true;option.dataset.custom='true';
            const items=read(key).filter(x=>x.value!==value);items.push({label:clean,value:value});write(key,items);
          }
          select.dispatchEvent(new Event('change',{bubbles:true}));
        }
      }else if(select.value===create){
        const label=window.prompt(type==='subject'?'Enter the new subject name:':'Enter the new target level name:','');
        if(label&&label.trim()){
          const clean=label.trim();
          const value=type==='subject'?slug(clean):('CUSTOM_'+slug(clean).toUpperCase());
          let option=[...select.options].find(o=>o.value===value);
          if(!option)option=addOption(select,clean,value,true);else option.selected=true;
          option.textContent=clean;option.dataset.custom='true';
          const items=read(key).filter(x=>x.value!==value);items.push({label:clean,value:value});write(key,items);
        }else{
          select.selectedIndex=0;
        }
      }
    });
  }
  document.addEventListener('DOMContentLoaded',function(){
    prepare(document.getElementById('ingestSubject'),'subject');
    prepare(document.getElementById('ingestLevel'),'level');
    prepare(document.getElementById('blueprintSubject'),'subject');
    prepare(document.getElementById('blueprintLevels'),'level');
  });
})();