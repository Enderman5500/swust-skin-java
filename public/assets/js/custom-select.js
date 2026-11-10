/**
 * Enhances the labeled list-page selects (page size, filters, sort) with the
 * same animated dropdown surface as the header menus, while keeping the native
 * <select> as the source of truth for existing change listeners and values.
 */
const SELECTOR = '.pager-size > select, .select-box > select';
let scanQueued = false;

function optionText(option) {
  return String(option.textContent || '').trim();
}

function enhance(select) {
  if (select.dataset.customSelect === 'true' || select.multiple) return;
  select.dataset.customSelect = 'true';

  const parent = select.parentNode;
  const label = select.closest('.pager-size, .select-box');
  const wrapper = document.createElement('span');
  wrapper.className = 'custom-select';
  parent.insertBefore(wrapper, select);
  wrapper.appendChild(select);

  select.hidden = true;
  select.tabIndex = -1;
  select.setAttribute('aria-hidden', 'true');
  label?.classList.add('has-custom-select');

  const trigger = document.createElement('button');
  trigger.type = 'button';
  trigger.className = 'custom-select-trigger';
  trigger.setAttribute('aria-haspopup', 'listbox');
  trigger.setAttribute('aria-expanded', 'false');
  const ariaLabel = select.getAttribute('aria-label');
  if (ariaLabel) trigger.setAttribute('aria-label', ariaLabel);

  const valueEl = document.createElement('span');
  valueEl.className = 'custom-select-value';
  const chevron = document.createElement('span');
  chevron.className = 'custom-select-chevron';
  chevron.setAttribute('aria-hidden', 'true');
  trigger.append(valueEl, chevron);

  const panel = document.createElement('div');
  panel.className = 'custom-select-panel dropdown-panel';
  panel.id = `custom-select-${Math.random().toString(36).slice(2, 9)}`;
  panel.setAttribute('role', 'listbox');
  panel.setAttribute('aria-hidden', 'true');
  trigger.setAttribute('aria-controls', panel.id);

  wrapper.append(trigger, panel);

  let open = false;

  const syncLabel = () => {
    const option = select.selectedOptions[0] || select.options[select.selectedIndex];
    valueEl.textContent = option ? optionText(option) : '';
    trigger.classList.toggle('is-placeholder', !option || option.value === '');
  };

  const renderOptions = () => {
    panel.replaceChildren();
    for (const option of [...select.options]) {
      if (option.hidden || option.disabled) continue;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'custom-select-option';
      button.dataset.value = option.value;
      button.setAttribute('role', 'option');
      button.setAttribute('aria-selected', String(option.selected));
      if (option.selected) button.classList.add('is-selected');
      const text = document.createElement('span');
      text.className = 'custom-select-option-label';
      text.textContent = optionText(option);
      button.appendChild(text);
      button.addEventListener('click', () => {
        select.value = option.value;
        syncLabel();
        setOpen(false);
        trigger.focus();
        select.dispatchEvent(new Event('change', { bubbles: true }));
      });
      panel.appendChild(button);
    }
    syncLabel();
  };

  const closeOthers = () => {
    document.querySelectorAll('.custom-select.is-open').forEach((other) => {
      if (other !== wrapper) other.__customSelectClose?.();
    });
  };

  const setOpen = (next) => {
    if (open === next) return;
    open = next;
    trigger.setAttribute('aria-expanded', String(open));
    panel.setAttribute('aria-hidden', String(!open));
    if (open) {
      renderOptions();
      closeOthers();
      const rect = trigger.getBoundingClientRect();
      const panelHeight = panel.offsetHeight;
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;
      wrapper.classList.toggle(
        'is-up',
        spaceBelow < panelHeight + 20 && spaceAbove > panelHeight
      );
      wrapper.classList.add('is-open');
      panel.classList.add('is-open');
    } else {
      wrapper.classList.remove('is-open');
      panel.classList.remove('is-open');
    }
  };
  wrapper.__customSelectClose = () => setOpen(false);

  trigger.addEventListener('click', () => {
    setOpen(!open);
  });

  trigger.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
      const options = [...panel.querySelectorAll('.custom-select-option')];
      const selected = options.findIndex((item) => item.classList.contains('is-selected'));
      options[selected >= 0 ? selected : 0]?.focus();
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  });

  panel.addEventListener('keydown', (event) => {
    const options = [...panel.querySelectorAll('.custom-select-option')];
    const index = options.indexOf(document.activeElement);
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      options[Math.min(options.length - 1, index + 1)]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      options[Math.max(0, index - 1)]?.focus();
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      document.activeElement?.click();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
      trigger.focus();
    }
  });

  select.addEventListener('change', syncLabel);
  select.addEventListener('input', syncLabel);
  new MutationObserver(() => renderOptions()).observe(select, {
    childList: true,
    subtree: true,
    characterData: true,
  });

  label?.addEventListener('click', (event) => {
    if (event.target.closest('.custom-select')) return;
    event.preventDefault();
    trigger.click();
  });

  renderOptions();
}

function scan() {
  scanQueued = false;
  document.querySelectorAll(SELECTOR).forEach((select) => {
    if (select.dataset.customSelect !== 'true') enhance(select);
  });
}

function scheduleScan() {
  if (scanQueued) return;
  scanQueued = true;
  requestAnimationFrame(scan);
}

export function initCustomSelects() {
  scan();
  new MutationObserver(scheduleScan).observe(document.body, {
    childList: true,
    subtree: true,
  });
  document.addEventListener('click', (event) => {
    document.querySelectorAll('.custom-select.is-open').forEach((wrapper) => {
      if (!wrapper.contains(event.target)) wrapper.__customSelectClose?.();
    });
  });
  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    document.querySelectorAll('.custom-select.is-open').forEach((wrapper) => {
      wrapper.__customSelectClose?.();
    });
  });
}
