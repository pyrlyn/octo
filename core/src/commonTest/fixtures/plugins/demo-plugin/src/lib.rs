//! Octo's demo plugin, written against Octo's plugin ABI with nothing but `core`, so the fixture
//! stays tiny and needs no allocator crate.
#![no_std]

use core::ptr::addr_of_mut;

#[panic_handler]
fn panic(_: &core::panic::PanicInfo) -> ! {
    core::arch::wasm32::unreachable()
}

const HEAP_SIZE: usize = 64 * 1024;
static mut HEAP: [u8; HEAP_SIZE] = [0; HEAP_SIZE];
static mut NEXT: usize = 0;
static mut OUT: [u8; 4096] = [0; 4096];

#[cfg(not(feature = "future-abi"))]
const MANIFEST: &[u8] = br#"{"abi":1,"id":"demo","name":"Demo","version":"1.0.0","agents":[{"id":"demo-acp","displayName":"Demo ACP","command":"demo-acp","args":["--acp"],"verifiedVersion":"1.0.0"}],"actions":[{"id":"review","title":"Ask for a review"},{"id":"note","title":"Show a note"}]}"#;

#[cfg(feature = "future-abi")]
const MANIFEST: &[u8] = br#"{"abi":99,"id":"future","name":"Future"}"#;

/// `(ptr, len)` in one i64: the pointer in the low 32 bits, the length in the high 32.
fn pack(ptr: *const u8, len: usize) -> i64 {
    ((len as i64) << 32) | (ptr as u32 as i64)
}

#[no_mangle]
pub extern "C" fn octo_alloc(len: i32) -> i32 {
    unsafe {
        let next = addr_of_mut!(NEXT);
        let start = *next;
        if len < 0 || start + len as usize > HEAP_SIZE {
            return 0;
        }
        *next = start + len as usize;
        (addr_of_mut!(HEAP) as *mut u8).add(start) as i32
    }
}

#[no_mangle]
pub extern "C" fn octo_manifest() -> i64 {
    pack(MANIFEST.as_ptr(), MANIFEST.len())
}

fn find(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|w| w == needle)
}

/// The raw JSON string value of `key`; it is copied back into JSON as is, so escapes stay valid.
fn string_value<'a>(input: &'a [u8], key: &[u8]) -> &'a [u8] {
    let Some(at) = find(input, key) else { return b"" };
    let rest = &input[at + key.len()..];
    let mut end = 0;
    while end < rest.len() && !(rest[end] == b'"' && (end == 0 || rest[end - 1] != b'\\')) {
        end += 1;
    }
    &rest[..end]
}

#[no_mangle]
pub extern "C" fn octo_run(ptr: i32, len: i32) -> i64 {
    let input = unsafe { core::slice::from_raw_parts(ptr as *const u8, len as usize) };
    if cfg!(feature = "spin") {
        let mut n: u64 = 0;
        loop {
            n = unsafe { core::ptr::read_volatile(&n) }.wrapping_add(1);
        }
    }
    let parts: [&[u8]; 3] = if find(input, br#""action":"review""#).is_some() {
        [br#"{"prompt":"Review the changes on "#, string_value(input, br#""branch":""#), br#"."}"#]
    } else if find(input, br#""action":"note""#).is_some() {
        [br#"{"message":"Hello from the demo plugin about "#, string_value(input, br#""title":""#), br#"."}"#]
    } else {
        return -1;
    };
    unsafe {
        let out = addr_of_mut!(OUT) as *mut u8;
        let mut at = 0;
        for part in parts {
            if at + part.len() > 4096 {
                return -1;
            }
            core::ptr::copy_nonoverlapping(part.as_ptr(), out.add(at), part.len());
            at += part.len();
        }
        pack(out, at)
    }
}
