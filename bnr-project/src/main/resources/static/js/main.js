// BNR Licensing Portal — Main JS

const API = {
  BASE: '',

  async request(method, url, body, token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    try {
      const res = await fetch(this.BASE + url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined
      });
      const data = await res.json();
      if (!res.ok) throw { status: res.status, message: data.message || 'Request failed' };
      return data;
    } catch (err) {
      throw err;
    }
  },

  get:  (url, token)        => API.request('GET',   url, null, token),
  post: (url, body, token)  => API.request('POST',  url, body, token),
  put:  (url, body, token)  => API.request('PUT',   url, body, token),
  patch:(url, body, token)  => API.request('PATCH', url, body, token),
};

// Token + user storage
const Auth = {
  save(data) {
    sessionStorage.setItem('bnr_token', data.token);
    sessionStorage.setItem('bnr_user', JSON.stringify({
      email: data.email, fullName: data.fullName, role: data.role
    }));
  },
  token()    { return sessionStorage.getItem('bnr_token'); },
  user()     { return JSON.parse(sessionStorage.getItem('bnr_user') || 'null'); },
  clear()    { sessionStorage.clear(); },
  isLoggedIn() { return !!this.token(); },

  // Call this from every page to guard access
  require() {
    if (!this.isLoggedIn()) { window.location.href = '/login'; return null; }
    return this.user();
  }
};

// Sign out — clears storage and redirects
function signOut() {
  Auth.clear();
  window.location.href = '/login';
}

// Status badge
function statusBadge(status) {
  if (!status) return '';
  return `<span class="badge badge-${status.toLowerCase()}">${status.replace(/_/g,' ')}</span>`;
}

// Format currency
function formatRWF(amount) {
  if (!amount) return '—';
  return 'RWF ' + Number(amount).toLocaleString();
}

// Show alert
function showAlert(container, message, type = 'error') {
  if (typeof container === 'string')
    container = document.getElementById(container);
  if (!container) return;
  container.innerHTML = `<div class="alert alert-${type}">${message}</div>`;
  container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// Clear alert
function clearAlert(id) {
  const el = document.getElementById(id);
  if (el) el.innerHTML = '';
}

// Modal helpers
function openModal(id)  { document.getElementById(id)?.classList.add('show'); }
function closeModal(id) { document.getElementById(id)?.classList.remove('show'); }

// Button loading state
function setLoading(btn, loading) {
  if (!btn) return;
  if (loading) {
    btn.dataset.originalText = btn.innerHTML;
    btn.innerHTML = 'Please wait...';
    btn.disabled = true;
  } else {
    btn.innerHTML = btn.dataset.originalText || 'Submit';
    btn.disabled = false;
  }
}

// Check if app is in a state where applicant can upload
function canApplicantUpload(status) {
  return ['DRAFT', 'ADDITIONAL_INFO_REQUESTED'].includes(status);
}