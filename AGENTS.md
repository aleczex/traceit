# TraceIt AI Agent Instructions

## 🌍 Localization (Critical)
- **Default Language**: English (`res/values/strings.xml`).
- **Required 6 Languages**: Always provide translations for all new/modified string resources across:
  1. English (`res/values/strings.xml`)
  2. German (`res/values-de/strings.xml`)
  3. Spanish (`res/values-es/strings.xml`)
  4. French (`res/values-fr/strings.xml`)
  5. Polish (`res/values-pl/strings.xml`)
  6. Portuguese (`res/values-pt/strings.xml`)

## 🚀 Tech Stack
- Kotlin, Jetpack Compose, Material 3, Coroutines/Flows.
- osmdroid (OpenStreetMap), Foreground Services, Fused Location Provider.
- Google Mobile Ads SDK (standard Google implementation for embedding ads).
- Google Play Services OSS Licenses (standard Google implementation for showing OS licenses).

## 📝 Rules & Guidelines
1. **Idiomatic Code**: Use modern Kotlin and Jetpack Compose best practices.
2. **Background Tracking**: Preserve foreground service notifications and wake locks.
3. **Commit Messages**: Always generate and provide a concise Git commit message after completing any code change.
