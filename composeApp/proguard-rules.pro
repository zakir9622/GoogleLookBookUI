# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.zakir.vestra.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ONNX Runtime — JNI constructs NodeInfo/TensorInfo reflectively. R8 stripping
# caused Pixel try-on SIGABRT: NoSuchMethodError NodeInfo.<init>(String,ValueInfo).
# https://onnxruntime.ai/docs/build/android.html
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# LiteRT-LM (Gemma 4 / vision / audio) — JNI + ToolSet reflection
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# MediaPipe GenAI (legacy Gemma 3) — reflection + JNI
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

