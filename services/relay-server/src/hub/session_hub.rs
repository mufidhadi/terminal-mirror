use dashmap::DashMap;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::{broadcast, mpsc};

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct HostMetadata {
    pub host_name: Option<String>,
    pub shell: Option<String>,
}

pub type HostSender = Arc<Mutex<Option<(uuid::Uuid, mpsc::Sender<Vec<u8>>)>>>;

#[derive(Clone)]
pub struct SessionRouter {
    #[allow(dead_code)]
    pub session_id: String,
    pub broadcast_tx: broadcast::Sender<Vec<u8>>,
    pub host_tx: HostSender,
    pub host_meta: Arc<Mutex<Option<HostMetadata>>>,
    pub subscribers_count: Arc<AtomicUsize>,
    pub last_activity: Arc<Mutex<Instant>>,
}

impl SessionRouter {
    pub fn new(session_id: String) -> Self {
        let (broadcast_tx, _) = broadcast::channel(1024);
        Self {
            session_id,
            broadcast_tx,
            host_tx: Arc::new(Mutex::new(None)),
            host_meta: Arc::new(Mutex::new(None)),
            subscribers_count: Arc::new(AtomicUsize::new(0)),
            last_activity: Arc::new(Mutex::new(Instant::now())),
        }
    }

    pub fn touch(&self) {
        if let Ok(mut lock) = self.last_activity.lock() {
            *lock = Instant::now();
        }
    }

    pub fn is_stale(&self, timeout: Duration) -> bool {
        let host_disconnected = self.host_tx.lock().map(|h| h.is_none()).unwrap_or(true);
        let elapsed = self
            .last_activity
            .lock()
            .map(|t| t.elapsed())
            .unwrap_or(Duration::ZERO);
        host_disconnected && elapsed > timeout
    }
}

#[derive(Clone, Default)]
pub struct SessionHub {
    sessions: Arc<DashMap<String, SessionRouter>>,
}

impl SessionHub {
    pub fn new() -> Self {
        Self {
            sessions: Arc::new(DashMap::new()),
        }
    }

    pub fn get_or_create(&self, session_id: &str) -> SessionRouter {
        self.sessions
            .entry(session_id.to_string())
            .or_insert_with(|| SessionRouter::new(session_id.to_string()))
            .clone()
    }

    pub fn register_host(
        &self,
        session_id: &str,
        conn_id: uuid::Uuid,
        tx: mpsc::Sender<Vec<u8>>,
        host_name: Option<String>,
        shell: Option<String>,
    ) {
        let router = self.get_or_create(session_id);
        if let Ok(mut host_guard) = router.host_tx.lock() {
            *host_guard = Some((conn_id, tx));
        }
        if let Ok(mut meta_guard) = router.host_meta.lock() {
            *meta_guard = Some(HostMetadata { host_name, shell });
        }
        router.touch();
    }

    pub fn unregister_host(&self, session_id: &str, conn_id: uuid::Uuid) -> bool {
        if let Some(router) = self.sessions.get(session_id) {
            let mut cleared = false;
            if let Ok(mut host_guard) = router.host_tx.lock() {
                if let Some((active_id, _)) = *host_guard {
                    if active_id == conn_id {
                        *host_guard = None;
                        cleared = true;
                    }
                }
            }
            if cleared {
                if let Ok(mut meta_guard) = router.host_meta.lock() {
                    *meta_guard = None;
                }
                router.touch();
                return true;
            }
        }
        false
    }

    pub fn get_host_presence(&self, session_id: &str) -> (bool, Option<String>, Option<String>) {
        if let Some(router) = self.sessions.get(session_id) {
            let is_online = router.host_tx.lock().map(|h| h.is_some()).unwrap_or(false);
            if is_online {
                if let Ok(meta_guard) = router.host_meta.lock() {
                    if let Some(meta) = meta_guard.as_ref() {
                        return (true, meta.host_name.clone(), meta.shell.clone());
                    }
                }
                return (true, None, None);
            }
        }
        (false, None, None)
    }

    pub fn active_sessions_count(&self) -> usize {
        self.sessions.len()
    }

    pub fn total_subscribers_count(&self) -> usize {
        self.sessions
            .iter()
            .map(|entry| entry.value().subscribers_count.load(Ordering::Relaxed))
            .sum()
    }

    pub fn reap_stale(&self, timeout: Duration) -> usize {
        let mut reaped = 0;
        self.sessions.retain(|_id, router| {
            if router.is_stale(timeout) {
                reaped += 1;
                false
            } else {
                true
            }
        });
        reaped
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_session_hub_lifecycle() {
        let hub = SessionHub::new();
        assert_eq!(hub.active_sessions_count(), 0);

        let router1 = hub.get_or_create("session-mac");
        assert_eq!(hub.active_sessions_count(), 1);

        let router2 = hub.get_or_create("session-win");
        assert_eq!(hub.active_sessions_count(), 2);

        // Verify broadcast distribution
        let mut rx1 = router1.broadcast_tx.subscribe();
        let payload = b"vt100-escape-sequence".to_vec();
        let sent = router1.broadcast_tx.send(payload.clone());
        assert!(sent.is_ok());

        let received = rx1.try_recv();
        assert_eq!(received.unwrap(), payload);

        // Subscribers count
        router1.subscribers_count.fetch_add(2, Ordering::SeqCst);
        router2.subscribers_count.fetch_add(1, Ordering::SeqCst);
        assert_eq!(hub.total_subscribers_count(), 3);
    }

    #[test]
    fn test_stale_session_reaping() {
        let hub = SessionHub::new();
        let router = hub.get_or_create("abandoned-session");

        // Manually age the activity time past 10 milliseconds
        if let Ok(mut lock) = router.last_activity.lock() {
            *lock = Instant::now() - Duration::from_millis(50);
        }

        // Host is not registered (None), so it's stale after 10ms
        let reaped = hub.reap_stale(Duration::from_millis(10));
        assert_eq!(reaped, 1);
        assert_eq!(hub.active_sessions_count(), 0);
    }

    #[test]
    fn test_register_host_sets_metadata_and_presence() {
        let hub = SessionHub::new();
        let (tx, _rx) = mpsc::channel(16);
        let conn_id = uuid::Uuid::new_v4();

        hub.register_host(
            "sess-mac",
            conn_id,
            tx,
            Some("MacBook Pro Mas Mufid".into()),
            Some("/bin/zsh".into()),
        );

        let (online, name, shell) = hub.get_host_presence("sess-mac");
        assert!(online);
        assert_eq!(name.as_deref(), Some("MacBook Pro Mas Mufid"));
        assert_eq!(shell.as_deref(), Some("/bin/zsh"));
    }

    #[test]
    fn test_unregister_host_with_matching_uuid_clears_state() {
        let hub = SessionHub::new();
        let (tx, _rx) = mpsc::channel(16);
        let conn_id = uuid::Uuid::new_v4();

        hub.register_host(
            "sess-mac",
            conn_id,
            tx,
            Some("Mac".into()),
            Some("zsh".into()),
        );
        let ok = hub.unregister_host("sess-mac", conn_id);
        assert!(ok);

        let (online, name, shell) = hub.get_host_presence("sess-mac");
        assert!(!online);
        assert_eq!(name, None);
        assert_eq!(shell, None);
    }

    #[test]
    fn test_unregister_host_with_stale_uuid_is_noop() {
        let hub = SessionHub::new();
        let (tx1, _rx1) = mpsc::channel(16);
        let (tx2, _rx2) = mpsc::channel(16);
        let conn_id_1 = uuid::Uuid::new_v4();
        let conn_id_2 = uuid::Uuid::new_v4();

        // Host 1 registers
        hub.register_host("sess-mac", conn_id_1, tx1, Some("Mac1".into()), None);

        // Host 2 takes over (reconnect or replacement)
        hub.register_host("sess-mac", conn_id_2, tx2, Some("Mac2".into()), None);

        // Host 1 delayed disconnect arrives
        let ok = hub.unregister_host("sess-mac", conn_id_1);
        assert!(!ok, "Unregistering stale host UUID must be a no-op");

        // Host 2 presence must remain alive!
        let (online, name, _) = hub.get_host_presence("sess-mac");
        assert!(online, "Host 2 must still be online");
        assert_eq!(name.as_deref(), Some("Mac2"));
    }

    #[test]
    fn test_get_host_presence_unregistered_session_returns_false() {
        let hub = SessionHub::new();
        let (online, name, shell) = hub.get_host_presence("non-existent");
        assert!(!online);
        assert_eq!(name, None);
        assert_eq!(shell, None);
    }
}
