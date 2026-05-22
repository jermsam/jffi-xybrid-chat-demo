use std::io::{self, Write};
use testapp_core::Core;

fn main() -> anyhow::Result<()> {
    println!("===============================================");
    println!("           JFFI AI Chat CLI Client             ");
    println!("===============================================");
    println!("Commands:");
    println!("  /exit      - Quit the application");
    println!("  /clear     - Clear chat history");
    println!("-----------------------------------------------");
    println!("Mode: On-Device LLM (Gemma-3-1b)");
    println!("===============================================\n");

    let core = Core::new();

    // Spawn a thread to warm up the model in the background
    std::thread::spawn(|| {
        let _ = testapp_core::init_model();
    });

    // Print initial bot message
    for msg in core.get_history() {
        if let (Some(lat), Some(tok), Some(tps)) = (msg.latency_ms, msg.tokens_out, msg.tokens_per_second) {
            println!("[AI Assistant] ({}ms, {} tokens, {:.1} tok/s): {}", lat, tok, tps, msg.text);
        } else {
            println!("[AI Assistant]: {}", msg.text);
        }
    }

    let stdin = io::stdin();
    
    loop {
        print!("\nYou: ");
        io::stdout().flush()?;
        
        let mut input = String::new();
        stdin.read_line(&mut input)?;
        let trimmed = input.trim();
        
        if trimmed.is_empty() {
            continue;
        }

        if trimmed == "/exit" {
            println!("Goodbye!");
            break;
        }

        if trimmed == "/clear" {
            core.clear_history();
            println!("\n--- Chat history cleared ---");
            if let Some(msg) = core.get_history().last() {
                println!("[AI Assistant]: {}", msg.text);
            }
            continue;
        }

        println!("(Thinking...)");
        match core.send_message(trimmed.to_string()) {
            Ok(response) => {
                if let Some(msg) = core.get_history().last() {
                    if let (Some(lat), Some(tok), Some(tps)) = (msg.latency_ms, msg.tokens_out, msg.tokens_per_second) {
                        println!("\n[AI Assistant] ({}ms, {} tokens, {:.1} tok/s): {}", lat, tok, tps, response);
                    } else {
                        println!("\n[AI Assistant]: {}", response);
                    }
                } else {
                    println!("\n[AI Assistant]: {}", response);
                }
            }
            Err(e) => {
                println!("\n[Error]: {}", e);
            }
        }
    }

    Ok(())
}

