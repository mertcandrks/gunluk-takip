# Retain source positions for local crash diagnosis; R8 still optimizes code.
-keepattributes SourceFile,LineNumberTable

# ML Kit discovers these registrars by their manifest class names at runtime.
-keep class com.google.mlkit.**.*Registrar { public <init>(); }
