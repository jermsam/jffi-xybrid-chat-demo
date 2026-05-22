use xybrid_sdk::{InferenceResult, ModelLoader, XybridModel};
use xybrid_core::ir::envelope::Envelope;
use xybrid_core::ir::EnvelopeKind;
use std::collections::HashMap;
use std::sync::OnceLock;

static MODEL: OnceLock<XybridModel> = OnceLock::new();

/// System prompt applied to every request.
///
/// Design notes (based on Gemma-3-1b instruct research):
/// - Gemma IT models only have user/model roles, no native system role.
///   The xybrid SDK injects this as the system turn in the chat template,
///   which Gemma's tokeniser does support via its jinja template.
/// - Small models need EXPLICIT, LABELLED sections — they respond better
///   to structured instructions than prose paragraphs.
/// - Abstention must be framed as the *correct* behaviour, not a fallback.
const SYSTEM_PROMPT: &str = "\
# Role
You are a concise, honest AI assistant running fully offline on the user's device.

# Core Rules
1. ACCURACY FIRST — Only state things you are confident are true. When uncertain, say \"I'm not sure\" or \"I don't know\" clearly.
2. NO INVENTED FACTS — Never fabricate URLs, names, statistics, dates, prices, or citations. If you don't know a specific URL or fact, say so explicitly rather than guessing.
3. BREVITY — Answer in 2–4 sentences for simple questions. Only go longer when depth is genuinely needed.
4. NO FILLER — Skip preamble (\"Sure!\", \"Great question!\"), sign-offs, and unsolicited follow-up questions.
5. PLAIN LANGUAGE — Write clearly and directly. Only use Markdown (bold, lists, code) when it meaningfully improves readability.

# On Uncertainty
Saying \"I'm not sure\" or \"You should verify this\" is always better than a confident wrong answer.
Never invent an answer to avoid appearing uncertain.";

fn make_envelope(text: &str) -> Envelope {
    let mut metadata = HashMap::new();
    metadata.insert("system_prompt".to_string(), SYSTEM_PROMPT.to_string());
    // max_tokens: 400 is generous for concise answers; prevents essay-length rambling
    metadata.insert("max_tokens".to_string(), "400".to_string());
    // temperature 0.2: much lower than default 0.7 — forces the model toward
    // high-probability (factual) tokens rather than creative/hallucinated ones.
    // Research consensus: 0.1–0.3 is optimal for factual accuracy on 1B models.
    metadata.insert("temperature".to_string(), "0.2".to_string());
    // top_p 0.85: nucleus sampling — cuts off the long tail of unlikely tokens
    metadata.insert("top_p".to_string(), "0.85".to_string());
    // top_k 40: limits vocabulary per step, further reducing hallucination risk
    metadata.insert("top_k".to_string(), "40".to_string());
    Envelope {
        kind: EnvelopeKind::Text(text.to_string()),
        metadata,
    }
}

pub fn warmup_model() -> anyhow::Result<()> {
    if MODEL.get().is_none() {
        println!("Loading and warming up model for the first time...");
        let m = ModelLoader::from_registry("gemma-3-1b").load()?;
        m.warmup()?;  // Pre-loads model weights, compiles shaders
        let _ = MODEL.set(m);
    }
    Ok(())
}

/*
pub fn test_xy(strinput: &str) -> anyhow::Result<InferenceResult> {
    warmup_model()?;

    let model = MODEL.get().unwrap();
    let input = make_envelope(strinput);
    let result = model.run(&input, None /* Option<&GenerationConfig> */)?;  // Fast!
    Ok(result)
}
*/

pub fn test_xy_streaming<F>(strinput: &str, mut on_token: F) -> anyhow::Result<InferenceResult>
where
    F: FnMut(String) -> Result<(), Box<dyn std::error::Error + Send + Sync>> + Send,
{
    warmup_model()?;

    let model = MODEL.get().unwrap();
    let input = make_envelope(strinput);
    let result = model.run_streaming(&input, None, move |partial| {
        on_token(partial.token)
    })?;
    Ok(result)
}

// use xybrid_sdk::{ModelLoader, PipelineRef, Envelope};
//
// // Option 1: Warmup a single model
// let model = ModelLoader::from_registry("gemma-3-1b").load()?;
// model.warmup()?;  // Pre-loads model weights, compiles shaders
// let result = model.run(&Envelope::text("Hello"))?;  // Fast!
//
// // Option 2: Warmup a pipeline
// let pipeline = PipelineRef::from_yaml(yaml)?.load()?;
// pipeline.load_models()?;  // Download models
// pipeline.warmup()?;       // Pre-load into memory
// let result = pipeline.run(&Envelope::text("Hello"))?;  // Fast!
//
// // Option 3: Async warmup for background loading
// let model = loader.load()?;
// tokio::spawn(async move {
// model.warmup_async().await
// });

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_xy_runs() {
        println!("Executing test_xy_streaming test...");
        let result = test_xy_streaming("Give me a list of dog-friendly parks within 5 miles", |token| {
            print!("{}", token);
            Ok(())
        });
        println!("Result: {:?}", result);
        assert!(result.is_ok());
    }
}