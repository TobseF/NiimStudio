# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class dev.b18print.model.** { *** Companion; }
-keepclasseswithmembers class dev.b18print.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.b18print.model.**$$serializer { *; }
