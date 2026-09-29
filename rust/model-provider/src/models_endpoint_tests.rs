use super::parse_model_ids;

#[test]
fn reads_the_generic_shape() {
    let ids = parse_model_ids(r#"{"data":[{"id":"gpt-4o"},{"id":"gpt-4o"}]}"#).expect("ids");
    assert_eq!(ids, ["gpt-4o"]);
}

#[test]
fn reads_the_deepseek_shape() {
    let ids = parse_model_ids(r#"{"models":[{"id":"deepseek-flash"},{"model_name":"deepseek-v4-pro"}]}"#)
        .expect("ids");
    assert_eq!(ids, ["deepseek-flash", "deepseek-v4-pro"]);
}

#[test]
fn a_body_without_a_list_is_malformed() {
    assert!(parse_model_ids(r#"{"error":"nope"}"#).is_err());
}
