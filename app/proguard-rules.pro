
# ML Kit GenAI structured output: the @Generable answer classes and their KSP-generated schema
# providers are found and filled by reflection (release builds, once minify is on).
-keep @com.google.mlkit.genai.schema.annotations.Generable class * { *; }
-keep class **_GeneratedProvider { *; }
