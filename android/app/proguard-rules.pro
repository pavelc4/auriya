# Auriya UI app — ProGuard / R8 rules.
# Full obfuscation and maximum iterative optimization passes.
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

-dontwarn org.jetbrains.annotations.**

# Keep manifest-referenced components from being renamed/repackaged
-keep class dev.auriya.app.AuriyaApplication
-keep class dev.auriya.app.MainActivity
-keep class dev.auriya.app.receiver.AuriyaActionReceiver
-keep class dev.auriya.app.service.AuriyaTileService
-keep class dev.auriya.app.service.BenchmarkRecordingService
-keep class dev.auriya.app.service.OverlayService

# Strip source file name and line numbers from release dex
-renamesourcefileattribute SourceFile

# Strip debugging logs and print statements
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int println(...);
}

# Strip all redundant Kotlin runtime intrinsics/assertions
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void throwNpe();
    public static void throwNpe(java.lang.String);
}
