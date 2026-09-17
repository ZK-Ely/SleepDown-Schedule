# The common transition framework loads this adapter only after the ColorOS runtime class exists.
# Keep the boundary intact so R8 cannot inline the compileOnly vendor superclass into common code.
-keep class com.xiaomanjun.sleepdownschedule.transition.OplusVendorCallbackFactory { *; }
-keep class com.xiaomanjun.sleepdownschedule.transition.OplusVendorAnimationCallback { *; }

# ONNX Runtime: libonnxruntime4j_jni.so registers native methods and looks up classes by
# their original names inside JNI_OnLoad / GetMethodID. Renaming or stripping these classes
# makes FindClass return null and aborts the process (SIGABRT, java_class == null).
-keep class ai.onnxruntime.** { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.importing.special.OcrEngineManager { *; }

# ML Kit is consumed via a repackaged AAR (app/libs/text-recognition-noassets.aar) whose
# consumer ProGuard rules do not propagate through files(); keep the SDK surface intact
# so its reflection-based component init and native JNI registration keep working.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text.** { *; }

# Retaining the callback is part of the per-session protocol, not an otherwise observable read.
# Without this member rule R8 correctly sees the field as dead and removes the strong reference.
-keepclassmembers class com.xiaomanjun.sleepdownschedule.transition.NativeSessionResource {
    java.lang.Object callback;
}
