# Auriya service — ProGuard / R8 rules.
#
# The service is launched via `app_process` with our `Main.kt` as the
# entry point, NOT through an Activity or ContentProvider. R8 therefore
# strips Main and every type reachable from it unless we explicitly
# keep them.

# Entry point + everything in our packages.
-keep class dev.auriya.service.Main { *; }
-keep class dev.auriya.service.** { *; }
-keep class dev.auriya.shared.** { *; }

# Reflection targets (hidden Android API surfaces).
-keepnames class android.app.IActivityTaskManager$Stub { *; }
-keepnames class android.app.ITaskStackListener$Stub { *; }
-keepnames class android.os.ServiceManager { *; }

# Strip debug logs in release.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# Kotlin coroutines: keep enough to function under aggressive optimization.
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile java.lang.Object result;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
