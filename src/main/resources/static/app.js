const state = {
  token: null,
  userId: null,
  expiresAt: null,
  files: [],
  eventAbortController: null,
  eventCount: 0,
};

const elements = {
  authBadge: document.querySelector('#auth-badge'),
  currentUser: document.querySelector('#current-user'),
  tokenStatus: document.querySelector('#token-status'),
  tokenExpiry: document.querySelector('#token-expiry'),
  appHealthStatus: document.querySelector('#app-health-status'),
  appHealthBody: document.querySelector('#app-health-body'),
  actuatorHealthStatus: document.querySelector('#actuator-health-status'),
  actuatorHealthBody: document.querySelector('#actuator-health-body'),
  storageHealthStatus: document.querySelector('#storage-health-status'),
  storageHealthBody: document.querySelector('#storage-health-body'),
  uploadResult: document.querySelector('#upload-result'),
  filesTableBody: document.querySelector('#files-table-body'),
  fileCountTotal: document.querySelector('#file-count-total'),
  fileCountReady: document.querySelector('#file-count-ready'),
  fileCountFailed: document.querySelector('#file-count-failed'),
  fileCountDeleting: document.querySelector('#file-count-deleting'),
  eventLog: document.querySelector('#event-log'),
  eventConnectionStatus: document.querySelector('#event-connection-status'),
  eventCount: document.querySelector('#event-count'),
  latestEventName: document.querySelector('#latest-event-name'),
  actionLog: document.querySelector('#action-log'),
  fileRowTemplate: document.querySelector('#file-row-template'),
  registerForm: document.querySelector('#register-form'),
  loginForm: document.querySelector('#login-form'),
  uploadForm: document.querySelector('#upload-form'),
  fileInput: document.querySelector('#file-input'),
  refreshHealth: document.querySelector('#refresh-health'),
  refreshFiles: document.querySelector('#refresh-files'),
  connectEvents: document.querySelector('#connect-events'),
  disconnectEvents: document.querySelector('#disconnect-events'),
  clearActionLog: document.querySelector('#clear-action-log'),
  snapshotSystem: document.querySelector('#snapshot-system'),
  noteMinioStopped: document.querySelector('#note-minio-stopped'),
  noteMinioRestored: document.querySelector('#note-minio-restored'),
};

const EVENT_MEANINGS = {
  'file.upload.started': 'The metadata row exists and the system is beginning the blob write.',
  'file.upload.progress': 'The write is advancing and the client is receiving progress visibility.',
  'file.upload.completed': 'The blob write finished and the file should now be READY.',
  'file.upload.failed': 'Blob storage failed and the file should move to FAILED.',
  'file.deleted': 'Blob deletion and metadata soft delete both completed.',
  'file.delete.failed': 'The system could not remove the stored bytes; the metadata should remain in DELETE_FAILED.',
  'file.downloaded': 'A read path completed successfully against stored bytes.',
  connected: 'The SSE channel is open and the browser can now observe lifecycle events.',
};

function prettyJson(value) {
  return JSON.stringify(value, null, 2);
}

function updateAuthUI() {
  const authenticated = Boolean(state.token);
  elements.authBadge.textContent = authenticated ? 'Authenticated' : 'Signed out';
  elements.authBadge.className = `badge ${authenticated ? 'badge-ok' : 'badge-idle'}`;
  elements.currentUser.textContent = state.userId ?? '-';
  elements.tokenStatus.textContent = authenticated ? `Bearer token loaded (${state.token.length} chars)` : 'No token loaded';
  elements.tokenExpiry.textContent = state.expiresAt ?? '-';
  elements.connectEvents.disabled = !authenticated;
  elements.uploadForm.querySelector('button').disabled = !authenticated;
  elements.refreshFiles.disabled = !authenticated;
}

function updateEventStatus(status) {
  elements.eventConnectionStatus.textContent = status;
}

function updateFileSummary(files) {
  const ready = files.filter((file) => file.status === 'READY').length;
  const failed = files.filter((file) => file.status === 'FAILED' || file.status === 'DELETE_FAILED').length;
  const deleting = files.filter((file) => file.status === 'DELETING').length;
  elements.fileCountTotal.textContent = String(files.length);
  elements.fileCountReady.textContent = String(ready);
  elements.fileCountFailed.textContent = String(failed);
  elements.fileCountDeleting.textContent = String(deleting);
}

function formatTimestamp(value) {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function addActionLog(title, concept, notes, level = 'info') {
  if (elements.actionLog.querySelector('.empty-state')) {
    elements.actionLog.innerHTML = '';
  }

  const entry = document.createElement('article');
  entry.className = 'action-entry';
  const now = new Date().toLocaleTimeString();
  entry.innerHTML = `
    <div class="action-meta">
      <span>${now}</span>
      <span>${level.toUpperCase()}</span>
    </div>
    <h3 class="event-name">${title}</h3>
    <p class="action-notes"><strong>Concept:</strong> ${concept}\n${notes}</p>
  `;
  elements.actionLog.prepend(entry);
}

function addEventLog(eventName, payload) {
  if (elements.eventLog.querySelector('.empty-state')) {
    elements.eventLog.innerHTML = '';
  }

  const entry = document.createElement('article');
  entry.className = 'event-entry';
  const explanation = EVENT_MEANINGS[eventName] ?? 'No interpretation mapped yet for this event.';
  entry.innerHTML = `
    <div class="event-meta">
      <span>${formatTimestamp(payload.occurredAt ?? new Date().toISOString())}</span>
      <span>${payload.correlationId ?? 'no-correlation-id'}</span>
    </div>
    <h3 class="event-name">${eventName}</h3>
    <p class="action-notes"><strong>Why this matters:</strong> ${explanation}</p>
    <pre class="code-block event-data">${prettyJson(payload)}</pre>
  `;
  elements.eventLog.prepend(entry);
  state.eventCount += 1;
  elements.eventCount.textContent = String(state.eventCount);
  elements.latestEventName.textContent = eventName;
}

function setCodeBlock(target, body, fallback = 'No data.') {
  target.textContent = body ? prettyJson(body) : fallback;
  target.classList.toggle('muted', !body);
}

async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.token) {
    headers.set('Authorization', `Bearer ${state.token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  const contentType = response.headers.get('content-type') || '';
  let body = null;
  if (contentType.includes('application/json')) {
    body = await response.json();
  } else if (!contentType.includes('application/octet-stream')) {
    const text = await response.text();
    body = text ? { raw: text } : null;
  }

  if (!response.ok) {
    const error = new Error(body?.error?.message || `Request failed with status ${response.status}`);
    error.response = response;
    error.body = body;
    throw error;
  }

  return { response, body };
}

async function refreshHealth() {
  try {
    const [{ body: appHealth }, { body: actuatorHealth }] = await Promise.all([
      apiRequest('/api/v1/health'),
      apiRequest('/actuator/health'),
    ]);

    elements.appHealthStatus.textContent = appHealth.status ?? 'UNKNOWN';
    setCodeBlock(elements.appHealthBody, appHealth);

    elements.actuatorHealthStatus.textContent = actuatorHealth.status ?? 'UNKNOWN';
    setCodeBlock(elements.actuatorHealthBody, actuatorHealth);

    const storage = actuatorHealth.components?.storage ?? null;
    elements.storageHealthStatus.textContent = storage?.status ?? 'UNKNOWN';
    setCodeBlock(elements.storageHealthBody, storage ?? { note: 'Storage component not present' });

    addActionLog(
      'Health refreshed',
      'Operational visibility and dependency awareness',
      'Called /api/v1/health and /actuator/health to inspect app and storage readiness.'
    );
  } catch (error) {
    addActionLog(
      'Health refresh failed',
      'Operational visibility and failure surfacing',
      `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
      'error'
    );
  }
}

function renderFiles(files) {
  elements.filesTableBody.innerHTML = '';
  updateFileSummary(files);
  if (!files.length) {
    elements.filesTableBody.innerHTML = '<tr><td colspan="6" class="empty-state">No files for this user yet.</td></tr>';
    return;
  }

  for (const file of files) {
    const row = elements.fileRowTemplate.content.firstElementChild.cloneNode(true);
    row.dataset.fileId = file.id;
    row.querySelector('[data-field="fileName"]').textContent = file.fileName;

    const statusCell = row.querySelector('[data-field="status"]');
    const chip = document.createElement('span');
    chip.className = `status-chip status-${String(file.status).toLowerCase()}`;
    chip.textContent = file.status;
    statusCell.appendChild(chip);

    row.querySelector('[data-field="sizeBytes"]').textContent = `${file.sizeBytes} bytes`;
    row.querySelector('[data-field="checksum"]').textContent = file.checksum ?? '-';
    row.querySelector('[data-field="updatedAt"]').textContent = formatTimestamp(file.updatedAt);

    row.querySelector('[data-action="download"]').addEventListener('click', () => downloadFile(file));
    row.querySelector('[data-action="delete"]').addEventListener('click', () => deleteFile(file));

    elements.filesTableBody.appendChild(row);
  }
}

async function refreshFiles() {
  if (!state.token) {
    addActionLog('Files refresh skipped', 'Auth boundary awareness', 'Log in before loading user-scoped files.', 'error');
    return;
  }

  try {
    const { body } = await apiRequest('/api/v1/files');
    state.files = body.items ?? [];
    renderFiles(state.files);
    addActionLog(
      'Files refreshed',
      'Read-path consistency and ownership scoping',
      `Loaded ${state.files.length} file(s) for the authenticated user.`
    );
  } catch (error) {
    addActionLog(
      'Files refresh failed',
      'Read-path failure surfacing',
      `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
      'error'
    );
  }
}

async function snapshotSystem() {
  addActionLog(
    'System snapshot requested',
    'Operational visibility during failure drills',
    'Refreshing health and file state together so you can compare before and after a failure event.'
  );
  await refreshHealth();
  if (state.token) {
    await refreshFiles();
  }
}

async function downloadFile(file) {
  try {
    const response = await fetch(`/api/v1/files/${file.id}/download`, {
      headers: {
        Authorization: `Bearer ${state.token}`,
      },
    });

    if (!response.ok) {
      const body = await response.json();
      throw Object.assign(new Error(body?.error?.message || 'Download failed'), { body, response });
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = file.fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);

    addActionLog(
      'File downloaded',
      'Read-after-write validation across blob storage',
      `Downloaded ${file.fileName} (${file.id}) from object storage-backed endpoint.`
    );
  } catch (error) {
    addActionLog(
      'Download failed',
      'Read-path failure surfacing',
      `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
      'error'
    );
  }
}

async function deleteFile(file) {
  if (!confirm(`Delete ${file.fileName}?`)) {
    return;
  }

  try {
    await apiRequest(`/api/v1/files/${file.id}`, { method: 'DELETE' });
    addActionLog(
      'File deleted',
      'Coordinated metadata and blob deletion',
      `Deleted ${file.fileName} (${file.id}). Refreshing list to confirm the user view updated.`
    );
    await refreshFiles();
  } catch (error) {
    addActionLog(
      'Delete failed',
      'Delete consistency and failure handling',
      `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
      'error'
    );
  }
}

async function handleAuth(event, mode) {
  event.preventDefault();
  const form = event.currentTarget;
  const email = form.querySelector('input[type="email"]').value;
  const password = form.querySelector('input[type="password"]').value;

  try {
    const { body } = await apiRequest(`/api/v1/auth/${mode}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });

    state.token = body.token;
    state.userId = body.userId;
    state.expiresAt = body.expiresAt;
    updateAuthUI();

    if (mode === 'register') {
      document.querySelector('#login-email').value = email;
      document.querySelector('#login-password').value = password;
    }

    addActionLog(
      mode === 'register' ? 'User registered' : 'User logged in',
      'Authentication bootstrap and token issuance',
      `User ${email} authenticated. Token expires at ${body.expiresAt}.`
    );

    await refreshHealth();
    await refreshFiles();
  } catch (error) {
    addActionLog(
      `${mode === 'register' ? 'Registration' : 'Login'} failed`,
      'Authentication failure handling',
      `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
      'error'
    );
  }
}

async function handleUpload(event) {
  event.preventDefault();
  if (!state.token) {
    addActionLog('Upload blocked', 'Auth boundary awareness', 'Log in before uploading files.', 'error');
    return;
  }

  const file = elements.fileInput.files[0];
  if (!file) {
    addActionLog('Upload blocked', 'Input validation', 'Choose a file before uploading.', 'error');
    return;
  }

  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await fetch('/api/v1/files', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${state.token}`,
      },
      body: formData,
    });

    const body = await response.json();
    if (!response.ok) {
      throw Object.assign(new Error(body?.error?.message || 'Upload failed'), { body, response });
    }

    elements.uploadResult.textContent = prettyJson(body);
    elements.uploadResult.classList.remove('muted');

    addActionLog(
      'File uploaded',
      'Blob write before metadata finalization',
      `Uploaded ${file.name}. The system should have written bytes to MinIO, updated metadata to READY, and emitted upload events.`
    );

    await refreshFiles();
  } catch (error) {
    elements.uploadResult.textContent = prettyJson(error.body ?? { error: error.message });
    elements.uploadResult.classList.remove('muted');
    addActionLog(
      'Upload failed',
      'Write-path failure and consistency handling',
      `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
      'error'
    );
  }
}

function parseSseBlock(block) {
  const lines = block.split('\n');
  let eventName = 'message';
  const dataLines = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }

  const dataText = dataLines.join('\n');
  if (!dataText) {
    return null;
  }

  try {
    return { eventName, payload: JSON.parse(dataText) };
  } catch {
    return { eventName, payload: { raw: dataText } };
  }
}

function handleEventSideEffects(eventName) {
  if (eventName === 'file.upload.completed' || eventName === 'file.upload.failed' || eventName === 'file.deleted' || eventName === 'file.delete.failed') {
    refreshFiles().catch(() => {});
  }
  if (eventName === 'file.upload.failed' || eventName === 'file.delete.failed') {
    refreshHealth().catch(() => {});
  }
  addActionLog(
    `Observed ${eventName}`,
    'Event-driven system visibility',
    EVENT_MEANINGS[eventName] ?? 'Captured a real-time event from the server.'
  );
}

async function connectEventStream() {
  if (!state.token) {
    addActionLog('Event stream blocked', 'Auth boundary awareness', 'Log in before connecting to the event stream.', 'error');
    return;
  }
  if (state.eventAbortController) {
    addActionLog('Event stream already connected', 'Connection lifecycle awareness', 'Disconnect first if you want to restart the SSE stream.');
    return;
  }

  const controller = new AbortController();
  state.eventAbortController = controller;
  elements.connectEvents.disabled = true;
  elements.disconnectEvents.disabled = false;
  updateEventStatus('Connecting');

  addActionLog(
    'Connecting event stream',
    'Authenticated server-to-client streaming',
    'Opening /api/v1/events/stream with fetch() so the browser can send the JWT header.'
  );

  try {
    const response = await fetch('/api/v1/events/stream', {
      headers: {
        Authorization: `Bearer ${state.token}`,
        Accept: 'text/event-stream',
      },
      signal: controller.signal,
    });

    if (!response.ok || !response.body) {
      const body = await response.json().catch(() => ({ raw: 'Unable to parse event-stream error body' }));
      throw Object.assign(new Error(body?.error?.message || 'Could not connect to event stream'), { body, response });
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    updateEventStatus('Connected');

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split('\n\n');
      buffer = blocks.pop() ?? '';
      for (const block of blocks) {
        const parsed = parseSseBlock(block.trim());
        if (parsed) {
          addEventLog(parsed.eventName, parsed.payload);
          handleEventSideEffects(parsed.eventName);
        }
      }
    }

    if (!controller.signal.aborted) {
      updateEventStatus('Disconnected');
      addActionLog('Event stream closed by server', 'Connection lifecycle awareness', 'The server ended the stream unexpectedly.', 'error');
    }
  } catch (error) {
    if (controller.signal.aborted) {
      updateEventStatus('Disconnected');
      addActionLog('Event stream disconnected', 'Connection lifecycle awareness', 'The event stream was disconnected from the UI.');
    } else {
      updateEventStatus('Error');
      addActionLog(
        'Event stream failed',
        'Real-time channel failure surfacing',
        `Message: ${error.message}\nBody: ${prettyJson(error.body ?? { raw: 'n/a' })}`,
        'error'
      );
    }
  } finally {
    state.eventAbortController = null;
    elements.connectEvents.disabled = !state.token;
    elements.disconnectEvents.disabled = true;
    if (elements.eventConnectionStatus.textContent === 'Connecting') {
      updateEventStatus('Disconnected');
    }
  }
}

function disconnectEventStream() {
  if (state.eventAbortController) {
    state.eventAbortController.abort();
  }
}

function wireEvents() {
  elements.registerForm.addEventListener('submit', (event) => handleAuth(event, 'register'));
  elements.loginForm.addEventListener('submit', (event) => handleAuth(event, 'login'));
  elements.uploadForm.addEventListener('submit', handleUpload);
  elements.refreshHealth.addEventListener('click', refreshHealth);
  elements.refreshFiles.addEventListener('click', refreshFiles);
  elements.connectEvents.addEventListener('click', connectEventStream);
  elements.disconnectEvents.addEventListener('click', disconnectEventStream);
  elements.snapshotSystem.addEventListener('click', snapshotSystem);
  elements.noteMinioStopped.addEventListener('click', async () => {
    addActionLog(
      'MinIO stop noted',
      'Failure drill coordination',
      'You marked object storage as stopped. Next: attempt an upload and watch actuator storage health plus SSE failure events.'
    );
    await snapshotSystem();
  });
  elements.noteMinioRestored.addEventListener('click', async () => {
    addActionLog(
      'MinIO restore noted',
      'Recovery observation',
      'You marked object storage as restored. Refreshing health and files so you can confirm the platform recovered.'
    );
    await snapshotSystem();
  });
  elements.clearActionLog.addEventListener('click', () => {
    elements.actionLog.innerHTML = '<div class="empty-state">No actions recorded.</div>';
  });
}

async function bootstrap() {
  updateAuthUI();
  elements.disconnectEvents.disabled = true;
  updateEventStatus('Disconnected');
  elements.eventCount.textContent = '0';
  elements.latestEventName.textContent = '-';
  updateFileSummary([]);
  wireEvents();
  await refreshHealth();
  addActionLog(
    'Learning console ready',
    'Observation-first system design workflow',
    'Use this page to authenticate, upload files, inspect health, and watch live events without leaving the browser.'
  );
}

bootstrap();
