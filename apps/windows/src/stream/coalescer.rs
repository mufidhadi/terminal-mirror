/// Manages downstream backpressure and coalesces intermediate deltas
/// when network buffer latency exceeds threshold.
#[allow(dead_code)]
pub struct StreamCoalescer {
    pub max_pending_bytes: usize,
    pub current_pending_bytes: usize,
}

impl StreamCoalescer {
    pub fn new(max_pending_bytes: usize) -> Self {
        Self {
            max_pending_bytes,
            current_pending_bytes: 0,
        }
    }

    /// Evaluates whether an incoming chunk should be forwarded or dropped
    /// in favor of a future full screen snapshot.
    pub fn should_coalesce(&mut self, chunk_len: usize) -> bool {
        self.current_pending_bytes += chunk_len;
        self.current_pending_bytes > self.max_pending_bytes
    }

    /// Resets pending byte counter when an ack or sync frame is sent
    #[allow(dead_code)]
    pub fn reset(&mut self) {
        self.current_pending_bytes = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_stream_coalescer_threshold_and_reset() {
        let mut coalescer = StreamCoalescer::new(100);

        assert!(!coalescer.should_coalesce(50));
        assert_eq!(coalescer.current_pending_bytes, 50);

        assert!(!coalescer.should_coalesce(50));
        assert_eq!(coalescer.current_pending_bytes, 100);

        // Exceeds threshold (101 > 100)
        assert!(coalescer.should_coalesce(1));
        assert_eq!(coalescer.current_pending_bytes, 101);

        coalescer.reset();
        assert_eq!(coalescer.current_pending_bytes, 0);
        assert!(!coalescer.should_coalesce(10));
    }
}
