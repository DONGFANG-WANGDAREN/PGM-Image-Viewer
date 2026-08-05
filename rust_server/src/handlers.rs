use axum::{
    body::{Body, Bytes},
    extract::{Form, Multipart, Path, Query, State},
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use chrono::Local;
use log::error;
use serde_json::json;
use std::{
    collections::HashMap,
    fs::{self, File},
    io::{Seek, SeekFrom, Write},
    path::PathBuf,
};
use tokio::io::AsyncReadExt;
use uuid::Uuid;

use crate::{
    auth::{cleanup_expired_web_login_sessions, create_web_login_session, is_authenticated},
    helpers::{
        broadcast_message, build_chat_message, build_set_cookie, create_qr_png,
        current_http_address_blocking, image_extension_from_content_type,
        image_extension_from_filename, parse_range, relative_path, resolve_unique_name,
        sanitize_path,
    },
    shared::{
        AppState, ConfigResponse, DownloadParams, FileEntry, FileListParams, FileListResponse,
        UploadFinishForm, UploadInitParams, UploadInitResponse, COOKIE_NAME, WEB_AUTH_COOKIE_NAME,
    },
};

pub(crate) async fn root_handler(State(state): State<AppState>) -> Response {
    serve_index(&state.config.web_root).await
}

pub(crate) async fn chat_handler(State(state): State<AppState>) -> Response {
    serve_index(&state.config.web_root).await
}

pub(crate) async fn files_handler(State(state): State<AppState>, headers: HeaderMap) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    serve_file(
        &state.config.web_root,
        "files.html",
        "text/html; charset=utf-8",
    )
    .await
}

pub(crate) async fn login_handler(
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> Response {
    let token = params.get("token").cloned().unwrap_or_default();
    if token == state.config.http_auth_token {
        return Response::builder()
            .status(StatusCode::FOUND)
            .header(header::LOCATION, "/files")
            .header(header::SET_COOKIE, build_set_cookie(COOKIE_NAME, &token))
            .body(Body::empty())
            .unwrap();
    }
    serve_login_page(&state).await
}

pub(crate) async fn web_login_handler(State(state): State<AppState>) -> Response {
    serve_file(
        &state.config.web_root,
        "web-login.html",
        "text/html; charset=utf-8",
    )
    .await
}

pub(crate) async fn serve_index(web_root: &str) -> Response {
    serve_file(web_root, "index.html", "text/html; charset=utf-8").await
}

pub(crate) async fn serve_file(web_root: &str, name: &str, content_type: &str) -> Response {
    let path = PathBuf::from(web_root).join(name);
    match tokio::fs::read(&path).await {
        Ok(bytes) => Response::builder()
            .header(header::CONTENT_TYPE, content_type)
            .body(Body::from(bytes))
            .unwrap(),
        Err(_) => Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::from("Not found"))
            .unwrap(),
    }
}

pub(crate) async fn serve_login_page(state: &AppState) -> Response {
    serve_file(
        &state.config.web_root,
        "login.html",
        "text/html; charset=utf-8",
    )
    .await
}

pub(crate) async fn config_handler(State(_state): State<AppState>) -> impl IntoResponse {
    let address = current_http_address_blocking();
    Json(ConfigResponse {
        ws_address: address.replace("http://", "ws://") + "/ws",
        http_address: address.clone(),
        rust: true,
    })
}

pub(crate) async fn device_handler(State(state): State<AppState>, headers: HeaderMap) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let mut device = state.config.device_info.clone();
    device["serverTime"] = json!(Local::now().format("%Y-%m-%d %H:%M:%S").to_string());
    Json(device).into_response()
}

pub(crate) async fn web_login_session_handler(State(state): State<AppState>) -> impl IntoResponse {
    cleanup_expired_web_login_sessions(&state).await;
    let session = create_web_login_session();
    let login_url = current_http_address_blocking() + "/web-login";

    state
        .web_login_sessions
        .lock()
        .await
        .insert(session.session_id.clone(), session.clone());

    Json(json!({
        "sessionId": session.session_id,
        "token": session.token,
        "loginUrl": login_url
    }))
}

pub(crate) async fn confirm_handler(
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> impl IntoResponse {
    cleanup_expired_web_login_sessions(&state).await;
    let session_id = params.get("session").cloned().unwrap_or_default();
    let token = params.get("token").cloned().unwrap_or_default();

    let mut sessions = state.web_login_sessions.lock().await;
    let authenticated = if let Some(session) = sessions.get_mut(&session_id) {
        if session.token == token {
            session.authenticated = true;
            true
        } else {
            false
        }
    } else {
        false
    };

    let status = if authenticated {
        StatusCode::OK
    } else {
        StatusCode::UNAUTHORIZED
    };
    (status, Json(json!({ "success": authenticated })))
}

pub(crate) async fn session_status_handler(
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> Response {
    cleanup_expired_web_login_sessions(&state).await;
    let session_id = params.get("session").cloned().unwrap_or_default();
    let sessions = state.web_login_sessions.lock().await;
    if let Some(session) = sessions.get(&session_id) {
        let mut builder = Response::builder()
            .status(StatusCode::OK)
            .header(header::CONTENT_TYPE, "application/json");
        if session.authenticated {
            builder = builder.header(
                header::SET_COOKIE,
                build_set_cookie(WEB_AUTH_COOKIE_NAME, &session.auth_token),
            );
        }
        return builder
            .body(Body::from(
                json!({ "authenticated": session.authenticated }).to_string(),
            ))
            .unwrap();
    }
    Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(json!({ "authenticated": false }).to_string()))
        .unwrap()
}

pub(crate) async fn qr_png_handler(Query(params): Query<HashMap<String, String>>) -> Response {
    let Some(data) = params.get("data") else {
        return Response::builder()
            .status(StatusCode::BAD_REQUEST)
            .body(Body::from("Missing data"))
            .unwrap();
    };
    let Some(bytes) = create_qr_png(data) else {
        return Response::builder()
            .status(StatusCode::INTERNAL_SERVER_ERROR)
            .body(Body::from("Failed to generate QR code"))
            .unwrap();
    };
    Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, "image/png")
        .body(Body::from(bytes))
        .unwrap()
}

pub(crate) async fn api_files_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<FileListParams>,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let relative = params.path.unwrap_or_default();
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &relative);

    if !target.exists() || !target.is_dir() {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({"error": "Invalid directory"})),
        )
            .into_response();
    }

    let mut files = Vec::new();
    if let Ok(entries) = fs::read_dir(&target) {
        for entry in entries.flatten() {
            if let Ok(meta) = entry.metadata() {
                let name = entry.file_name().to_string_lossy().to_string();
                let path = relative_path(&base, &entry.path());
                let modified = meta
                    .modified()
                    .ok()
                    .and_then(|time| time.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|duration| {
                        let dt = chrono::DateTime::from_timestamp(duration.as_secs() as i64, 0)
                            .unwrap_or_else(|| chrono::DateTime::UNIX_EPOCH);
                        dt.format("%Y-%m-%d %H:%M:%S").to_string()
                    })
                    .unwrap_or_else(|| "-".to_string());
                files.push(FileEntry {
                    name,
                    path,
                    directory: meta.is_dir(),
                    size: meta.len(),
                    modified,
                });
            }
        }
    }

    files.sort_by(|a, b| b.directory.cmp(&a.directory).then(a.name.cmp(&b.name)));

    let parent = if relative.is_empty() {
        None
    } else {
        PathBuf::from(&relative)
            .parent()
            .map(|path| path.to_string_lossy().to_string())
    };

    (
        StatusCode::OK,
        Json(json!(FileListResponse {
            path: relative,
            parent,
            files,
        })),
    )
        .into_response()
}

pub(crate) async fn api_download_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<DownloadParams>,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &params.path);

    if !target.exists() || target.is_dir() {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::from("Not found"))
            .unwrap();
    }

    let file_size = match fs::metadata(&target) {
        Ok(metadata) => metadata.len(),
        Err(_) => {
            return Response::builder()
                .status(StatusCode::NOT_FOUND)
                .body(Body::from("Not found"))
                .unwrap()
        }
    };

    let range_header = headers
        .get(header::RANGE)
        .and_then(|value| value.to_str().ok());

    if let Some(range) = range_header {
        if let Some((start, end)) = parse_range(range, file_size) {
            let len = end - start + 1;
            let file = match File::open(&target) {
                Ok(mut value) => {
                    let _ = value.seek(SeekFrom::Start(start));
                    value
                }
                Err(_) => {
                    return Response::builder()
                        .status(StatusCode::NOT_FOUND)
                        .body(Body::from("Not found"))
                        .unwrap()
                }
            };

            let stream =
                tokio_util::io::ReaderStream::new(tokio::fs::File::from_std(file).take(len));
            return Response::builder()
                .status(StatusCode::PARTIAL_CONTENT)
                .header(header::CONTENT_TYPE, "application/octet-stream")
                .header(header::CONTENT_LENGTH, len.to_string())
                .header(
                    header::CONTENT_RANGE,
                    format!("bytes {}-{}/{}", start, end, file_size),
                )
                .header(header::ACCEPT_RANGES, "bytes")
                .body(Body::from_stream(stream))
                .unwrap();
        }
    }

    let stream = tokio_util::io::ReaderStream::new(match tokio::fs::File::open(&target).await {
        Ok(file) => file,
        Err(_) => {
            return Response::builder()
                .status(StatusCode::NOT_FOUND)
                .body(Body::from("Not found"))
                .unwrap()
        }
    });

    let mime = mime_guess::from_path(&target)
        .first_or_octet_stream()
        .to_string();
    let mut builder = Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, mime.clone())
        .header(header::CONTENT_LENGTH, file_size.to_string())
        .header(header::ACCEPT_RANGES, "bytes");
    if mime.starts_with("application/") || mime.starts_with("video/") || mime.starts_with("audio/")
    {
        let safe_name = target
            .file_name()
            .map(|name| name.to_string_lossy().replace('"', "'"))
            .unwrap_or_else(|| "file".to_string());
        builder = builder.header(
            header::CONTENT_DISPOSITION,
            format!("attachment; filename=\"{}\"", safe_name),
        );
    }
    builder.body(Body::from_stream(stream)).unwrap()
}

pub(crate) async fn api_download_head_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<DownloadParams>,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &params.path);

    if !target.exists() || target.is_dir() {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::empty())
            .unwrap();
    }

    let (file_size, mime) = match fs::metadata(&target) {
        Ok(metadata) => {
            let mime = mime_guess::from_path(&target)
                .first_or_octet_stream()
                .to_string();
            (metadata.len(), mime)
        }
        Err(_) => {
            return Response::builder()
                .status(StatusCode::NOT_FOUND)
                .body(Body::empty())
                .unwrap()
        }
    };

    let mut builder = Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, mime.clone())
        .header(header::CONTENT_LENGTH, file_size.to_string())
        .header(header::ACCEPT_RANGES, "bytes");

    if mime.starts_with("application/") || mime.starts_with("video/") || mime.starts_with("audio/")
    {
        let safe_name = target
            .file_name()
            .map(|name| name.to_string_lossy().replace('"', "'"))
            .unwrap_or_else(|| "file".to_string());
        builder = builder.header(
            header::CONTENT_DISPOSITION,
            format!("attachment; filename=\"{}\"", safe_name),
        );
    }

    builder.body(Body::empty()).unwrap()
}

pub(crate) async fn api_view_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<DownloadParams>,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &params.path);

    if !target.exists() || target.is_dir() {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::from("Not found"))
            .unwrap();
    }

    let mime = mime_guess::from_path(&target)
        .first_or_octet_stream()
        .to_string();

    let stream = tokio_util::io::ReaderStream::new(match tokio::fs::File::open(&target).await {
        Ok(file) => file,
        Err(_) => {
            return Response::builder()
                .status(StatusCode::NOT_FOUND)
                .body(Body::from("Not found"))
                .unwrap()
        }
    });

    Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, mime)
        .body(Body::from_stream(stream))
        .unwrap()
}

pub(crate) async fn api_upload_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<UploadInitParams>,
    mut multipart: Multipart,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let base = PathBuf::from(&state.config.upload_root);
    let target_dir = sanitize_path(&base, &params.path.unwrap_or_default());

    while let Ok(Some(field)) = multipart.next_field().await {
        let file_name: Option<&str> = field.file_name();
        if let Some(name) = file_name {
            let file_name = name.to_string();
            let data = match field.bytes().await {
                Ok(bytes) => bytes,
                Err(_) => continue,
            };
            let target = target_dir.join(&file_name);
            if let Some(parent) = target.parent() {
                let _ = fs::create_dir_all(parent);
            }
            if let Err(err) = fs::write(&target, &data) {
                error!("Failed to write uploaded file: {}", err);
                return Json(json!({"success": false, "error": err.to_string()})).into_response();
            }
            return Json(json!({"success": true, "path": relative_path(&base, &target)}))
                .into_response();
        }
    }

    Json(json!({"success": false, "error": "No file"})).into_response()
}

pub(crate) async fn api_upload_init_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(params): Query<UploadInitParams>,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let upload_id = params.id;
    let base = PathBuf::from(&state.config.upload_root);
    let target_dir = sanitize_path(&base, &params.path.unwrap_or_default());
    let temp_path = PathBuf::from(&state.config.upload_root)
        .join(".rust_upload_tmp")
        .join(&upload_id);

    if let Some(parent) = temp_path.parent() {
        let _ = fs::create_dir_all(parent);
    }

    let session = crate::shared::UploadSession {
        temp_path,
        target_dir,
        file_name: params.name,
        last_active_at: std::time::Instant::now(),
    };

    state
        .upload_sessions
        .lock()
        .await
        .insert(upload_id.clone(), session);

    Json(UploadInitResponse {
        success: true,
        upload_id,
    })
    .into_response()
}

pub(crate) async fn api_upload_chunk_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    mut multipart: Multipart,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let mut upload_id: Option<String> = None;
    let mut chunk_data: Option<Bytes> = None;

    while let Ok(Some(field)) = multipart.next_field().await {
        let field_name: Option<&str> = field.name();
        match field_name {
            Some("id") => upload_id = field.text().await.ok(),
            Some("chunk") => chunk_data = field.bytes().await.ok(),
            _ => {}
        }
    }

    let upload_id = match upload_id {
        Some(id) => id,
        None => return Json(json!({"success": false, "error": "Missing id"})).into_response(),
    };
    let chunk_data = match chunk_data {
        Some(data) => data,
        None => return Json(json!({"success": false, "error": "Missing chunk"})).into_response(),
    };

    let mut sessions = state.upload_sessions.lock().await;
    let session = match sessions.get_mut(&upload_id) {
        Some(value) => value,
        None => {
            return Json(json!({"success": false, "error": "Invalid upload_id"})).into_response()
        }
    };

    session.last_active_at = std::time::Instant::now();

    let mut file = match File::options()
        .create(true)
        .append(true)
        .open(&session.temp_path)
    {
        Ok(value) => value,
        Err(err) => {
            return Json(json!({"success": false, "error": err.to_string()})).into_response()
        }
    };

    if let Err(err) = file.write_all(&chunk_data) {
        return Json(json!({"success": false, "error": err.to_string()})).into_response();
    }

    Json(json!({"success": true})).into_response()
}

pub(crate) async fn api_upload_finish_handler(
    State(state): State<AppState>,
    headers: HeaderMap,
    Form(form): Form<UploadFinishForm>,
) -> Response {
    if !is_authenticated(&headers, &state).await {
        return serve_login_page(&state).await;
    }
    let session = {
        let mut sessions = state.upload_sessions.lock().await;
        sessions.remove(&form.id)
    };

    let session = match session {
        Some(value) => value,
        None => {
            return Json(json!({"success": false, "error": "Invalid upload_id"})).into_response()
        }
    };

    let target = session.target_dir.join(&session.file_name);
    if let Some(parent) = target.parent() {
        let _ = fs::create_dir_all(parent);
    }

    let unique_target = resolve_unique_name(&target);
    let saved_name = unique_target
        .file_name()
        .map(|name| name.to_string_lossy().to_string())
        .unwrap_or_else(|| session.file_name);

    match fs::rename(&session.temp_path, &unique_target) {
        Ok(_) => Json(json!({
            "success": true,
            "name": saved_name
        }))
        .into_response(),
        Err(err) => Json(json!({"success": false, "error": err.to_string()})).into_response(),
    }
}

pub(crate) async fn api_chat_send_text_handler(
    State(state): State<AppState>,
    Form(form): Form<HashMap<String, String>>,
) -> Response {
    let message = form
        .get("message")
        .map(|value| value.trim().to_string())
        .unwrap_or_default();
    if message.is_empty() {
        return Json(json!({"success": false, "error": "Missing message"})).into_response();
    }

    let payload = build_chat_message("Station RX", &message, "text", None);
    broadcast_message(&state, payload).await;
    Json(json!({"success": true})).into_response()
}

pub(crate) async fn api_chat_upload_image_handler(
    State(state): State<AppState>,
    mut multipart: Multipart,
) -> Response {
    let images_root = PathBuf::from(&state.config.chat_images_root);
    let _ = fs::create_dir_all(&images_root);

    while let Ok(Some(field)) = multipart.next_field().await {
        let file_name: Option<&str> = field.file_name();
        if let Some(name) = file_name {
            let original_name = name.to_string();
            let content_type = field
                .content_type()
                .map(|value| value.to_string())
                .unwrap_or_else(|| "image/jpeg".to_string());
            let data = match field.bytes().await {
                Ok(bytes) => bytes,
                Err(_) => continue,
            };

            let ext = image_extension_from_filename(&original_name)
                .or_else(|| Some(image_extension_from_content_type(&content_type)))
                .unwrap_or_else(|| ".jpg".to_string());

            let now = Local::now();
            let timestamp = now.format("%Y%m%d_%H%M%S");
            let millis = (now.timestamp_millis() % 1000) as u16;
            let suffix = &Uuid::new_v4().to_string()[..4];
            let base_name = format!("IMG_{}_{:03}_{}", timestamp, millis, suffix);
            let mut saved_file_name = format!("{}{}", base_name, ext);

            let mut target = images_root.join(&saved_file_name);
            if target.exists() {
                let mut index = 1;
                loop {
                    let candidate = images_root.join(format!("{}_{}{}", base_name, index, ext));
                    if !candidate.exists() {
                        saved_file_name =
                            candidate.file_name().unwrap().to_string_lossy().to_string();
                        target = candidate;
                        break;
                    }
                    index += 1;
                }
            }

            if let Err(err) = fs::write(&target, &data) {
                error!("Failed to write chat image: {}", err);
                return Json(json!({"success": false, "error": err.to_string()})).into_response();
            }

            let image_url = format!("/images/{}", saved_file_name);
            let payload = build_chat_message("Station RX", "[Image]", "image", Some(&image_url));
            broadcast_message(&state, payload).await;

            return Json(json!({"success": true, "url": image_url, "name": saved_file_name}))
                .into_response();
        }
    }

    Json(json!({"success": false, "error": "No image"})).into_response()
}

pub(crate) async fn chat_image_handler(
    State(state): State<AppState>,
    Path(file_name): Path<String>,
) -> impl IntoResponse {
    let images_root = PathBuf::from(&state.config.chat_images_root);
    let target = sanitize_path(&images_root, &file_name);

    if !target.exists() || target.is_dir() {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::from("Not found"))
            .unwrap();
    }

    let mime = mime_guess::from_path(&target)
        .first_or_octet_stream()
        .to_string();

    match tokio::fs::read(&target).await {
        Ok(bytes) => Response::builder()
            .header(header::CONTENT_TYPE, mime)
            .body(Body::from(bytes))
            .unwrap(),
        Err(_) => Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::from("Not found"))
            .unwrap(),
    }
}
