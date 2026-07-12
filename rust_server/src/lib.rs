use axum::{
    body::{Body, Bytes},
    extract::{DefaultBodyLimit, Form, Multipart, Path, Query, State, WebSocketUpgrade},
    http::{header, HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use chrono::Local;
use futures::{sink::SinkExt, stream::StreamExt};
use jni::{
    objects::JString,
    sys::{jboolean, jint, JNI_VERSION_1_6, JNI_FALSE, JNI_TRUE},
    JNIEnv, JavaVM,
};
use log::{error, info};
use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    collections::HashMap,
    fs::{self, File},
    io::{Seek, SeekFrom, Write},
    net::SocketAddr,
    os::raw::c_void,
    path::{Path as StdPath, PathBuf},
    str::FromStr,
    sync::atomic::{AtomicBool, Ordering},
    sync::Arc,
    sync::Mutex as StdMutex,
};
use tokio::{
    io::AsyncReadExt,
    net::TcpListener,
    runtime::Runtime,
    sync::{broadcast, Mutex as TokioMutex, Notify},
};
use tower_http::{cors::CorsLayer, limit::RequestBodyLimitLayer, services::ServeDir};
use uuid::Uuid;

static RUNTIME: OnceCell<Runtime> = OnceCell::new();
static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);
static SHUTDOWN_NOTIFY: OnceCell<Arc<StdMutex<Option<Arc<Notify>>>>> = OnceCell::new();
static HTTP_ADDRESS: OnceCell<Arc<TokioMutex<String>>> = OnceCell::new();
static MESSAGE_BROADCASTER: OnceCell<broadcast::Sender<String>> = OnceCell::new();

#[derive(Clone)]
struct AppConfig {
    web_root: String,
    upload_root: String,
    chat_images_root: String,
    device_info: Value,
}

#[allow(dead_code)]
struct ChatClient {
    username: String,
    connected_at: i64,
    message_count: u64,
}

struct ChatState {
    clients: HashMap<String, ChatClient>,
    total_messages_received: u64,
    total_messages_sent: u64,
    total_unique_users: u64,
    peak_active_users: usize,
}

#[derive(Clone)]
struct AppState {
    config: Arc<AppConfig>,
    chat: Arc<TokioMutex<ChatState>>,
    upload_sessions: Arc<TokioMutex<HashMap<String, UploadSession>>>,
}

struct UploadSession {
    temp_path: PathBuf,
    target_dir: PathBuf,
    file_name: String,
    last_active_at: std::time::Instant,
}

#[derive(Serialize)]
struct ConfigResponse {
    ws_address: String,
    http_address: String,
    rust: bool,
}

#[derive(Serialize)]
struct FileEntry {
    name: String,
    path: String,
    directory: bool,
    size: u64,
    modified: String,
}

#[derive(Serialize)]
struct FileListResponse {
    path: String,
    parent: Option<String>,
    files: Vec<FileEntry>,
}

#[derive(Deserialize)]
struct FileListParams {
    path: Option<String>,
}

#[derive(Deserialize)]
struct DownloadParams {
    path: String,
}

#[derive(Deserialize)]
struct UploadInitParams {
    path: Option<String>,
    name: String,
    id: String,
}

#[derive(Deserialize)]
struct UploadFinishForm {
    id: String,
}

#[derive(Serialize)]
struct UploadInitResponse {
    success: bool,
    upload_id: String,
}

fn init_logging() {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Debug),
    );
}

fn current_time_iso() -> String {
    Local::now().format("%Y-%m-%dT%H:%M:%S.%3fZ").to_string()
}

fn build_chat_message(sender: &str, message: &str, message_type: &str, url: Option<&str>) -> String {
    let mut obj = json!({
        "timestamp": current_time_iso(),
        "from": sender,
        "message": message,
        "system": false,
        "type": message_type,
    });
    if let Some(u) = url {
        obj["url"] = json!(u);
    }
    obj.to_string()
}

fn build_system_message(message: &str) -> String {
    json!({
        "timestamp": current_time_iso(),
        "from": "System",
        "message": message,
        "system": true,
        "type": "text",
    })
    .to_string()
}

async fn root_handler(State(state): State<AppState>) -> Response {
    serve_index(&state.config.web_root).await
}

async fn chat_handler(State(state): State<AppState>) -> Response {
    serve_index(&state.config.web_root).await
}

async fn files_handler(State(state): State<AppState>) -> Response {
    serve_index(&state.config.web_root).await
}

async fn login_handler(State(state): State<AppState>) -> Response {
    serve_file(&state.config.web_root, "login.html", "text/html; charset=utf-8").await
}

async fn web_login_handler(State(state): State<AppState>) -> Response {
    serve_file(&state.config.web_root, "web-login.html", "text/html; charset=utf-8").await
}

async fn serve_index(web_root: &str) -> Response {
    serve_file(web_root, "index.html", "text/html; charset=utf-8").await
}

async fn serve_file(web_root: &str, name: &str, content_type: &str) -> Response {
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

async fn config_handler(State(_state): State<AppState>) -> impl IntoResponse {
    let address = HTTP_ADDRESS
        .get()
        .map(|m| m.blocking_lock().clone())
        .unwrap_or_default();
    Json(ConfigResponse {
        ws_address: address.replace("http://", "ws://") + "/ws",
        http_address: address.clone(),
        rust: true,
    })
}

async fn device_handler(State(state): State<AppState>) -> impl IntoResponse {
    let mut device = state.config.device_info.clone();
    device["serverTime"] = json!(Local::now().format("%Y-%m-%d %H:%M:%S").to_string());
    Json(device)
}

async fn api_files_handler(
    State(state): State<AppState>,
    Query(params): Query<FileListParams>,
) -> impl IntoResponse {
    let relative = params.path.unwrap_or_default();
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &relative);

    if !target.exists() || !target.is_dir() {
        return (
            StatusCode::BAD_REQUEST,
            Json(json!({"error": "Invalid directory"})),
        );
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
                    .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|d| {
                        let dt = chrono::DateTime::from_timestamp(d.as_secs() as i64, 0)
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
            .map(|p| p.to_string_lossy().to_string())
    };

    (
        StatusCode::OK,
        Json(json!(FileListResponse {
            path: relative,
            parent,
            files,
        })),
    )
}

async fn api_download_handler(
    State(state): State<AppState>,
    Query(params): Query<DownloadParams>,
    headers: HeaderMap,
) -> impl IntoResponse {
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &params.path);

    if !target.exists() || target.is_dir() {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::from("Not found"))
            .unwrap();
    }

    let file_size = match fs::metadata(&target) {
        Ok(m) => m.len(),
        Err(_) => {
            return Response::builder()
                .status(StatusCode::NOT_FOUND)
                .body(Body::from("Not found"))
                .unwrap()
        }
    };

    let range_header = headers.get(header::RANGE).and_then(|v| v.to_str().ok());

    if let Some(range) = range_header {
        if let Some((start, end)) = parse_range(range, file_size) {
            let len = end - start + 1;
            let file = match File::open(&target) {
                Ok(mut f) => {
                    let _ = f.seek(SeekFrom::Start(start));
                    f
                }
                Err(_) => {
                    return Response::builder()
                        .status(StatusCode::NOT_FOUND)
                        .body(Body::from("Not found"))
                        .unwrap()
                }
            };

            let stream = tokio_util::io::ReaderStream::new(
                tokio::fs::File::from_std(file).take(len),
            );
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

    let stream = tokio_util::io::ReaderStream::new(
        match tokio::fs::File::open(&target).await {
            Ok(f) => f,
            Err(_) => {
                return Response::builder()
                    .status(StatusCode::NOT_FOUND)
                    .body(Body::from("Not found"))
                    .unwrap()
            }
        },
    );

    let mime = mime_guess::from_path(&target)
        .first_or_octet_stream()
        .to_string();
    let mut builder = Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, mime.clone())
        .header(header::CONTENT_LENGTH, file_size.to_string())
        .header(header::ACCEPT_RANGES, "bytes");
    if mime.starts_with("application/") || mime.starts_with("video/") || mime.starts_with("audio/") {
        let safe_name = target
            .file_name()
            .map(|n| n.to_string_lossy().replace('"', "'"))
            .unwrap_or_else(|| "file".to_string());
        builder = builder.header(
            header::CONTENT_DISPOSITION,
            format!("attachment; filename=\"{}\"", safe_name),
        );
    }
    builder.body(Body::from_stream(stream)).unwrap()
}

async fn api_download_head_handler(
    State(state): State<AppState>,
    Query(params): Query<DownloadParams>,
) -> impl IntoResponse {
    let base = PathBuf::from(&state.config.upload_root);
    let target = sanitize_path(&base, &params.path);

    if !target.exists() || target.is_dir() {
        return Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(Body::empty())
            .unwrap();
    }

    let (file_size, mime) = match fs::metadata(&target) {
        Ok(m) => {
            let mime = mime_guess::from_path(&target)
                .first_or_octet_stream()
                .to_string();
            (m.len(), mime)
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

    if mime.starts_with("application/") || mime.starts_with("video/") || mime.starts_with("audio/") {
        let safe_name = target
            .file_name()
            .map(|n| n.to_string_lossy().replace('"', "'"))
            .unwrap_or_else(|| "file".to_string());
        builder = builder.header(
            header::CONTENT_DISPOSITION,
            format!("attachment; filename=\"{}\"", safe_name),
        );
    }

    builder.body(Body::empty()).unwrap()
}

async fn api_view_handler(
    State(state): State<AppState>,
    Query(params): Query<DownloadParams>,
) -> impl IntoResponse {
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

    let stream = tokio_util::io::ReaderStream::new(
        match tokio::fs::File::open(&target).await {
            Ok(f) => f,
            Err(_) => {
                return Response::builder()
                    .status(StatusCode::NOT_FOUND)
                    .body(Body::from("Not found"))
                    .unwrap()
            }
        },
    );

    Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, mime)
        .body(Body::from_stream(stream))
        .unwrap()
}

async fn api_upload_handler(
    State(state): State<AppState>,
    Query(params): Query<UploadInitParams>,
    mut multipart: Multipart,
) -> impl IntoResponse {
    let base = PathBuf::from(&state.config.upload_root);
    let target_dir = sanitize_path(&base, &params.path.unwrap_or_default());

    while let Ok(Some(field)) = multipart.next_field().await {
        let file_name: Option<&str> = field.file_name();
        if let Some(name) = file_name {
            let name = name.to_string();
            let data = match field.bytes().await {
                Ok(b) => b,
                Err(_) => continue,
            };
            let target = target_dir.join(&name);
            if let Some(parent) = target.parent() {
                let _ = fs::create_dir_all(parent);
            }
            if let Err(e) = fs::write(&target, &data) {
                error!("Failed to write uploaded file: {}", e);
                return Json(json!({"success": false, "error": e.to_string()}));
            }
            return Json(json!({"success": true, "path": relative_path(&base, &target)}));
        }
    }

    Json(json!({"success": false, "error": "No file"}))
}

async fn api_upload_init_handler(
    State(state): State<AppState>,
    Query(params): Query<UploadInitParams>,
) -> impl IntoResponse {
    let upload_id = params.id;
    let base = PathBuf::from(&state.config.upload_root);
    let target_dir = sanitize_path(&base, &params.path.unwrap_or_default());
    let temp_path = PathBuf::from(&state.config.upload_root)
        .join(".rust_upload_tmp")
        .join(&upload_id);

    if let Some(parent) = temp_path.parent() {
        let _ = fs::create_dir_all(parent);
    }

    let session = UploadSession {
        temp_path,
        target_dir,
        file_name: params.name,
        last_active_at: std::time::Instant::now(),
    };

    state.upload_sessions.lock().await.insert(upload_id.clone(), session);

    Json(UploadInitResponse {
        success: true,
        upload_id,
    })
}

async fn api_upload_chunk_handler(
    State(state): State<AppState>,
    mut multipart: Multipart,
) -> impl IntoResponse {
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
        None => return Json(json!({"success": false, "error": "Missing id"})),
    };
    let chunk_data = match chunk_data {
        Some(data) => data,
        None => return Json(json!({"success": false, "error": "Missing chunk"})),
    };

    let mut sessions = state.upload_sessions.lock().await;
    let session = match sessions.get_mut(&upload_id) {
        Some(s) => s,
        None => return Json(json!({"success": false, "error": "Invalid upload_id"})),
    };

    session.last_active_at = std::time::Instant::now();

    let mut file = match File::options().create(true).append(true).open(&session.temp_path) {
        Ok(f) => f,
        Err(e) => return Json(json!({"success": false, "error": e.to_string()})),
    };

    if let Err(e) = file.write_all(&chunk_data) {
        return Json(json!({"success": false, "error": e.to_string()}));
    }

    Json(json!({"success": true}))
}

async fn api_upload_finish_handler(
    State(state): State<AppState>,
    Form(form): Form<UploadFinishForm>,
) -> impl IntoResponse {
    let session = {
        let mut sessions = state.upload_sessions.lock().await;
        sessions.remove(&form.id)
    };

    let session = match session {
        Some(s) => s,
        None => return Json(json!({"success": false, "error": "Invalid upload_id"})),
    };

    let target = session.target_dir.join(&session.file_name);
    if let Some(parent) = target.parent() {
        let _ = fs::create_dir_all(parent);
    }

    let unique_target = resolve_unique_name(&target);
    let saved_name = unique_target
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| session.file_name);

    match fs::rename(&session.temp_path, &unique_target) {
        Ok(_) => {
            Json(json!({
                "success": true,
                "name": saved_name
            }))
        }
        Err(e) => Json(json!({"success": false, "error": e.to_string()})),
    }
}

async fn api_chat_upload_image_handler(
    State(state): State<AppState>,
    mut multipart: Multipart,
) -> impl IntoResponse {
    let images_root = PathBuf::from(&state.config.chat_images_root);
    let _ = fs::create_dir_all(&images_root);

    while let Ok(Some(field)) = multipart.next_field().await {
        let file_name: Option<&str> = field.file_name();
        if file_name.is_some() {
            let original_name = file_name.unwrap().to_string();
            let content_type: Option<&str> = field.content_type();
            let content_type = content_type.map(|s| s.to_string()).unwrap_or_else(|| "image/jpeg".to_string());
            let data = match field.bytes().await {
                Ok(b) => b,
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
            let mut file_name = format!("{}{}", base_name, ext);

            let mut target = images_root.join(&file_name);
            if target.exists() {
                let mut index = 1;
                loop {
                    let candidate = images_root.join(format!("{}_{}{}", base_name, index, ext));
                    if !candidate.exists() {
                        file_name = candidate.file_name().unwrap().to_string_lossy().to_string();
                        target = candidate;
                        break;
                    }
                    index += 1;
                }
            }

            if let Err(e) = fs::write(&target, &data) {
                error!("Failed to write chat image: {}", e);
                return Json(json!({"success": false, "error": e.to_string()}));
            }

            let image_url = format!("/images/{}", file_name);
            let sender = "Station RX";
            let payload = build_chat_message(sender, "[Image]", "image", Some(&image_url));

            if let Some(tx) = MESSAGE_BROADCASTER.get() {
                let _ = tx.send(payload);
            }

            return Json(json!({"success": true, "url": image_url, "name": file_name}));
        }
    }

    Json(json!({"success": false, "error": "No image"}))
}

async fn chat_image_handler(
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

async fn ws_handler(
    Query(params): Query<HashMap<String, String>>,
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let username = params
        .get("name")
        .cloned()
        .unwrap_or_else(|| format!("User-{}", &Uuid::new_v4().to_string()[..4]));
    ws.on_upgrade(move |socket| handle_socket(socket, username, state))
}

async fn handle_socket(socket: axum::extract::ws::WebSocket, username: String, state: AppState) {
    let (mut sender, mut receiver) = socket.split();
    let broadcast_tx = MESSAGE_BROADCASTER
        .get()
        .expect("broadcaster not initialized")
        .clone();
    let mut broadcast_rx = broadcast_tx.subscribe();

    let client_id = Uuid::new_v4().to_string();
    let connected_at = Local::now().timestamp_millis();

    {
        let mut chat = state.chat.lock().await;
        chat.clients.insert(
            client_id.clone(),
            ChatClient {
                username: username.clone(),
                connected_at,
                message_count: 0,
            },
        );
        chat.total_unique_users += 1;
        chat.peak_active_users = chat.peak_active_users.max(chat.clients.len());
    }

    let join_msg = build_system_message(&format!("{} 进入聊天室", username));
    let _ = broadcast_tx.send(join_msg);

    let send_task = {
        tokio::spawn(async move {
            while let Ok(msg) = broadcast_rx.recv().await {
                if sender.send(axum::extract::ws::Message::Text(msg)).await.is_err() {
                    break;
                }
            }
            let _ = sender.close().await;
        })
    };

    let recv_task = {
        let client_id = client_id.clone();
        let username = username.clone();
        let broadcast_tx = broadcast_tx.clone();
        let chat_state = state.chat.clone();
        tokio::spawn(async move {
            while let Some(Ok(msg)) = receiver.next().await {
                match msg {
                    axum::extract::ws::Message::Text(text) => {
                        let mut chat = chat_state.lock().await;
                        chat.total_messages_received += 1;
                        if let Some(client) = chat.clients.get_mut(&client_id) {
                            client.message_count += 1;
                        }
                        drop(chat);

                        let payload = build_chat_message(&username, &text, "text", None);
                        let _ = broadcast_tx.send(payload);

                        let mut chat = chat_state.lock().await;
                        chat.total_messages_sent += 1;
                    }
                    axum::extract::ws::Message::Close(_) => break,
                    _ => {}
                }
            }
        })
    };

    tokio::select! {
        _ = send_task => {},
        _ = recv_task => {},
    }

    {
        let mut chat = state.chat.lock().await;
        chat.clients.remove(&client_id);
    }

    let leave_msg = build_system_message(&format!("{} 离开聊天室", username));
    let _ = broadcast_tx.send(leave_msg);
}

fn build_router(state: AppState) -> Router {
    let cors = CorsLayer::permissive();
    let web_root = state.config.web_root.clone();

    Router::new()
        .route("/", get(root_handler))
        .route("/chat", get(chat_handler))
        .route("/files", get(files_handler))
        .route("/login", get(login_handler))
        .route("/web-login", get(web_login_handler))
        .route("/api/config", get(config_handler))
        .route("/api/device", get(device_handler))
        .route("/api/files", get(api_files_handler))
        .route("/api/download", get(api_download_handler).head(api_download_head_handler))
        .route("/api/view", get(api_view_handler))
        .route("/api/upload", post(api_upload_handler))
        .route("/api/upload-init", get(api_upload_init_handler))
        .route("/api/upload-chunk", post(api_upload_chunk_handler))
        .route("/api/upload-finish", post(api_upload_finish_handler))
        .route("/api/chat/upload-image", post(api_chat_upload_image_handler))
        .route("/images/:file_name", get(chat_image_handler))
        .route("/ws", get(ws_handler))
        .fallback_service(ServeDir::new(web_root))
        .layer(DefaultBodyLimit::disable())
        .layer(RequestBodyLimitLayer::new(10 * 1024 * 1024 * 1024))
        .layer(cors)
        .with_state(state)
}

async fn run_server(
    bind_host: String,
    display_host: String,
    port: u16,
    config: Arc<AppConfig>,
    shutdown_notify: Arc<Notify>,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let addr = SocketAddr::from_str(&format!("{}:{}", bind_host, port))?;
    let listener = TcpListener::bind(addr).await?;
    let address = format!("http://{}:{}", display_host, port);

    if let Some(holder) = HTTP_ADDRESS.get() {
        *holder.lock().await = address;
    }

    let _ = MESSAGE_BROADCASTER.set(broadcast::channel(256).0);

    let state = AppState {
        config,
        chat: Arc::new(TokioMutex::new(ChatState {
            clients: HashMap::new(),
            total_messages_received: 0,
            total_messages_sent: 0,
            total_unique_users: 0,
            peak_active_users: 0,
        })),
        upload_sessions: Arc::new(TokioMutex::new(HashMap::new())),
    };

    info!("Rust server listening on {}:{}", bind_host, port);
    SERVER_RUNNING.store(true, Ordering::SeqCst);

    axum::serve(listener, build_router(state))
        .with_graceful_shutdown(async move {
            shutdown_notify.notified().await;
            info!("Rust server shutdown signal received");
        })
        .await?;

    SERVER_RUNNING.store(false, Ordering::SeqCst);
    Ok(())
}

fn sanitize_path(base: &StdPath, relative: &str) -> PathBuf {
    let mut target = base.to_path_buf();
    for part in relative.split('/').filter(|p| !p.is_empty() && *p != ".") {
        if part == ".." {
            continue;
        }
        target.push(part);
    }
    target
}

fn relative_path(base: &StdPath, target: &StdPath) -> String {
    target
        .strip_prefix(base)
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_else(|_| target.to_string_lossy().to_string())
}

fn resolve_unique_name(target: &StdPath) -> PathBuf {
    if !target.exists() {
        return target.to_path_buf();
    }
    let stem = target
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default();
    let ext = target
        .extension()
        .map(|s| format!(".{}", s.to_string_lossy()))
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

fn parse_range(range: &str, file_size: u64) -> Option<(u64, u64)> {
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

fn image_extension_from_content_type(content_type: &str) -> String {
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

fn image_extension_from_filename(file_name: &str) -> Option<String> {
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

#[no_mangle]
pub extern "C" fn JNI_OnLoad(_vm: JavaVM, _reserved: *mut c_void) -> jint {
    let _ = _vm;
    let _ = _reserved;
    init_logging();
    JNI_VERSION_1_6
}

#[no_mangle]
pub extern "system" fn Java_com_DONGFANG_1WANGDAREN_Station_1RX_rust_RustServerBridge_nativeStartServer<'local>(
    mut env: JNIEnv<'local>,
    _class: jni::objects::JClass<'local>,
    bind_host: JString<'local>,
    display_host: JString<'local>,
    port: jint,
    web_root: JString<'local>,
    upload_root: JString<'local>,
    chat_images_root: JString<'local>,
    device_info_json: JString<'local>,
) -> JString<'local> {
    init_logging();

    if SERVER_RUNNING.load(Ordering::SeqCst) {
        let msg = env.new_string("already running").unwrap();
        return msg;
    }

    let bind_host: String = env.get_string(&bind_host).unwrap().into();
    let display_host: String = env.get_string(&display_host).unwrap().into();
    let port = port as u16;
    let web_root: String = env.get_string(&web_root).unwrap().into();
    let upload_root: String = env.get_string(&upload_root).unwrap().into();
    let chat_images_root: String = env.get_string(&chat_images_root).unwrap().into();
    let device_info_json: String = env.get_string(&device_info_json).unwrap().into();

    let device_info: Value = serde_json::from_str(&device_info_json).unwrap_or_else(|_| json!({}));

    let config = Arc::new(AppConfig {
        web_root,
        upload_root,
        chat_images_root,
        device_info,
    });

    let _ = HTTP_ADDRESS.set(Arc::new(TokioMutex::new(String::new())));

    let shutdown_notify = Arc::new(Notify::new());
    let _ = SHUTDOWN_NOTIFY.set(Arc::new(StdMutex::new(Some(shutdown_notify.clone()))));

    let runtime = RUNTIME.get_or_init(|| Runtime::new().expect("Failed to create Tokio runtime"));

    runtime.spawn(async move {
        if let Err(e) = run_server(bind_host, display_host, port, config, shutdown_notify).await {
            error!("Rust server error: {}", e);
        }
    });

    let msg = env.new_string("started").unwrap();
    msg
}

#[no_mangle]
pub extern "system" fn Java_com_DONGFANG_1WANGDAREN_Station_1RX_rust_RustServerBridge_nativeStopServer(
    _env: JNIEnv,
    _class: jni::objects::JClass,
) -> jboolean {
    if let Some(holder) = SHUTDOWN_NOTIFY.get() {
        let mut guard = holder.lock().unwrap();
        if let Some(notify) = guard.take() {
            notify.notify_one();
        }
        *guard = Some(Arc::new(Notify::new()));
    }
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_DONGFANG_1WANGDAREN_Station_1RX_rust_RustServerBridge_nativeIsRunning(
    _env: JNIEnv,
    _class: jni::objects::JClass,
) -> jboolean {
    if SERVER_RUNNING.load(Ordering::SeqCst) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
