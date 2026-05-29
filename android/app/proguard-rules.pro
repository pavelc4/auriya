# Auriya UI app — ProGuard / R8 rules.

# Compose / Kotlin reflection support that R8 cannot statically prove
# reachable. Without these rules the runtime crashes with
# ClassNotFoundException on first composition.
-keep class androidx.compose.runtime.** { *; }
-dontwarn org.jetbrains.annotations.**

# Keep Auriya's own data classes so reflection-based serialization
# (if and when it lands) still works.
-keep class dev.auriya.app.** { *; }
-keep class dev.auriya.shared.** { *; }

# Drop debug logs from the shipped APK.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# Coroutines internals R8 occasionally over-strips.
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile java.lang.Object result;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
