FROM rust:1.97-slim as builder

WORKDIR /usr/src/app
COPY . .

RUN cargo build --release --bin terminal-mirror-relay

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /usr/src/app/target/release/terminal-mirror-relay /usr/local/bin/terminal-mirror-relay

EXPOSE 8080
CMD ["terminal-mirror-relay"]
