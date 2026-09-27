use jasmine_protocol::models::DEFAULT_IMAGE_DETAIL;
use jasmine_protocol::models::FunctionCallOutputContentItem;
use jasmine_protocol::models::ImageDetail;

/// Picks the image detail a tool output may request from a model.
///
/// `supports_image_detail_original` is the capability the selected model
/// reports; jasmine reads it from the model provider rather than from the
/// upstream `ModelInfo` payload.
pub fn normalize_output_image_detail(
    supports_image_detail_original: bool,
    detail: Option<ImageDetail>,
) -> Option<ImageDetail> {
    match detail {
        Some(ImageDetail::Original) if supports_image_detail_original => {
            Some(ImageDetail::Original)
        }
        Some(ImageDetail::Original) | None => None,
        Some(ImageDetail::Auto | ImageDetail::Low | ImageDetail::High) => detail,
    }
}

pub fn sanitize_original_image_detail(
    can_request_original_image_detail: bool,
    items: &mut [FunctionCallOutputContentItem],
) {
    if can_request_original_image_detail {
        return;
    }

    for item in items {
        if let FunctionCallOutputContentItem::InputImage { detail, .. } = item
            && matches!(detail, Some(ImageDetail::Original))
        {
            *detail = Some(DEFAULT_IMAGE_DETAIL);
        }
    }
}
