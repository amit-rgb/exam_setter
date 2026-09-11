(function () {
  const INGEST_LEVEL_ID = 'ingestLevel';
  const BLUEPRINT_LEVEL_ID = 'blueprintLevels';

  function normalizeLevels(value) {
    if (Array.isArray(value)) {
      return [...new Set(value.map(v => String(v || '').trim().toUpperCase()).filter(Boolean))];
    }
    return [...new Set(String(value || '').split(',').map(v => v.trim().toUpperCase()).filter(Boolean))];
  }

  function makeIngestLevelMultiSelect() {
    const select = document.getElementById(INGEST_LEVEL_ID);
    if (!select || select.multiple) return;

    select.multiple = true;
    select.size = 6;
    select.setAttribute('aria-label', 'Target levels, select one or more');
    select.setAttribute('aria-describedby', 'ingestLevelHelp');
    select.classList.add('multi-level-select');

    // Keep app.js backward-compatible: its existing l.value read/write now
    // represents all selected levels as a comma-separated value.
    Object.defineProperty(select, 'value', {
      configurable: true,
      get() {
        return [...select.selectedOptions]
          .map(option => option.value)
          .filter(value => value && value !== '__CREATE_LEVEL__')
          .join(',');
      },
      set(value) {
        const wanted = new Set(normalizeLevels(value));
        [...select.options].forEach(option => {
          option.selected = wanted.has(String(option.value).trim().toUpperCase());
        });
      }
    });
  }

  function preserveTargetLevelsInContext() {
    const originalSetItem = sessionStorage.setItem.bind(sessionStorage);
    sessionStorage.setItem = function (key, value) {
      if (key === 'examContext') {
        try {
          const context = JSON.parse(value || '{}');
          const targetLevels = normalizeLevels(context.targetLevels || context.level);
          if (targetLevels.length) {
            context.targetLevels = targetLevels;
            context.level = targetLevels.join(',');
          }
          value = JSON.stringify(context);
        } catch (_) {
          // Preserve the original storage operation if the value is not JSON.
        }
      }
      return originalSetItem(key, value);
    };
  }

  function selectAllBlueprintLevels(levels) {
    const select = document.getElementById(BLUEPRINT_LEVEL_ID);
    if (!select) return;
    const wanted = new Set(normalizeLevels(levels));
    [...select.options].forEach(option => {
      option.selected = wanted.has(String(option.value).trim().toUpperCase());
    });
  }

  makeIngestLevelMultiSelect();
  preserveTargetLevelsInContext();

  if (typeof window.initIngestPage === 'function') {
    const originalInitIngestPage = window.initIngestPage;
    window.initIngestPage = function () {
      return originalInitIngestPage.apply(this, arguments);
    };
  }

  if (typeof window.initBlueprintPage === 'function') {
    const originalInitBlueprintPage = window.initBlueprintPage;
    window.initBlueprintPage = function () {
      const context = typeof readContext === 'function' ? readContext() : {};
      const result = originalInitBlueprintPage.apply(this, arguments);
      selectAllBlueprintLevels(context.targetLevels || context.level);
      return result;
    };
  }
})();