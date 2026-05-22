pub mod ai;

use std::sync::{Arc, Mutex};
use std::time::SystemTime;

#[uniffi::export(callback_interface)]
pub trait ChatCallback: Send + Sync {
    fn on_token(&self, token: String);
}

#[derive(uniffi::Record, Clone)]
pub struct ChatMessage {
    pub sender: String, // "user" or "bot"
    pub text: String,
    pub timestamp: i64,
    pub latency_ms: Option<i64>,
    pub tokens_out: Option<i32>,
    pub tokens_per_second: Option<f64>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("Error: {msg}")]
    Error { msg: String },
}

#[derive(uniffi::Object)]
pub struct Core {
    history: Mutex<Vec<ChatMessage>>,
}

#[uniffi::export]
impl Core {
    #[uniffi::constructor]
    pub fn new() -> Self {
        let initial_msg = ChatMessage {
            sender: "bot".to_string(),
            text: "Hello! I am your AI assistant. How can I help you today?".to_string(),
            timestamp: Self::current_timestamp(),
            latency_ms: None,
            tokens_out: None,
            tokens_per_second: None,
        };
        Self {
            history: Mutex::new(vec![initial_msg]),
        }
    }

    pub fn get_history(&self) -> Vec<ChatMessage> {
        self.history.lock().unwrap().clone()
    }

    pub fn send_message(&self, text: String) -> Result<String, CoreError> {
        let user_msg = ChatMessage {
            sender: "user".to_string(),
            text: text.clone(),
            timestamp: Self::current_timestamp(),
            latency_ms: None,
            tokens_out: None,
            tokens_per_second: None,
        };
        
        {
            let mut history = self.history.lock().unwrap();
            history.push(user_msg);
        }

        // Always run the real model and extract text and metrics
        // (Since test_xy is commented out, we can call test_xy_streaming with a dummy callback)
        let (response_text, latency_ms, tokens_out, tokens_per_second) = match ai::test_xy_streaming(&text, |_| Ok(())) {
            Ok(res) => {
                let response = res.unwrap_text().to_string();
                let lat = Some(res.latency_ms() as i64);
                let metrics = res.metrics();
                let tokens = metrics.tokens_out.map(|t| t as i32);
                let tps = metrics.tokens_per_second.map(|t| t as f64);
                (response, lat, tokens, tps)
            }
            Err(e) => (format!("Error calling AI model: {}", e), None, None, None),
        };

        let bot_msg = ChatMessage {
            sender: "bot".to_string(),
            text: response_text.clone(),
            timestamp: Self::current_timestamp(),
            latency_ms,
            tokens_out,
            tokens_per_second,
        };

        {
            let mut history = self.history.lock().unwrap();
            history.push(bot_msg);
        }

        Ok(response_text)
    }

    pub fn send_message_streaming(&self, text: String, callback: Box<dyn ChatCallback>) -> Result<String, CoreError> {
        let user_msg = ChatMessage {
            sender: "user".to_string(),
            text: text.clone(),
            timestamp: Self::current_timestamp(),
            latency_ms: None,
            tokens_out: None,
            tokens_per_second: None,
        };
        
        let bot_msg = ChatMessage {
            sender: "bot".to_string(),
            text: String::new(),
            timestamp: Self::current_timestamp(),
            latency_ms: None,
            tokens_out: None,
            tokens_per_second: None,
        };

        {
            let mut history = self.history.lock().unwrap();
            history.push(user_msg);
            history.push(bot_msg);
        }

        let history_ref = &self.history;
        let callback = Arc::new(callback);
        let callback_clone = callback.clone();

        let res = ai::test_xy_streaming(&text, move |token| {
            {
                let mut history = history_ref.lock().unwrap();
                if let Some(last_msg) = history.last_mut() {
                    last_msg.text.push_str(&token);
                }
            }
            callback_clone.on_token(token);
            Ok(())
        });

        let (response_text, latency_ms, tokens_out, tokens_per_second) = match res {
            Ok(res) => {
                let response = res.unwrap_text().to_string();
                let lat = Some(res.latency_ms() as i64);
                let metrics = res.metrics();
                let tokens = metrics.tokens_out.map(|t| t as i32);
                let tps = metrics.tokens_per_second.map(|t| t as f64);
                (response, lat, tokens, tps)
            }
            Err(e) => (format!("Error calling AI model: {}", e), None, None, None),
        };

        {
            let mut history = self.history.lock().unwrap();
            if let Some(last_msg) = history.last_mut() {
                last_msg.text = response_text.clone();
                last_msg.latency_ms = latency_ms;
                last_msg.tokens_out = tokens_out;
                last_msg.tokens_per_second = tokens_per_second;
            }
        }

        Ok(response_text)
    }

    pub fn clear_history(&self) {
        let mut history = self.history.lock().unwrap();
        history.clear();
        history.push(ChatMessage {
            sender: "bot".to_string(),
            text: "History cleared. How can I help you now?".to_string(),
            timestamp: Self::current_timestamp(),
            latency_ms: None,
            tokens_out: None,
            tokens_per_second: None,
        });
    }
}

impl Core {
    fn current_timestamp() -> i64 {
        SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0)
    }
}

#[uniffi::export]
pub fn init_sdk_cache_dir(cache_dir: String) {
    xybrid_sdk::init_sdk_cache_dir(cache_dir);
}

#[uniffi::export]
pub fn init_model() -> Result<(), CoreError> {
    ai::warmup_model().map_err(|e| CoreError::Error { msg: e.to_string() })
}

uniffi::setup_scaffolding!();


