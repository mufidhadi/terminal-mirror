use std::sync::Arc;
use std::time::Duration;
use tokio::sync::mpsc;
use tokio::time::Instant;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ResizeRequest {
    pub cols: u16,
    pub rows: u16,
}

/// ConPTY Window Resize Debouncer
///
/// Prevents the ConPTY "resize storm" caused by rapid window/mobile layout shifts.
/// Batches resize requests within a configurable sliding window (default 200ms)
/// and triggers a single layout recalculation when sizing has settled.
pub struct ResizeDebouncer {
    tx: mpsc::Sender<ResizeRequest>,
}

impl ResizeDebouncer {
    pub fn new<F>(debounce_duration: Duration, on_settled: F) -> Self
    where
        F: Fn(u16, u16) + Send + Sync + 'static,
    {
        let callback = Arc::new(on_settled);
        let (tx, mut rx) = mpsc::channel::<ResizeRequest>(64);

        tokio::spawn(async move {
            let mut pending_size: Option<(u16, u16)> = None;
            let mut deadline: Option<Instant> = None;

            loop {
                tokio::select! {
                    recv_res = rx.recv() => {
                        match recv_res {
                            Some(req) => {
                                pending_size = Some((req.cols, req.rows));
                                deadline = Some(Instant::now() + debounce_duration);
                            }
                            None => {
                                // Channel closed: flush if pending and exit
                                if let Some((cols, rows)) = pending_size.take() {
                                    callback(cols, rows);
                                }
                                break;
                            }
                        }
                    }
                    _ = async {
                        if let Some(d) = deadline {
                            tokio::time::sleep_until(d).await;
                        } else {
                            futures_util::future::pending::<()>().await;
                        }
                    }, if deadline.is_some() => {
                        if let Some((cols, rows)) = pending_size.take() {
                            deadline = None;
                            callback(cols, rows);
                        }
                    }
                }
            }
        });

        Self { tx }
    }

    pub async fn request_resize(
        &self,
        cols: u16,
        rows: u16,
    ) -> Result<(), mpsc::error::SendError<ResizeRequest>> {
        self.tx.send(ResizeRequest { cols, rows }).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Mutex;
    use tokio::time::sleep;

    #[tokio::test]
    async fn test_resize_debouncer_coalesces_rapid_events() {
        let call_count = Arc::new(AtomicUsize::new(0));
        let last_size = Arc::new(Mutex::new((0u16, 0u16)));

        let c_count = Arc::clone(&call_count);
        let l_size = Arc::clone(&last_size);

        let debouncer = ResizeDebouncer::new(Duration::from_millis(100), move |cols, rows| {
            c_count.fetch_add(1, Ordering::SeqCst);
            let mut guard = l_size.lock().unwrap();
            *guard = (cols, rows);
        });

        // Fire 3 rapid resize events within 50ms (window is 100ms)
        debouncer.request_resize(80, 24).await.unwrap();
        sleep(Duration::from_millis(20)).await;
        debouncer.request_resize(90, 30).await.unwrap();
        sleep(Duration::from_millis(20)).await;
        debouncer.request_resize(100, 35).await.unwrap();

        // Wait for debounce window (100ms) to elapse + margin
        sleep(Duration::from_millis(150)).await;

        // Verify only 1 execution occurred with the final dimensions
        assert_eq!(call_count.load(Ordering::SeqCst), 1);
        assert_eq!(*last_size.lock().unwrap(), (100, 35));

        // Fire a subsequent event well after the window
        debouncer.request_resize(120, 40).await.unwrap();
        sleep(Duration::from_millis(150)).await;

        assert_eq!(call_count.load(Ordering::SeqCst), 2);
        assert_eq!(*last_size.lock().unwrap(), (120, 40));
    }
}
