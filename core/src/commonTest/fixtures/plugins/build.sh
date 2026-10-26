#!/bin/sh
# Rebuilds the plugin fixtures from demo-plugin; needs Rust with the wasm32-unknown-unknown target.
set -eu
cd "$(dirname "$0")"
target="${CARGO_TARGET_DIR:-${TMPDIR:-/tmp}/octo-demo-plugin}"
for variant in demo:"" future-abi:future-abi spin:spin; do
    name="${variant%%:*}"
    features="${variant#*:}"
    CARGO_TARGET_DIR="$target/$name" cargo build --quiet --release --manifest-path demo-plugin/Cargo.toml \
        --target wasm32-unknown-unknown ${features:+--features "$features"}
    cp "$target/$name/wasm32-unknown-unknown/release/octo_demo_plugin.wasm" "$name.wasm"
done
