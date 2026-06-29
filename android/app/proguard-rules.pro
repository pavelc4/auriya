# Auriya UI app — ProGuard / R8 rules.
# Full obfuscation: repackage all classes into a single package and
# allow access modification so R8 can inline aggressively.
-allowaccessmodification
-repackageclasses ''
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Compose / Kotlin reflection support that R8 cannot statically prove
# reachable. Without these rules the runtime crashes with
# ClassNotFoundException on first composition.
-keep class androidx.compose.runtime.** { *; }
-dontwarn org.jetbrains.annotations.**

# Keep manifest-referenced classes from being repackaged by -repackageclasses.
# Without these, AuriyaApplication / MainActivity move to root package but the
# manifest's android:name=".AuriyaApplication" still looks in dev.auriya.app.
-keep class dev.auriya.app.AuriyaApplication
-keep class dev.auriya.app.MainActivity
-keep class dev.auriya.app.widget.AuriyaWidgetReceiver
-keep class dev.auriya.app.service.OverlayService

# Keep Glance app widget action callbacks from being stripped or renamed
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }

# kotlinx.serialization (shared module uses it)
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class dev.auriya.**$$serializer { *; }
-keepclassmembers class dev.auriya.** {
    *** Companion;
}
-keepclasseswithmembers class dev.auriya.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt / Dagger (not yet used but keeps the door open)
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

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

# WorkManager and Room proguard rules
-keep class androidx.work.** { *; }
-keep class androidx.room.** { *; }
-keep class * extends androidx.work.impl.WorkDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.work.**
-dontwarn androidx.room.**
