//! Normalizes the conversation so that the request it becomes is one the provider accepts.
//!
//! The two output passes exist for one failure: an assistant message whose tool calls are not all
//! answered is rejected outright, and so is a result whose call is missing. Either shape
//! leaves the conversation permanently unable to send anything.
//!
//! The two media passes exist for a second one: a model that cannot read a modality rejects the
//! whole request, so the content the model could not read is replaced with a note saying so.

use jasmine_protocol::models::ContentItem;
use jasmine_protocol::models::FunctionCallOutputBody;
use jasmine_protocol::models::FunctionCallOutputContentItem;
use jasmine_protocol::models::FunctionCallOutputPayload;
use jasmine_protocol::models::ResponseItem;
use jasmine_protocol::openai_models::InputModality;
use std::collections::HashSet;

/// The result recorded for a call that never produced one.
const ABORTED: &str = "aborted";

/// What an image becomes when the model cannot read images.
const UNSUPPORTED_IMAGE: &str = "image content omitted because you do not support image input";

/// What audio becomes when the model cannot read audio.
const UNSUPPORTED_AUDIO: &str = "audio content omitted because you do not support audio input";

/// Fills in a result for every call that has none, inserted right after the call that
/// is missing it.
pub fn ensure_call_outputs_present(items: &mut Vec<ResponseItem>) {
    let answered: HashSet<String> = items
        .iter()
        .filter_map(|item| match item {
            ResponseItem::FunctionCallOutput { call_id, .. } => call_id.clone(),
            _ => None,
        })
        .collect();

    // Collected with their position first, then inserted back-to-front so the earlier
    // insertions do not shift the later ones.
    let mut missing: Vec<(usize, ResponseItem)> = Vec::new();
    for (index, item) in items.iter().enumerate() {
        let ResponseItem::FunctionCall { call_id, .. } = item else {
            continue;
        };
        if answered.contains(call_id) {
            continue;
        }
        tracing::info!("tool call has no result recorded; filling one in: {call_id}");
        missing.push((
            index,
            ResponseItem::FunctionCallOutput {
                id: None,
                call_id: Some(call_id.clone()),
                name: None,
                namespace: None,
                output: FunctionCallOutputPayload::from_text(ABORTED.to_string()),
            },
        ));
    }
    for (index, output) in missing.into_iter().rev() {
        items.insert(index + 1, output);
    }
}

/// Drops results whose call is gone, which happens when the call was trimmed away.
pub fn remove_orphan_outputs(items: &mut Vec<ResponseItem>) {
    let called: HashSet<String> = items
        .iter()
        .filter_map(|item| match item {
            ResponseItem::FunctionCall { call_id, .. } => Some(call_id.clone()),
            _ => None,
        })
        .collect();

    items.retain(|item| match item {
        ResponseItem::FunctionCallOutput { call_id, .. } => call_id
            .as_ref()
            .is_some_and(|call_id| called.contains(call_id)),
        _ => true,
    });
}

/// Replaces image content with a note when the model does not read images.
///
/// When `input_modalities` contains [`InputModality::Image`], nothing is replaced.
pub fn strip_images_when_unsupported(
    input_modalities: &[InputModality],
    items: &mut [ResponseItem],
) {
    if input_modalities.contains(&InputModality::Image) {
        return;
    }

    for item in items.iter_mut() {
        match item {
            ResponseItem::Message { content, .. } => {
                for content_item in content.iter_mut() {
                    if matches!(content_item, ContentItem::InputImage { .. }) {
                        *content_item = ContentItem::InputText {
                            text: UNSUPPORTED_IMAGE.to_string(),
                        };
                    }
                }
            }
            ResponseItem::FunctionCallOutput { output, .. } => {
                let FunctionCallOutputBody::ContentItems(content_items) = &mut output.body else {
                    continue;
                };
                for content_item in content_items.iter_mut() {
                    if matches!(
                        content_item,
                        FunctionCallOutputContentItem::InputImage { .. }
                    ) {
                        *content_item = FunctionCallOutputContentItem::InputText {
                            text: UNSUPPORTED_IMAGE.to_string(),
                        };
                    }
                }
            }
            _ => {}
        }
    }
}

/// Replaces audio content with a note when the model does not read audio.
///
/// When `input_modalities` contains [`InputModality::Audio`], nothing is replaced.
pub fn strip_audio_when_unsupported(
    input_modalities: &[InputModality],
    items: &mut [ResponseItem],
) {
    if input_modalities.contains(&InputModality::Audio) {
        return;
    }

    for item in items.iter_mut() {
        match item {
            ResponseItem::Message { content, .. } => {
                for content_item in content.iter_mut() {
                    if matches!(content_item, ContentItem::InputAudio { .. }) {
                        *content_item = ContentItem::InputText {
                            text: UNSUPPORTED_AUDIO.to_string(),
                        };
                    }
                }
            }
            ResponseItem::FunctionCallOutput { output, .. } => {
                let FunctionCallOutputBody::ContentItems(content_items) = &mut output.body else {
                    continue;
                };
                for content_item in content_items.iter_mut() {
                    if matches!(
                        content_item,
                        FunctionCallOutputContentItem::InputAudio { .. }
                    ) {
                        *content_item = FunctionCallOutputContentItem::InputText {
                            text: UNSUPPORTED_AUDIO.to_string(),
                        };
                    }
                }
            }
            _ => {}
        }
    }
}

/// Runs the passes in the order the request needs them.
pub fn normalize(items: &mut Vec<ResponseItem>, input_modalities: &[InputModality]) {
    strip_images_when_unsupported(input_modalities, items);
    strip_audio_when_unsupported(input_modalities, items);
    remove_orphan_outputs(items);
    ensure_call_outputs_present(items);
}

#[cfg(test)]
mod tests {
    use super::strip_images_when_unsupported;
    use jasmine_protocol::models::ContentItem;
    use jasmine_protocol::models::ImageReference;
    use jasmine_protocol::models::ResponseItem;
    use jasmine_protocol::openai_models::InputModality;

    fn message_with_image() -> ResponseItem {
        ResponseItem::Message {
            id: None,
            role: "user".to_string(),
            content: vec![
                ContentItem::InputText {
                    text: "look at this".to_string(),
                },
                ContentItem::InputImage {
                    image: ImageReference::Inline {
                        image_url: "data:image/png;base64,AAAA".to_string(),
                    },
                    detail: None,
                },
            ],
        }
    }

    fn second_content_item(items: &[ResponseItem]) -> &ContentItem {
        let ResponseItem::Message { content, .. } = &items[0] else {
            panic!("expected a message");
        };
        &content[1]
    }

    #[test]
    fn replaces_image_content_when_the_model_cannot_read_images() {
        let mut items = vec![message_with_image()];
        strip_images_when_unsupported(&[InputModality::Text], &mut items);
        assert!(matches!(
            second_content_item(&items),
            ContentItem::InputText { .. }
        ));
    }

    #[test]
    fn keeps_image_content_when_the_model_reads_images() {
        let mut items = vec![message_with_image()];
        strip_images_when_unsupported(&[InputModality::Text, InputModality::Image], &mut items);
        assert!(matches!(
            second_content_item(&items),
            ContentItem::InputImage { .. }
        ));
    }
}
