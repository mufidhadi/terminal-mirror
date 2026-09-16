# Contributing to Terminal Mirror

Thank you for your interest in contributing to **Terminal Mirror**! We are committed to building a secure, high-performance, open-source terminal streaming platform for developers worldwide.

---

## 🧭 Code of Conduct
We expect all contributors to maintain a respectful, welcoming, and inclusive environment. Please communicate constructively and treat everyone with respect.

---

## 🛠️ Development Setup

### Prerequisites
* **Rust 1.97+** (`rustup default stable`)
* **Cargo**
* **Docker & Docker Compose** (for running the local relay server)
* **Android Studio / SDK 34+** (for Android client development)

### Building the Workspace
```bash
# Clone the repository
git clone https://github.com/mufidhadi/terminal-mirror.git
cd terminal-mirror

# Run tests across all Rust workspace members
cargo test

# Build release binaries
cargo build --release
```

---

## 🧪 Testing Guidelines (Strict TDD)
* We practice **Test-Driven Development (TDD)**. All PRs adding features or fixing bugs must include corresponding tests in `crates/protocol/tests/` or relevant package directories.
* Run tests before submitting a PR:
  ```bash
  cargo test
  cargo clippy -- -D warnings
  cargo fmt --check
  ```

---

## 🔒 Security Best Practices for Contributors
* **Zero Secrets in Git**: Never commit `.env` files, private keys, or real server IP addresses. Use generic placeholders (e.g. `127.0.0.1`, `10.x.x.x`).
* **Zero-Knowledge Principle**: The relay server must never decrypt or possess keys to client terminal streams. All new packet types must maintain end-to-end encryption.
* **Security Disclosures**: If you discover a vulnerability, please report it privately via GitHub Security Advisories or by emailing `mufidhadi@madhani.id`.

---

## 🚀 Pull Request Process
1. Fork the repository and create a descriptive feature branch: `feature/your-feature-name`.
2. Ensure all unit tests pass with zero compiler warnings.
3. Write clear, concise commit messages. Do NOT include co-authoring attributions in commit messages.
4. Submit a Pull Request targeting the `main` branch with a thorough description of your changes.
