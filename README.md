# KPM - Kotlin Package Manager

A modern package manager for Kotlin projects with zero third-party dependencies. Built for Android and JVM development with npm-like simplicity.

<script type='text/javascript' src='https://storage.ko-fi.com/cdn/widget/Widget_2.js'></script><script type='text/javascript'>kofiwidget2.init('Support me on Ko-fi', '#7db8a4', 'L4L81UCM9F');kofiwidget2.draw();</script>

## Features

- **Zero Dependencies**: No external libraries required
- **Android First**: Full Android SDK integration and Compose support
- **NPM-Like Experience**: `kpm add picasso` automatically resolves latest versions
- **Smart Search**: Live Maven Central API integration
- **Custom CLI Framework**: Built-in command-line interface
- **Custom TOML Parser**: Parse project manifests without external deps
- **Gradle Integration**: Automatic build.gradle.kts generation and sync
- **Project Templates**: Initialize projects with sensible defaults
- **Maven Central Integration**: Search and add dependencies seamlessly
- **Global Configuration**: npm/bun/pip-like global config with dependencies and registries
- **IDE Auto-Sync**: Automatically triggers IDE Gradle sync after dependency changes

## Installation

### Homebrew (macOS/Linux) - Recommended

```bash
# Add the KPM tap
brew tap BenMorrisRains/kpm

# Install KPM
brew install kpm

# Or install directly in one command
brew install BenMorrisRains/kpm/kpm
```

### Manual Installation

#### From GitHub Releases
```bash
# Download latest release
curl -L https://github.com/BenMorrisRains/Kotlin-Package-Manager/releases/latest/download/kpm-1.0.0.tar.gz | tar xz
export PATH="$PWD/kpm/bin:$PATH"

# Add to your shell profile for permanent installation
echo 'export PATH="$PWD/kpm/bin:$PATH"' >> ~/.zshrc
```

#### From Source
```bash
git clone https://github.com/BenMorrisRains/Kotlin-Package-Manager.git
cd kpm
./gradlew build
./gradlew installDist
export PATH="$PATH:$(pwd)/build/install/kpm/bin"

# Or use the local build directly
./build/install/kpm/bin/kpm --version
```

## Quick Start

### Create Projects with Smart Defaults

```bash
# Android app with Compose UI
kpm new MyApp --android --compose

# Ktor API server
kpm new MyAPI --ktor

# Simple JVM application
kpm new MyApp

# Android library
kpm new MyLib --android --library
```

### NPM-Like Dependency Management

```bash
# Add dependencies by name (automatically finds latest version)
kpm add picasso          # → com.squareup.picasso:picasso:2.8
kpm add retrofit         # → com.squareup.retrofit2:retrofit:2.9.0
kpm add lottie           # → com.airbnb.android:lottie:6.6.6

# Add test dependencies
kpm add junit --test
kpm add mockito --test

# Traditional coordinates still work
kpm add androidx.compose:compose-bom:2024.10.00

# Search for libraries
kpm search image         # Find image loading libraries
kpm search networking    # Find networking libraries
```

### Build and Run

```bash
cd MyApp
kpm build               # Builds automatically, no manual Gradle setup needed
kpm run                 # Run your application
kpm test                # Run tests
```

### Global Configuration (npm/bun/pip-like)

```bash
# Initialize global configuration
kpm config init

# Set user preferences (applied to all new projects)
kpm config set default_author "Jane Developer"
kpm config set max_heap_size 16g

# Add global dependencies (like npm -g)
kpm config add-global timber          # Latest version (npm-style)
kpm config add-global retrofit 2.8.0  # Specific version

# Add custom/corporate registries
kpm config registry corporate https://nexus.company.com/maven-public/

# View all configuration
kpm config list

# Every new project automatically gets your global dependencies!
kpm new MyApp --android  # ← Includes timber & retrofit automatically
```

## Project Structure

### JVM Application
```
my-app/
├── kpm.toml            # Project manifest
├── kpm.lock            # Dependency lockfile (auto-generated)
├── build.gradle.kts    # Auto-generated Gradle build
├── settings.gradle.kts # Auto-generated Gradle settings
├── gradlew             # Gradle wrapper (bundled)
├── gradle.properties   # Gradle configuration
└── src/
    ├── main/kotlin/
    │   └── Main.kt     # Application entry point
    └── test/kotlin/
```

### Android Application
```
my-android-app/
├── kpm.toml            # Project manifest
├── kpm.lock            # Dependency lockfile
├── build.gradle.kts    # Auto-generated with Android config
├── settings.gradle.kts # Auto-generated
├── gradle.properties   # AndroidX enabled, SDK settings
├── local.properties    # Android SDK path (auto-configured)
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── kotlin/com/example/app/
    │   │   └── MainActivity.kt
    │   └── res/values/
    │       └── strings.xml
    └── test/kotlin/
```

### Global Configuration File
```
~/.kpm/config.toml      # Global user configuration
```

Example global configuration:
```toml
[user]
default_author = "Jane Developer"
default_license = "MIT"
default_kotlin_version = "1.9.25"

[global_dependencies]
# Dependencies included in every new project (added via npm-style commands)
timber = "com.jakewharton.timber:timber:5.0.1"
retrofit = "com.squareup.retrofit2:retrofit:2.8.0"

[build]
max_heap_size = "16g"
parallel_builds = true
build_cache = true

[custom_registries]
corporate = "https://nexus.company.com/maven-public/"
jitpack = "https://jitpack.io"
```

### Project Configuration (kpm.toml)

Example Android application configuration:
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

## Commands

### Project Creation
- `kpm new <name> [--android] [--compose] [--ktor] [--library]` - Create project with smart defaults
- `kpm init --name <name> --type <type>` - Initialize project (advanced)

### Dependency Management
- `kpm add <name>` - Add dependency by name (npm-like)
- `kpm add <group:artifact:version>` - Add dependency with full coordinates
- `kpm add <dependency> --test` - Add as test dependency
- `kpm remove <dependency>` - Remove a dependency
- `kpm search <query>` - Search Maven Central for dependencies

### Build & Run
- `kpm build` - Build the project
- `kpm test` - Run tests
- `kpm run` - Run the application
- `kpm install` - Install dependencies (optional, auto-syncs on add/remove)

### Global Configuration
- `kpm config init` - Initialize global configuration
- `kpm config list` - Show all global configuration
- `kpm config set <key> <value>` - Set configuration value
- `kpm config get <key>` - Get configuration value
- `kpm config add-global <name> [version]` - Add global dependency (like npm -g)
- `kpm config remove-global <name>` - Remove global dependency
- `kpm config registry <name> <url>` - Add custom registry

### Utilities
- `kpm info <dependency>` - Show dependency information
- `kpm doctor` - Check project health and configuration
- `kpm android detect` - Detect Android SDK
- `kpm android setup` - Setup Android SDK for current project

## Project Types

KPM supports multiple project types with the `kpm init` command:

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

## Android Support

KPM provides first-class Android development support:

### Automatic Android SDK Detection
```bash
# KPM automatically finds and configures your Android SDK
kpm new MyApp --android
# Found Android SDK at: /Users/you/Library/Android/sdk
# Android SDK configured for project
```

### Android SDK Management
```bash
kpm android detect      # Check current SDK setup
kpm android setup       # Configure SDK for current project
kpm android install     # Install additional SDK components
```

### Compose Support
```bash
# Create Android app with Compose pre-configured
kpm new MyApp --android --compose
# Automatically adds:
# - Compose BOM with platform() wrapper
# - Compose UI, Material3, Activity Compose
# - Proper build configuration
```

## Popular Libraries (NPM-Style)

KPM knows about 40+ popular libraries for instant resolution:

| Command | Resolves To | Description |
|---------|-------------|-------------|
| `kpm add picasso` | com.squareup.picasso:picasso:2.8 | Image loading |
| `kpm add glide` | com.github.bumptech.glide:glide:4.16.0 | Image loading |
| `kpm add retrofit` | com.squareup.retrofit2:retrofit:2.9.0 | HTTP client |
| `kpm add okhttp` | com.squareup.okhttp3:okhttp:4.12.0 | HTTP client |
| `kpm add gson` | com.google.code.gson:gson:2.10.1 | JSON parsing |
| `kpm add hilt` | com.google.dagger:hilt-android:2.48 | Dependency injection |
| `kpm add room` | androidx.room:room-runtime:2.6.1 | Database |
| `kpm add coroutines` | org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 | Async programming |
| `kpm add timber` | com.jakewharton.timber:timber:5.0.1 | Logging |
| `kpm add junit` | junit:junit:4.13.2 | Testing |
| `kpm add mockito` | org.mockito:mockito-core:5.7.0 | Mocking |
| `kpm add compose-bom` | androidx.compose:compose-bom:2024.10.00 | Compose BOM |

*Don't see your library? KPM will search Maven Central automatically!*

## Complete Workflow Example

### Android App with Modern Stack

```bash
# 0. One-time setup (optional - like npm/bun/pip)
kpm config init
kpm config add-global timber          # Latest version (npm-style)
kpm config add-global retrofit 2.8.0  # Specific version

# 1. Create Android app with Compose (gets global deps automatically!)
kpm new PhotoApp --android --compose
cd PhotoApp

# 2. Add additional libraries (npm-style)
kpm add picasso          # Image loading
kpm add hilt            # Dependency injection
kpm add room            # Database
kpm add coroutines      # Async programming
# Note: timber & retrofit already included from global config!

# 3. Add testing libraries
kpm add junit --test
kpm add mockito --test
kpm add espresso --test

# 4. Build and run (everything auto-configured!)
kpm build               # BUILD SUCCESSFUL
kpm run                 # App launches
```

### Ktor API Server

```bash
# 1. Create API server
kpm new MyAPI --ktor
cd MyAPI

# 2. Add additional dependencies
kpm add gson            # JSON serialization
kpm add coroutines      # Async support
kpm add junit --test    # Testing

# 3. Build and run
kpm build
kpm run                 # Server starts on http://localhost:8080
```

## Comparison with Other Tools

| Feature | KPM | Gradle | Maven |
|---------|-----|--------|-------|
| **Setup Time** | Instant | Manual setup required | Manual setup required |
| **Android Support** | Built-in SDK detection | Manual configuration | Limited support |
| **Dependency Resolution** | `kpm add picasso` | Manual coordinates | Manual coordinates |
| **Zero Config** | Yes | No | No |
| **Live Search** | Yes - Maven Central API | No | No |
| **Auto Gradle Sync** | Yes | Manual | N/A |
| **Global Configuration** | Yes - npm/bun/pip-like | No | No |
| **Global Dependencies** | Yes - `config add-global` | No | No |
| **IDE Auto-Sync** | Yes - IntelliJ/VS Code/Android Studio | No | No |

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Recently Completed

- [x] **Global Configuration**: npm/bun/pip-like global config system
- [x] **Global Dependencies**: `kpm config add-global` for dependencies in all projects
- [x] **Custom Registries**: Support for corporate/private Maven repositories
- [x] **Memory Optimization**: Automatic gradle.properties with build optimizations
- [x] **Android Compose Support**: Full scaffolding with proper build configuration
- [x] **IDE Auto-Sync**: Automatic IDE Gradle sync after dependency changes

## Roadmap

- [ ] **Version Management**: `kpm update` with semantic versioning
- [ ] **Dependency Graph**: Visual dependency analysis
- [ ] **Plugin System**: Custom build plugins
- [ ] **Multi-Module Support**: Gradle multi-project builds
- [ ] **IDE Integration**: IntelliJ IDEA and Android Studio plugins
- [ ] **Publishing**: `kpm publish` to Maven Central
- [ ] **Lockfile Validation**: Security and integrity checks

## License

MIT License
# Test GitHub Actions
