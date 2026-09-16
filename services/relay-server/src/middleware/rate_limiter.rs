use dashmap::DashMap;
use std::net::IpAddr;
use std::sync::Arc;
use std::time::Instant;

#[derive(Clone)]
pub struct IpRateLimiter {
    buckets: Arc<DashMap<IpAddr, (u32, Instant)>>,
    max_per_minute: u32,
}

impl IpRateLimiter {
    pub fn new(max_per_minute: u32) -> Self {
        Self {
            buckets: Arc::new(DashMap::new()),
            max_per_minute,
        }
    }

    /// Checks if a connection attempt from `ip` is within allowed rate limit.
    /// Returns `true` if allowed, `false` if limit exceeded.
    pub fn check_allowed(&self, ip: IpAddr) -> bool {
        let mut entry = self.buckets.entry(ip).or_insert((0, Instant::now()));
        let (count, start_time) = entry.value_mut();

        if start_time.elapsed().as_secs() >= 60 {
            *count = 1;
            *start_time = Instant::now();
            true
        } else {
            *count += 1;
            *count <= self.max_per_minute
        }
    }

    /// Returns the number of distinct tracked IP addresses in memory.
    #[allow(dead_code)]
    pub fn active_ip_count(&self) -> usize {
        self.buckets.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::Ipv4Addr;

    #[test]
    fn test_rate_limiter_allows_up_to_limit() {
        let limiter = IpRateLimiter::new(3);
        let ip = IpAddr::V4(Ipv4Addr::new(192, 168, 1, 10));

        assert!(limiter.check_allowed(ip)); // 1
        assert!(limiter.check_allowed(ip)); // 2
        assert!(limiter.check_allowed(ip)); // 3
        assert!(!limiter.check_allowed(ip)); // 4 - Exceeded!
        assert!(!limiter.check_allowed(ip)); // 5 - Exceeded!
    }

    #[test]
    fn test_rate_limiter_isolates_different_ips() {
        let limiter = IpRateLimiter::new(2);
        let ip1 = IpAddr::V4(Ipv4Addr::new(10, 0, 0, 1));
        let ip2 = IpAddr::V4(Ipv4Addr::new(10, 0, 0, 2));

        assert!(limiter.check_allowed(ip1));
        assert!(limiter.check_allowed(ip1));
        assert!(!limiter.check_allowed(ip1)); // ip1 blocked

        assert!(limiter.check_allowed(ip2)); // ip2 still allowed!
        assert!(limiter.check_allowed(ip2));
        assert!(!limiter.check_allowed(ip2));
    }
}
