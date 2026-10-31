//! Standard API response envelope for Auriya IPC and CLI output.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    pub id: String,
    pub success: bool,
    pub code: u16,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
    pub timestamp_ms: u128,
    pub timestamp_iso: String,
}

impl<T> ApiResponse<T> {
    pub fn ok(data: T) -> Self {
        let now = std::time::SystemTime::now();
        let timestamp_ms = now
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        let timestamp_iso = time::OffsetDateTime::now_utc()
            .format(&time::format_description::well_known::Rfc3339)
            .unwrap_or_else(|_| "1970-01-01T00:00:00Z".to_string());

        Self {
            id: uuid::Uuid::new_v4().to_string(),
            success: true,
            code: 200,
            message: "OK".to_string(),
            data: Some(data),
            timestamp_ms,
            timestamp_iso,
        }
    }

    pub fn err(code: u16, message: impl Into<String>) -> Self {
        let now = std::time::SystemTime::now();
        let timestamp_ms = now
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        let timestamp_iso = time::OffsetDateTime::now_utc()
            .format(&time::format_description::well_known::Rfc3339)
            .unwrap_or_else(|_| "1970-01-01T00:00:00Z".to_string());

        Self {
            id: uuid::Uuid::new_v4().to_string(),
            success: false,
            code,
            message: message.into(),
            data: None,
            timestamp_ms,
            timestamp_iso,
        }
    }
}
