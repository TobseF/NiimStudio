# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.tobsef.niimstudio.model.** { *** Companion; }
-keepclasseswithmembers class io.github.tobsef.niimstudio.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.tobsef.niimstudio.model.**$$serializer { *; }
