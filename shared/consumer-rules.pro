# ONNX Runtime JNI reflects into Java constructors (NodeInfo, TensorInfo, …).
# Without this, R8 strips them and release builds abort on OrtSession.getInputInfo
# with NoSuchMethodError → SIGABRT (Pixel try-on generate crash).
# See https://onnxruntime.ai/docs/build/android.html
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# MediaPipe GenAI (local Gemma) — reflection + JNI from shared androidMain
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# LiteRT-LM (Gemma 4 / vision / audio / tools)
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
