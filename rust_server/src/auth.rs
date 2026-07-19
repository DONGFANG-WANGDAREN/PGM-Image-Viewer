use axum::http::HeaderMap;
use chrono::Local;

use crate::{
    helpers::{generate_random_token, get_cookie_value},
    shared::{
        AppState, WebLoginSession, COOKIE_NAME, WEB_AUTH_COOKIE_NAME, WEB_LOGIN_SESSION_MAX_AGE_MS,
    },
};

pub(crate) fn create_web_login_session() -> WebLoginSession {
    WebLoginSession {
        session_id: generate_random_token(16),
        token: generate_random_token(32),
        auth_token: generate_random_token(32),
        created_at_ms: Local::now().timestamp_millis(),
        authenticated: false,
    }
}

pub(crate) async fn cleanup_expired_web_login_sessions(state: &AppState) {
    let now = Local::now().timestamp_millis();
    let mut sessions = state.web_login_sessions.lock().await;
    sessions.retain(|_, session| now - session.created_at_ms <= WEB_LOGIN_SESSION_MAX_AGE_MS);
}

pub(crate) async fn is_web_login_session_authenticated(state: &AppState, auth_token: &str) -> bool {
    cleanup_expired_web_login_sessions(state).await;
    let sessions = state.web_login_sessions.lock().await;
    sessions
        .values()
        .any(|session| session.authenticated && session.auth_token == auth_token)
}

pub(crate) async fn is_authenticated(headers: &HeaderMap, state: &AppState) -> bool {
    if state.config.http_auth_token.is_empty() {
        return false;
    }
    if get_cookie_value(headers, COOKIE_NAME)
        .map(|token| token == state.config.http_auth_token)
        .unwrap_or(false)
    {
        return true;
    }
    if let Some(auth_token) = get_cookie_value(headers, WEB_AUTH_COOKIE_NAME) {
        return is_web_login_session_authenticated(state, &auth_token).await;
    }
    false
}
