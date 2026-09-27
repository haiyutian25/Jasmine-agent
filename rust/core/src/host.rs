//! What the core needs from the platform: the device's clock.
//!
//! Time is the one thing the core cannot know by itself: the zone is a platform fact, and a
//! formatted answer is what both the interface and the model read. Conversations do not cross
//! this boundary any more — they live in the core's own rollout files, under the directory the
//! platform hands over at attach time.

/// The platform's clock, already formatted in the device's zone.
pub trait Clock: Send + Sync {
    /// Something like `2026-09-27 10:31:05 GMT+08:00`.
    fn now_formatted(&self) -> String;

    /// The same shape for a moment already recorded, so a list reads in the device's zone.
    fn format(&self, timestamp: &str) -> String;
}
