/// Streaming UTF-8 chunker that handles multibyte character slicing across buffer boundaries
#[derive(Debug, Default)]
pub struct Utf8StreamChunker {
    pending_bytes: Vec<u8>,
}

impl Utf8StreamChunker {
    pub fn new() -> Self {
        Self {
            pending_bytes: Vec::with_capacity(4),
        }
    }

    /// Appends incoming raw bytes and returns valid UTF-8 bytes,
    /// buffering any trailing incomplete multibyte sequence (1-3 bytes).
    pub fn process_chunk(&mut self, incoming: &[u8]) -> Vec<u8> {
        let mut combined = Vec::with_capacity(self.pending_bytes.len() + incoming.len());
        combined.extend_from_slice(&self.pending_bytes);
        combined.extend_from_slice(incoming);
        self.pending_bytes.clear();

        if combined.is_empty() {
            return Vec::new();
        }

        // Find how many valid UTF-8 bytes we have from the start
        match std::str::from_utf8(&combined) {
            Ok(_) => combined,
            Err(e) => {
                let valid_up_to = e.valid_up_to();
                if let Some(error_len) = e.error_len() {
                    // There's a genuine invalid byte sequence, not just an incomplete trailing sequence.
                    // Split at error, skip error bytes, and continue
                    let mut valid = combined[..valid_up_to].to_vec();
                    let remaining = &combined[valid_up_to + error_len..];
                    let sub_res = self.process_chunk(remaining);
                    valid.extend(sub_res);
                    valid
                } else {
                    // The error is due to an incomplete multibyte sequence at the very end of the buffer!
                    self.pending_bytes.extend_from_slice(&combined[valid_up_to..]);
                    combined[..valid_up_to].to_vec()
                }
            }
        }
    }

    pub fn pending_len(&self) -> usize {
        self.pending_bytes.len()
    }
}
