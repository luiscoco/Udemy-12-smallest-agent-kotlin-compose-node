# Asset provenance

The Android astronaut drawable is copied individually from edition 10's known course asset. Upstream reference: https://github.com/luiscoco/Udemy-Claude-Agent-SDK-lesson01-app at commit 832439954c11d4f3623d1b4a311f7dd67389fcc8. Preserve upstream rights and attribution when redistributing. No real environment file or credential was copied.

The UML SVG and PlantUML templates adapt edition 10's corrected explicit-presentation diagram to Kotlin Compose and Node. scripts/generate-lesson.mjs produces matching 16-step source explanations and native/download assets. Native AndroidSVG rendering uses only local trusted assets; it does not load model-supplied SVG.

Gradle wrapper scripts/JAR were copied individually from the official wrapper generated for edition 11 and point to the checksum-pinned Gradle distribution. Preserve their upstream Apache 2.0 notices. AndroidSVG is Apache 2.0; AndroidX, Kotlin, OkHttp and their dependencies retain their upstream licenses. System fonts are used; no remote fonts are fetched by the application.
