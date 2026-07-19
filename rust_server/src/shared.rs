use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::sync::atomic::AtomicBool;
use std::{collections::HashMap, path::PathBuf, sync::Arc, sync::Mutex as StdMutex};
use tokio::{
    runtime::Runtime,
    sync::{broadcast, Mutex as TokioMutex, Notify},
};

pub(crate) static RUNTIME: OnceCell<Runtime> = OnceCell::new();
pub(crate) static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);
pub(crate) static SHUTDOWN_NOTIFY: OnceCell<Arc<StdMutex<Option<Arc<Notify>>>>> = OnceCell::new();
pub(crate) static HTTP_ADDRESS: OnceCell<Arc<TokioMutex<String>>> = OnceCell::new();
pub(crate) static MESSAGE_BROADCASTER: OnceCell<Arc<StdMutex<Option<broadcast::Sender<String>>>>> =
    OnceCell::new();

pub(crate) const COOKIE_NAME: &str = "rrx_token";
pub(crate) const WEB_AUTH_COOKIE_NAME: &str = "rrx_web_auth";
pub(crate) const WEB_LOGIN_SESSION_MAX_AGE_MS: i64 = 10 * 60 * 1000;
pub(crate) const COOKIE_MAX_AGE_SECONDS: i64 = 7 * 24 * 60 * 60;

#[derive(Clone)]
pub(crate) struct AppConfig {
    pub(crate) web_root: String,
    pub(crate) upload_root: String,
    pub(crate) chat_images_root: String,
    pub(crate) device_info: Value,
    pub(crate) http_auth_token: String,
}

#[allow(dead_code)]
pub(crate) struct ChatClient {
    pub(crate) username: String,
    pub(crate) connected_at: i64,
    pub(crate) message_count: u64,
}

pub(crate) struct ChatState {
    pub(crate) clients: HashMap<String, ChatClient>,
    pub(crate) total_messages_received: u64,
    pub(crate) total_messages_sent: u64,
    pub(crate) total_unique_users: u64,
    pub(crate) peak_active_users: usize,
}

pub(crate) struct ChatLogState {
    pub(crate) file_path: PathBuf,
}

#[derive(Clone)]
pub(crate) struct WebLoginSession {
    pub(crate) session_id: String,
    pub(crate) token: String,
    pub(crate) auth_token: String,
    pub(crate) created_at_ms: i64,
    pub(crate) authenticated: bool,
}

#[derive(Clone)]
pub(crate) struct AppState {
    pub(crate) config: Arc<AppConfig>,
    pub(crate) chat: Arc<TokioMutex<ChatState>>,
    pub(crate) chat_log: Arc<TokioMutex<ChatLogState>>,
    pub(crate) upload_sessions: Arc<TokioMutex<HashMap<String, UploadSession>>>,
    pub(crate) web_login_sessions: Arc<TokioMutex<HashMap<String, WebLoginSession>>>,
}

pub(crate) struct UploadSession {
    pub(crate) temp_path: PathBuf,
    pub(crate) target_dir: PathBuf,
    pub(crate) file_name: String,
    pub(crate) last_active_at: std::time::Instant,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ConfigResponse {
    pub(crate) ws_address: String,
    pub(crate) http_address: String,
    pub(crate) rust: bool,
}

#[derive(Serialize)]
pub(crate) struct FileEntry {
    pub(crate) name: String,
    pub(crate) path: String,
    pub(crate) directory: bool,
    pub(crate) size: u64,
    pub(crate) modified: String,
}

#[derive(Serialize)]
pub(crate) struct FileListResponse {
    pub(crate) path: String,
    pub(crate) parent: Option<String>,
    pub(crate) files: Vec<FileEntry>,
}

#[derive(Deserialize)]
pub(crate) struct FileListParams {
    pub(crate) path: Option<String>,
}

#[derive(Deserialize)]
pub(crate) struct DownloadParams {
    pub(crate) path: String,
}

#[derive(Deserialize)]
pub(crate) struct UploadInitParams {
    pub(crate) path: Option<String>,
    pub(crate) name: String,
    pub(crate) id: String,
}

#[derive(Deserialize)]
pub(crate) struct UploadFinishForm {
    pub(crate) id: String,
}

#[derive(Serialize)]
pub(crate) struct UploadInitResponse {
    pub(crate) success: bool,
    pub(crate) upload_id: String,
}
