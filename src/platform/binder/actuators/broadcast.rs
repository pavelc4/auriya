//! Direct Intent Broadcast actuator via Android `IActivityManager`.
//!
//! Replaces legacy `am broadcast` subshell execution with direct Native Binder IPC.

use anyhow::Result;

pub const ACTIVITY_SERVICE: &str = "activity";

// Transaction code extracted from framework.jar IActivityManager$Stub
pub const TRANSACTION_BROADCAST_INTENT_WITH_FEATURE: u32 = 26;

/// Write an Android String8 (length as i32, UTF-8 bytes + null terminator, 4-byte aligned).
#[cfg(target_os = "android")]
pub(crate) fn write_string8(
    parcel: &mut rsbinder::Parcel,
    s: Option<&str>,
) -> rsbinder::Result<()> {
    match s {
        Some(val) => {
            let bytes = val.as_bytes();
            let len = bytes.len() as i32;
            parcel.write_i32(len)?;
            let mut buf = bytes.to_vec();
            buf.push(0); // null terminator
            while buf.len() % 4 != 0 {
                buf.push(0);
            }
            for &chunk in buf.as_chunks::<4>().0 {
                let word = u32::from_ne_bytes(chunk);
                parcel.write_u32(word)?;
            }
            Ok(())
        }
        None => parcel.write_i32(-1),
    }
}

/// Serialize an Android `Intent` Parcelable directly into a Binder `Parcel`.
#[cfg(target_os = "android")]
fn write_intent(
    parcel: &mut rsbinder::Parcel,
    action: &str,
    extras: &[(&str, &str)],
) -> rsbinder::Result<()> {
    // 1. action: String8
    write_string8(parcel, Some(action))?;
    // 2. uri: Uri (null = 0)
    parcel.write_i32(0)?;
    // 3. type: String8 (null = -1)
    write_string8(parcel, None)?;
    // 4. identifier: String8 (null = -1)
    write_string8(parcel, None)?;
    // 5. flags: int (0x01000020 = FLAG_RECEIVER_INCLUDE_BACKGROUND | FLAG_EXCLUDE_STOPPED_PACKAGES)
    parcel.write_i32(0x01000020)?;
    // 6. extendedFlags: int (0) - Android 14+
    parcel.write_i32(0)?;
    // 7. package: String8 ("dev.auriya.app")
    write_string8(parcel, Some("dev.auriya.app"))?;
    // 8. component: ComponentName (null = -1)
    write_string8(parcel, None)?;
    // 9. sourceBounds: Rect (null = 0)
    parcel.write_i32(0)?;
    // 10. categories: ArraySet<String> (count = 0)
    parcel.write_i32(0)?;
    // 11. selector: Intent (null = 0)
    parcel.write_i32(0)?;
    // 12. clipData: ClipData (null = 0)
    parcel.write_i32(0)?;
    // 13. contentUserHint: -2 (UserHandle.USER_CURRENT)
    parcel.write_i32(-2)?;

    // 14. extras: Bundle
    if extras.is_empty() {
        parcel.write_i32(-1)?; // null Bundle
    } else {
        let mut bundle_parcel = rsbinder::Parcel::new();
        // BUNDLE_MAGIC = 0x4C444E42 ("BNDL")
        bundle_parcel.write_i32(0x4C444E42)?;
        bundle_parcel.write_i32(extras.len() as i32)?;
        for (k, v) in extras {
            // key: String16
            bundle_parcel.write(&k.to_string())?;
            // VAL_STRING = 0
            bundle_parcel.write_i32(0)?;
            // value: String16
            bundle_parcel.write(&v.to_string())?;
        }

        let bundle_size = bundle_parcel.data_size() as i32;
        parcel.write_i32(bundle_size)?;

        bundle_parcel.set_data_position(0);
        let num_words = (bundle_size as usize).div_ceil(4);
        for _ in 0..num_words {
            let word = bundle_parcel.read_u32().unwrap_or(0);
            parcel.write_u32(word)?;
        }
    }

    // 15. originalIntent: Intent (null = 0)
    parcel.write_i32(0)?;
    // 16. creatorTokenInfo: CreatorTokenInfo (null = 0)
    parcel.write_i32(0)?;
    Ok(())
}

/// Send an explicit or broadcast Intent directly to Android `ActivityManagerService` via Native Binder.
pub fn broadcast_intent(action: &str, extras: &[(&str, &str)]) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(ACTIVITY_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("activity service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("activity service is not a proxy"),
        };

        // TRANSACTION_broadcastIntentWithFeature = 26
        let mut data = proxy.prepare_transact(true)?;
        data.write(&Option::<rsbinder::SIBinder>::None)?; // caller = null
        data.write(&Option::<String>::None)?; // callingFeatureId = null

        // intent: TypedObject<Intent>
        data.write_i32(1)?; // non-null indicator for readTypedObject(Intent.CREATOR)
        write_intent(&mut data, action, extras)?; // intent

        data.write(&Option::<String>::None)?; // resolvedType = null
        data.write(&Option::<rsbinder::SIBinder>::None)?; // resultTo = null
        data.write_i32(0)?; // resultCode = 0
        data.write(&Option::<String>::None)?; // resultData = null
        data.write_i32(0)?; // resultExtras = null TypedObject
        data.write_i32(-1)?; // requiredPermissions = null Array
        data.write_i32(-1)?; // excludedPermissions = null Array
        data.write_i32(-1)?; // excludedPackages = null Array
        data.write_i32(-1)?; // appOp = OP_NONE (-1)
        data.write_i32(0)?; // options = null TypedObject
        data.write_bool(false)?; // serialized = false
        data.write_bool(false)?; // sticky = false
        data.write_i32(-1)?; // userId = USER_ALL (-1)

        let reply_opt =
            proxy.submit_transact(TRANSACTION_BROADCAST_INTENT_WITH_FEATURE, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                let err_msg: String = rep.read().unwrap_or_default();
                anyhow::bail!(
                    "broadcastIntentWithFeature returned exception {}: {}",
                    ex,
                    err_msg
                );
            }
            let res = rep.read_i32().unwrap_or(0);
            tracing::info!(target: "auriya::binder", "Broadcast intent '{}' dispatched via Native Binder (code: {})", action, res);
            return Ok(());
        }
        anyhow::bail!("broadcastIntentWithFeature received no reply");
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Broadcast intent '{}' with {} extras", action, extras.len());
        Ok(())
    }
}
