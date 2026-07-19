use axum::{
    extract::{Query, State, WebSocketUpgrade},
    response::IntoResponse,
};
use chrono::Local;
use futures::{sink::SinkExt, stream::StreamExt};
use std::collections::HashMap;
use uuid::Uuid;

use crate::{
    helpers::{broadcast_message, build_chat_message, build_system_message, message_broadcaster},
    shared::{AppState, ChatClient},
};

pub(crate) async fn ws_handler(
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
    let Some(broadcast_tx) = message_broadcaster() else {
        return;
    };
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
    broadcast_message(&state, join_msg).await;

    let send_task = tokio::spawn(async move {
        while let Ok(msg) = broadcast_rx.recv().await {
            if sender
                .send(axum::extract::ws::Message::Text(msg))
                .await
                .is_err()
            {
                break;
            }
        }
        let _ = sender.close().await;
    });

    let recv_task = {
        let client_id = client_id.clone();
        let username = username.clone();
        let state = state.clone();
        tokio::spawn(async move {
            while let Some(Ok(msg)) = receiver.next().await {
                match msg {
                    axum::extract::ws::Message::Text(text) => {
                        let mut chat = state.chat.lock().await;
                        chat.total_messages_received += 1;
                        if let Some(client) = chat.clients.get_mut(&client_id) {
                            client.message_count += 1;
                        }
                        drop(chat);

                        let payload = build_chat_message(&username, &text, "text", None);
                        broadcast_message(&state, payload).await;
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
    broadcast_message(&state, leave_msg).await;
}
