use axum::http::{header, HeaderMap};
use chrono::Local;
use image::{ImageBuffer, ImageFormat, Luma};
use serde_json::{json, Value};
use std::{
    fs::{self, OpenOptions},
    io::{BufWriter, Cursor, Write},
    path::{Path as StdPath, PathBuf},
};
use tokio::sync::broadcast;
use uuid::Uuid;

use crate::shared::{
    AppState, ChatLogState, COOKIE_MAX_AGE_SECONDS, HTTP_ADDRESS, MESSAGE_BROADCASTER,
};

pub(crate) fn init_logging() {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Debug),
    );
}

pub(crate) fn current_time_iso() -> String {
    Local::now().format("%Y-%m-%dT%H:%M:%S.%3fZ").to_string()
}

pub(crate) fn build_chat_message(
    sender: &str,
    message: &str,
    message_type: &str,
    url: Option<&str>,
) -> String {
    let mut obj = json!({
        "timestamp": current_time_iso(),
        "from": sender,
        "message": message,
        "system": false,
        "type": message_type,
    });
    if let Some(value) = url {
        obj["url"] = json!(value);
    }
    obj.to_string()
}

pub(crate) fn build_system_message(message: &str) -> String {
    json!({
        "timestamp": current_time_iso(),
        "from": "System",
        "message": message,
        "system": true,
        "type": "text",
    })
    .to_string()
}

pub(crate) fn build_set_cookie(name: &str, value: &str) -> String {
    format!(
        "{}={}; Path=/; Max-Age={}",
        name, value, COOKIE_MAX_AGE_SECONDS
    )
}

pub(crate) fn get_cookie_value(headers: &HeaderMap, name: &str) -> Option<String> {
    let cookie_header = headers.get(header::COOKIE)?.to_str().ok()?;
    for cookie in cookie_header.split(';') {
        let trimmed = cookie.trim();
        if let Some(value) = trimmed.strip_prefix(&format!("{}=", name)) {
            return Some(value.to_string());
        }
    }
    None
}

pub(crate) fn generate_random_token(length: usize) -> String {
    let mut token = String::new();
    while token.len() < length {
        token.push_str(&Uuid::new_v4().simple().to_string());
    }
    token.truncate(length);
    token
}

pub(crate) async fn append_chat_log_line(state: &AppState, payload: &str) {
    let file_path = {
        let chat_log: tokio::sync::MutexGuard<'_, ChatLogState> = state.chat_log.lock().await;
        chat_log.file_path.clone()
    };
    if let Some(parent) = file_path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    if let Ok(file) = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&file_path)
    {
        let mut writer = BufWriter::new(file);
        let _ = writer.write_all(format_chat_log_line(payload).as_bytes());
        let _ = writer.write_all(b"\n");
        let _ = writer.flush();
    }
}

pub(crate) fn format_chat_log_line(payload: &str) -> String {
    let parsed: Value = serde_json::from_str(payload).unwrap_or_else(|_| json!({}));
    let timestamp = parsed
        .get("timestamp")
        .and_then(Value::as_str)
        .unwrap_or_default();
    let time_only = chrono::DateTime::parse_from_rfc3339(timestamp)
        .map(|dt| dt.format("%H:%M:%S").to_string())
        .unwrap_or_else(|_| Local::now().format("%H:%M:%S").to_string());
    let sender = parsed
        .get("from")
        .and_then(Value::as_str)
        .unwrap_or("Unknown");
    let message = parsed.get("message").and_then(Value::as_str).unwrap_or("");
    let system = parsed
        .get("system")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    let prefix = if system {
        "[System]".to_string()
    } else {
        format!("[{}]", sender)
    };
    format!("[{}] {} {}", time_only, prefix, message)
}

pub(crate) async fn broadcast_message(state: &AppState, payload: String) {
    if let Some(holder) = MESSAGE_BROADCASTER.get() {
        if let Some(tx) = holder.lock().unwrap().clone() {
            let _ = tx.send(payload.clone());
        }
    }
    {
        let mut chat = state.chat.lock().await;
        chat.total_messages_sent += 1;
    }
    append_chat_log_line(state, &payload).await;
}

pub(crate) fn create_qr_png(data: &str) -> Option<Vec<u8>> {
    let qr = qrcodegen::QrCode::encode_text(data, qrcodegen::QrCodeEcc::Medium).ok()?;
    let module_count = qr.size();
    let scale = 8u32;
    let border = 4u32;
    let side = (module_count as u32 + border * 2) * scale;
    let mut image = ImageBuffer::<Luma<u8>, Vec<u8>>::from_pixel(side, side, Luma([255]));

    for y in 0..module_count {
        for x in 0..module_count {
            if !qr.get_module(x, y) {
                continue;
            }
            let start_x = (x as u32 + border) * scale;
            let start_y = (y as u32 + border) * scale;
            for dy in 0..scale {
                for dx in 0..scale {
                    image.put_pixel(start_x + dx, start_y + dy, Luma([0]));
                }
            }
        }
    }

    let mut output = Cursor::new(Vec::new());
    image::DynamicImage::ImageLuma8(image)
        .write_to(&mut output, ImageFormat::Png)
        .ok()?;
    Some(output.into_inner())
}

pub(crate) fn resolve_chat_log_path(base: &str) -> PathBuf {
    let now = Local::now();
    let day_directory = PathBuf::from(base).join(now.format("%Y-%m-%d").to_string());
    let _ = fs::create_dir_all(&day_directory);
    resolve_unique_name(
        &day_directory.join(format!("Chat_{}.txt", now.format("%H-%M-%S_%Y-%m-%d"))),
    )
}

pub(crate) fn message_broadcaster() -> Option<broadcast::Sender<String>> {
    MESSAGE_BROADCASTER
        .get()
        .and_then(|holder| holder.lock().unwrap().clone())
}

pub(crate) fn current_http_address_blocking() -> String {
    HTTP_ADDRESS
        .get()
        .map(|address| address.blocking_lock().clone())
        .unwrap_or_default()
}

pub(crate) fn sanitize_path(base: &StdPath, relative: &str) -> PathBuf {
    let mut target = base.to_path_buf();
    for part in relative
        .split('/')
        .filter(|part| !part.is_empty() && *part != ".")
    {
        if part == ".." {
            continue;
        }
        target.push(part);
    }
    target
}

pub(crate) fn relative_path(base: &StdPath, target: &StdPath) -> String {
    target
        .strip_prefix(base)
        .map(|path| path.to_string_lossy().to_string())
        .unwrap_or_else(|_| target.to_string_lossy().to_string())
}

pub(crate) fn resolve_unique_name(target: &StdPath) -> PathBuf {
    if !target.exists() {
        return target.to_path_buf();
    }
    let stem = target
        .file_stem()
        .map(|value| value.to_string_lossy().to_string())
        .unwrap_or_default();
    let ext = target
        .extension()
        .map(|value| format!(".{}", value.to_string_lossy()))
        .unwrap_or_default();
    let parent = target.parent().unwrap_or(StdPath::new(""));
    let mut index = 2;
    loop {
        let candidate = parent.join(format!("{}_{}{}", stem, index, ext));
        if !candidate.exists() {
            return candidate;
        }
        index += 1;
    }
}

pub(crate) fn parse_range(range: &str, file_size: u64) -> Option<(u64, u64)> {
    let range = range.strip_prefix("bytes=")?;
    let parts: Vec<&str> = range.split('-').collect();
    if parts.len() != 2 {
        return None;
    }
    let start: u64 = parts[0].parse().ok()?;
    let end: u64 = if parts[1].is_empty() {
        file_size - 1
    } else {
        parts[1].parse().ok()?
    };
    if start > end || end >= file_size {
        return None;
    }
    Some((start, end))
}

pub(crate) fn image_extension_from_content_type(content_type: &str) -> String {
    if content_type.contains("png") {
        ".png".to_string()
    } else if content_type.contains("gif") {
        ".gif".to_string()
    } else if content_type.contains("webp") {
        ".webp".to_string()
    } else if content_type.contains("bmp") {
        ".bmp".to_string()
    } else {
        ".jpg".to_string()
    }
}

pub(crate) fn image_extension_from_filename(file_name: &str) -> Option<String> {
    let lower = file_name.to_lowercase();
    if lower.ends_with(".png") {
        Some(".png".to_string())
    } else if lower.ends_with(".jpg") || lower.ends_with(".jpeg") {
        Some(".jpg".to_string())
    } else if lower.ends_with(".gif") {
        Some(".gif".to_string())
    } else if lower.ends_with(".webp") {
        Some(".webp".to_string())
    } else if lower.ends_with(".bmp") {
        Some(".bmp".to_string())
    } else {
        None
    }
}
