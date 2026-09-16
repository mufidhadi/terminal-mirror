use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

#[derive(Default, Clone)]
pub struct RelayMetrics {
    frames_routed: Arc<AtomicU64>,
    bytes_routed: Arc<AtomicU64>,
    dropped_frames: Arc<AtomicU64>,
    rate_limited_connections: Arc<AtomicU64>,
}

impl RelayMetrics {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn inc_frames_routed(&self, byte_count: usize) {
        self.frames_routed.fetch_add(1, Ordering::Relaxed);
        self.bytes_routed.fetch_add(byte_count as u64, Ordering::Relaxed);
    }

    pub fn inc_dropped_frames(&self, count: u64) {
        self.dropped_frames.fetch_add(count, Ordering::Relaxed);
    }

    pub fn inc_rate_limited(&self) {
        self.rate_limited_connections.fetch_add(1, Ordering::Relaxed);
    }

    pub fn render_prometheus(&self, active_sessions: usize, active_subscribers: usize) -> String {
        format!(
            "# HELP relay_active_sessions Number of registered active terminal sessions\n\
             # TYPE relay_active_sessions gauge\n\
             relay_active_sessions {}\n\
             \n\
             # HELP relay_active_subscribers Number of connected mobile subscriber clients\n\
             # TYPE relay_active_subscribers gauge\n\
             relay_active_subscribers {}\n\
             \n\
             # HELP relay_frames_routed_total Total number of binary terminal frames routed\n\
             # TYPE relay_frames_routed_total counter\n\
             relay_frames_routed_total {}\n\
             \n\
             # HELP relay_bytes_routed_total Total number of bytes routed through relay hub\n\
             # TYPE relay_bytes_routed_total counter\n\
             relay_bytes_routed_total {}\n\
             \n\
             # HELP relay_dropped_frames_total Total number of frames dropped due to subscriber buffer lag\n\
             # TYPE relay_dropped_frames_total counter\n\
             relay_dropped_frames_total {}\n\
             \n\
             # HELP relay_rate_limited_total Total number of connection attempts rejected by rate limiting\n\
             # TYPE relay_rate_limited_total counter\n\
             relay_rate_limited_total {}\n",
            active_sessions,
            active_subscribers,
            self.frames_routed.load(Ordering::Relaxed),
            self.bytes_routed.load(Ordering::Relaxed),
            self.dropped_frames.load(Ordering::Relaxed),
            self.rate_limited_connections.load(Ordering::Relaxed)
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metrics_counter_increments() {
        let metrics = RelayMetrics::new();
        metrics.inc_frames_routed(1024);
        metrics.inc_frames_routed(512);
        metrics.inc_dropped_frames(5);
        metrics.inc_rate_limited();

        let output = metrics.render_prometheus(2, 4);

        assert!(output.contains("relay_active_sessions 2"));
        assert!(output.contains("relay_active_subscribers 4"));
        assert!(output.contains("relay_frames_routed_total 2"));
        assert!(output.contains("relay_bytes_routed_total 1536"));
        assert!(output.contains("relay_dropped_frames_total 5"));
        assert!(output.contains("relay_rate_limited_total 1"));
    }
}
