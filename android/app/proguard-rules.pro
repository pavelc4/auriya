# Auriya UI app — ProGuard / R8 rules.
# Full obfuscation: repackage all classes into a single package and
# allow access modification so R8 can inline aggressively.
-allowaccessmodification
-repackageclasses ''

-dontwarn org.jetbrains.annotations.**

# Keep manifest-referenced classes from being repackaged by -repackageclasses.
# Without these, AuriyaApplication / MainActivity move to root package but the
# manifest's android:name=".AuriyaApplication" still looks in dev.auriya.app.
-keep class dev.auriya.app.AuriyaApplication
-keep class dev.auriya.app.MainActivity
-keep class dev.auriya.app.receiver.AuriyaActionReceiver
-keep class dev.auriya.app.service.AuriyaTileService
-keep class dev.auriya.app.service.BenchmarkRecordingService
-keep class dev.auriya.app.service.OverlayService

# kotlinx.serialization (shared module uses it)
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class dev.auriya.**$$serializer { *; }
-keepclassmembers class dev.auriya.** {
    *** Companion;
}
-keepclasseswithmembers class dev.auriya.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Drop debug logs from the shipped APK.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
