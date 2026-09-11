(function(){
  const SUBJECT_KEY='customSubjects', LEVEL_KEY='customLevels';
  const CREATE_SUBJECT='__CREATE_SUBJECT__', CREATE_LEVEL='__CREATE_LEVEL__';
  const read=(key)=>{try{return JSON.parse(sessionStorage.getItem(key)||'[]')}catch(e){return[]}};
  const write=(key,value)=>sessionStorage.setItem(key,JSON.stringify(value));
  const slug=(value)=>value.trim().toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'').slice(0,60)||('custom_'+Date.now());
  function addOption(select,label,value,selected){const o=document.createElement('option');o.value=value;o.textContent=label;o.dataset.custom='true';o.selected=!!selected;select.appendChild(o);return o;}
  function prepare(select,type){
    if(!select)return;
    const key=type==='subject'?SUBJECT_KEY:LEVEL_KEY, create=type==='subject'?CREATE_SUBJECT:CREATE_LEVEL;
    read(key).forEach(item=>{if(![...select.options].some(o=>o.value===item.value))addOption(select,item.label,item.value,false)});
    if(![...select.options].some(o=>o.value===create))addOption(select,type==='subject'?'＋ Create new subject…':'＋ Create new target level…',create,false);
    select.addEventListener('change',function(){
      if(select.multiple){
        if(![...select.selectedOptions].some(o=>o.value===create))return;
        const label=window.prompt('Enter the new target level name:','');
        [...select.options].filter(o=>o.value===create).forEach(o=>o.selected=false);
        if(!label||!label.trim())return;
        const clean=label.trim(),value='CUSTOM_'+slug(clean).toUpperCase();
        let option=[...select.options].find(o=>o.value===value);
        if(!option)option=addOption(select,clean,value,false);else option.textContent=clean;
        option.selected=true;option.dataset.custom='true';
        const items=read(key).filter(x=>x.value!==value);items.push({label:clean,value:value});write(key,items);
      }else if(select.value===create){
        const label=window.prompt(type==='subject'?'Enter the new subject name:':'Enter the new target level name:','');
        if(!label||!label.trim()){select.selectedIndex=0;return;}
        const clean=label.trim(),value=type==='subject'?slug(clean):('CUSTOM_'+slug(clean).toUpperCase());
        let option=[...select.options].find(o=>o.value===value);
        if(!option)option=addOption(select,clean,value,true);else option.selected=true;
        option.textContent=clean;option.dataset.custom='true';
        const items=read(key).filter(x=>x.value!==value);items.push({label:clean,value:value});write(key,items);
      }
    });
  }
  prepare(document.getElementById('ingestSubject'),'subject');
  prepare(document.getElementById('ingestLevel'),'level');
  prepare(document.getElementById('blueprintSubject'),'subject');
  prepare(document.getElementById('blueprintLevels'),'level');
})();