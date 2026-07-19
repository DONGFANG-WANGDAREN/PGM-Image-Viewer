mod auth;
mod handlers;
mod helpers;
mod server;
mod shared;
mod ws;

use jni::{
    objects::JString,
    sys::{jboolean, jint, JNI_FALSE, JNI_TRUE, JNI_VERSION_1_6},
    JNIEnv, JavaVM,
};
use log::error;
use serde_json::{json, Value};
use std::{os::raw::c_void, sync::atomic::Ordering, sync::Arc, sync::Mutex as StdMutex};
use tokio::{
    runtime::Runtime,
    sync::{Mutex as TokioMutex, Notify},
};

use crate::{
    helpers::{generate_random_token, init_logging},
    server::run_server,
    shared::{
        AppConfig, HTTP_ADDRESS, MESSAGE_BROADCASTER, RUNTIME, SERVER_RUNNING, SHUTDOWN_NOTIFY,
    },
};

#[no_mangle]
pub extern "C" fn JNI_OnLoad(_vm: JavaVM, _reserved: *mut c_void) -> jint {
    let _ = _vm;
    let _ = _reserved;
    init_logging();
    JNI_VERSION_1_6
}

#[no_mangle]
pub extern "system" fn Java_com_DONGFANG_1WANGDAREN_Station_1RX_rust_RustServerBridge_nativeStartServer<
    'local,
>(
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
        http_auth_token: generate_random_token(32),
    });

    if let Some(holder) = HTTP_ADDRESS.get() {
        *holder.blocking_lock() = String::new();
    } else {
        let _ = HTTP_ADDRESS.set(Arc::new(TokioMutex::new(String::new())));
    }

    let shutdown_notify = Arc::new(Notify::new());
    if let Some(holder) = SHUTDOWN_NOTIFY.get() {
        *holder.lock().unwrap() = Some(shutdown_notify.clone());
    } else {
        let _ = SHUTDOWN_NOTIFY.set(Arc::new(StdMutex::new(Some(shutdown_notify.clone()))));
    }
    if MESSAGE_BROADCASTER.get().is_none() {
        let _ = MESSAGE_BROADCASTER.set(Arc::new(StdMutex::new(None)));
    }

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
