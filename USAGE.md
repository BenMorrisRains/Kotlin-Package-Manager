# KPM Usage Examples

## Quick Start

### 1. Build KPM
```bash
./gradlew build
./gradlew installDist
```

### 2. Create a new Android app
```bash
./build/install/kpm/bin/kpm init --name MyApp --type android-app --package com.example.myapp
cd MyApp
```

### 3. Add dependencies
```bash
# Add implementation dependency
./build/install/kpm/bin/kpm add androidx.compose:compose-bom:2024.10.00

# Add test dependency
./build/install/kpm/bin/kpm add junit --test

# Add with full coordinates
./build/install/kpm/bin/kpm add "com.squareup.retrofit2:retrofit:2.9.0"
```

### 4. Install dependencies
```bash
./build/install/kpm/bin/kpm install
```

### 5. Build the project
```bash
./build/install/kpm/bin/kpm build
```

## Project Types

### Android Application
```bash
kpm init --name MyAndroidApp --type android-app --package com.example.app
```

### Android Library
```bash
kpm init --name MyLibrary --type android-library --package com.example.lib
```

### JVM Application
```bash
kpm init --name MyJvmApp --type jvm-application
```

### Ktor API
```bash
kpm init --name MyApi --type ktor-api
```

## Example kpm.toml

```toml
[project]
name = "my-android-app"
version = "0.1.0"
type = "android-app"
kotlin_version = "2.0.0"

[android]
application_id = "com.example.myapp"
min_sdk = 24
target_sdk = 35
compile_sdk = 35
namespace = "com.example.myapp"

[repositories]
maven_central = true
google = true
gradle_plugin_portal = false

[dependencies]
coreKtx = "androidx.core:core-ktx:1.13.+"
appcompat = "androidx.appcompat:appcompat:1.6.+"
material = "com.google.android.material:material:1.11.+"

[test_dependencies]
junit = "junit:junit:4.13.2"
```

## Available Commands

- `kpm init` - Create a new project
- `kpm add <dependency>` - Add a dependency
- `kpm remove <dependency>` - Remove a dependency
- `kpm install` - Install dependencies
- `kpm update` - Update dependencies
- `kpm build` - Build the project
- `kpm test` - Run tests
- `kpm run` - Run the application
- `kpm search <query>` - Search Maven Central
- `kpm info <dependency>` - Show dependency info
- `kpm doctor` - Check project health

## Shorthand Dependencies

KPM supports shorthand names for common dependencies:

- `junit` → `junit:junit:4.13.2`
- `mockito` → `org.mockito:mockito-core:5.7.0`
- `okhttp` → `com.squareup.okhttp3:okhttp:4.12.0`
- `gson` → `com.google.code.gson:gson:2.10.1`
- `retrofit` → `com.squareup.retrofit2:retrofit:2.9.0`
- `coroutines` → `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`
- `compose-bom` → `androidx.compose:compose-bom:2024.10.00`
- `hilt` → `com.google.dagger:hilt-android:2.48`
- `room` → `androidx.room:room-runtime:2.6.1`
