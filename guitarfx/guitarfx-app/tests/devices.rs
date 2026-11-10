//! The devices, without hardware (spec 027, T028, FR-003, FR-012, FR-013): the built-in microphone rule of `InputDeviceCatalog.classify`
//! with its Java cases, a remembered device found by its exact name only, and cpal's errors mapped to the messages of
//! contracts/application.md.

use cpal::{BackendSpecificError, BuildStreamError, DefaultStreamConfigError, PlayStreamError, SampleFormat, StreamError};
use guitarfx_app::devices::{find_exact, is_built_in_microphone, supported_format, Problem};

#[test]
fn the_built_in_microphone_is_recognised_as_in_java() {
    // InputDeviceCatalogTest.positivelyClassifiesOnlyRecognizableBuiltInMicrophoneNames
    assert!(is_built_in_microphone("Micro MacBook Pro"));
    assert!(is_built_in_microphone("Built-in Microphone"));
    assert!(!is_built_in_microphone("iPhone Microphone"));
    assert!(!is_built_in_microphone("USB Audio CODEC"));
    assert!(!is_built_in_microphone("Microsoft Teams Audio"));
    // the other words of the rule
    assert!(is_built_in_microphone("MacBook Pro Microphone"));
    assert!(is_built_in_microphone("Internal Microphone"));
    assert!(!is_built_in_microphone("Rocksmith Guitar Adapter"));
}

#[test]
fn a_bluetooth_input_is_labelled_like_any_other() {
    // InputDeviceCatalogTest.aBluetoothInputIsLabelledLikeAnyOtherAndNeverRefused
    for name in [
        "AirPods Pro",
        "Bluetooth Headset",
        "Galaxy Buds2 (Bluetooth)",
        "JBL Tune 510BT",
        "Hands-Free AG Audio",
    ] {
        assert!(!is_built_in_microphone(name), "{name}");
    }
}

#[test]
fn a_remembered_device_is_found_by_its_exact_name_only() {
    let names = ["MacBook Pro Speakers".to_string(), "External Headphones".to_string()];
    assert_eq!(find_exact(&names, "External Headphones"), Some(1));
    assert_eq!(find_exact(&names, "MacBook Pro"), None, "never another device in its place");
    assert_eq!(find_exact(&names, "macbook pro speakers"), None);
    assert_eq!(find_exact(&names, ""), None);
}

fn backend(text: &str) -> BackendSpecificError {
    BackendSpecificError {
        description: text.to_string(),
    }
}

#[test]
fn cpals_errors_are_mapped_to_the_messages() {
    assert_eq!(
        Problem::from_build(&BuildStreamError::DeviceNotAvailable, true),
        Problem::Lost { input: true }
    );
    assert_eq!(
        Problem::from_build(&BuildStreamError::DeviceNotAvailable, false),
        Problem::Lost { input: false }
    );
    assert_eq!(
        Problem::from_build(&BuildStreamError::StreamConfigNotSupported, true),
        Problem::Unsupported
    );
    assert_eq!(Problem::from_build(&BuildStreamError::InvalidArgument, false), Problem::Unsupported);
    assert_eq!(
        Problem::from_stream(&StreamError::DeviceNotAvailable, false),
        Problem::Lost { input: false }
    );
    assert_eq!(
        Problem::from_play(&PlayStreamError::DeviceNotAvailable, true),
        Problem::Lost { input: true }
    );
    assert_eq!(
        Problem::from_config(&DefaultStreamConfigError::StreamTypeNotSupported, true),
        Problem::Unsupported
    );
    assert_eq!(
        Problem::from_config(&DefaultStreamConfigError::DeviceNotAvailable, true),
        Problem::Lost { input: true }
    );
    for text in [
        "Permission denied",
        "the microphone access is not authorized",
        "Access is denied. (0x80070005)",
    ] {
        let err = backend(text);
        assert_eq!(
            Problem::from_build(&BuildStreamError::BackendSpecific { err: err.clone() }, true),
            Problem::PermissionDenied,
            "{text}"
        );
        assert_eq!(
            Problem::from_stream(&StreamError::BackendSpecific { err }, true),
            Problem::PermissionDenied,
            "{text}"
        );
    }
    assert_eq!(
        Problem::from_build(
            &BuildStreamError::BackendSpecific {
                err: backend("some driver error")
            },
            true
        ),
        Problem::Failed("some driver error".to_string())
    );
}

#[test]
fn every_problem_has_a_message_key() {
    let problems = [
        Problem::Missing { input: true },
        Problem::Missing { input: false },
        Problem::Lost { input: true },
        Problem::Lost { input: false },
        Problem::PermissionDenied,
        Problem::Unsupported,
        Problem::Failed("x".to_string()),
    ];
    for problem in problems {
        assert!(problem.key().starts_with("message."), "{problem:?}");
    }
}

#[test]
fn float_16_and_32_bit_samples_are_supported() {
    assert!(supported_format(SampleFormat::F32));
    assert!(supported_format(SampleFormat::I16));
    assert!(supported_format(SampleFormat::I32));
    assert!(!supported_format(SampleFormat::U8));
    assert!(!supported_format(SampleFormat::F64));
}
