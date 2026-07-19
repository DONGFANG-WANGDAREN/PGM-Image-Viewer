use axum::{
    extract::DefaultBodyLimit,
    routing::{get, post},
    Router,
};
use log::info;
use std::{collections::HashMap, net::SocketAddr, str::FromStr, sync::atomic::Ordering, sync::Arc};
use tokio::{
    net::TcpListener,
    sync::{broadcast, Mutex as TokioMutex, Notify},
};
use tower_http::{cors::CorsLayer, limit::RequestBodyLimitLayer, services::ServeDir};

use crate::{
    handlers::{
        api_chat_upload_image_handler, api_download_handler, api_download_head_handler,
        api_files_handler, api_upload_chunk_handler, api_upload_finish_handler, api_upload_handler,
        api_upload_init_handler, api_view_handler, chat_handler, chat_image_handler,
        config_handler, confirm_handler, device_handler, files_handler, login_handler,
        qr_png_handler, root_handler, session_status_handler, web_login_handler,
        web_login_session_handler,
    },
    helpers::resolve_chat_log_path,
    shared::{
        AppConfig, AppState, ChatLogState, ChatState, HTTP_ADDRESS, MESSAGE_BROADCASTER,
        SERVER_RUNNING,
    },
    ws::ws_handler,
};

pub(crate) fn build_router(state: AppState) -> Router {
    let cors = CorsLayer::permissive();
    let web_root = state.config.web_root.clone();

    Router::new()
        .route("/", get(root_handler))
        .route("/chat", get(chat_handler))
        .route("/files", get(files_handler))
        .route("/login", get(login_handler))
        .route("/web-login", get(web_login_handler))
        .route("/api/qr.png", get(qr_png_handler))
        .route("/api/confirm", get(confirm_handler))
        .route("/api/session-status", get(session_status_handler))
        .route("/api/config", get(config_handler))
        .route("/api/web-login-session", get(web_login_session_handler))
        .route("/api/device", get(device_handler))
        .route("/api/files", get(api_files_handler))
        .route(
            "/api/download",
            get(api_download_handler).head(api_download_head_handler),
        )
        .route("/api/view", get(api_view_handler))
        .route("/api/upload", post(api_upload_handler))
        .route("/api/upload-init", get(api_upload_init_handler))
        .route("/api/upload-chunk", post(api_upload_chunk_handler))
        .route("/api/upload-finish", post(api_upload_finish_handler))
        .route(
            "/api/chat/upload-image",
            post(api_chat_upload_image_handler),
        )
        .route("/images/:file_name", get(chat_image_handler))
        .route("/ws", get(ws_handler))
        .fallback_service(ServeDir::new(web_root))
        .layer(DefaultBodyLimit::disable())
        .layer(RequestBodyLimitLayer::new(10 * 1024 * 1024 * 1024))
        .layer(cors)
        .with_state(state)
}

pub(crate) async fn run_server(
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

    let (broadcast_tx, _) = broadcast::channel(256);
    if let Some(holder) = MESSAGE_BROADCASTER.get() {
        *holder.lock().unwrap() = Some(broadcast_tx.clone());
    }
    let chat_log_path = resolve_chat_log_path(&config.upload_root);

    let state = AppState {
        config,
        chat: Arc::new(TokioMutex::new(ChatState {
            clients: HashMap::new(),
            total_messages_received: 0,
            total_messages_sent: 0,
            total_unique_users: 0,
            peak_active_users: 0,
        })),
        chat_log: Arc::new(TokioMutex::new(ChatLogState {
            file_path: chat_log_path,
        })),
        upload_sessions: Arc::new(TokioMutex::new(HashMap::new())),
        web_login_sessions: Arc::new(TokioMutex::new(HashMap::new())),
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
    if let Some(holder) = MESSAGE_BROADCASTER.get() {
        *holder.lock().unwrap() = None;
    }
    if let Some(holder) = HTTP_ADDRESS.get() {
        holder.lock().await.clear();
    }
    Ok(())
}
