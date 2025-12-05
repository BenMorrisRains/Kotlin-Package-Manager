# KPM Global Configuration Implementation

## ✅ **Complete Implementation - npm/bun/pip Pattern**

KPM now follows the exact same global configuration patterns as npm, bun, and pip with full support for global dependencies and custom registries.

## 🌍 **Global Configuration Structure**

### **Location: `~/.kpm/config.toml`**

```toml
# KPM Global Configuration
# This file contains user-wide defaults and preferences

[user]
default_author = "benmorris-rains"
default_license = "MIT"
default_kotlin_version = "1.9.25"

[android]
sdk_path = ""

[repositories]
maven_central = true
google = true
gradle_plugin_portal = false

[build]
max_heap_size = "8g"
parallel_builds = true
build_cache = true
configure_on_demand = true

[global_dependencies]
# Dependencies to include in every new project
timber = "com.jakewharton.timber:timber:5.0.1"

[default_test_dependencies]
# Default test dependencies for new projects
junit = "junit:junit:4.13.2"
kotlin-test = "org.jetbrains.kotlin:kotlin-test"

[registry]
maven_central_url = "https://repo1.maven.org/maven2/"
google_url = "https://dl.google.com/dl/android/maven2/"
search_api_url = "https://search.maven.org/solrsearch/select"
timeout = 30

[custom_registries]
jitpack = "https://jitpack.io"
```

## 📦 **Package Manager Comparison**

| Feature | npm | bun | pip | **KPM** |
|---------|-----|-----|-----|---------|
| **Global Config** | `~/.npmrc` | `~/.bunfig.toml` | `~/.pip/pip.conf` | `~/.kpm/config.toml` ✅ |
| **Global Dependencies** | `npm install -g` | `bun add -g` | `pip install --user` | `kpm config add-global` ✅ |
| **Custom Registries** | `npm config set registry` | `bunfig.toml` | `pip.conf` | `kpm config registry` ✅ |
| **Project Config** | `package.json` | `package.json` | `requirements.txt` | `kpm.toml` ✅ |

## 🚀 **Commands Implemented**

### **Basic Configuration**
```bash
# Initialize global config
kpm config init

# View all settings
kpm config list

# Get specific setting
kpm config get max_heap_size

# Set specific setting
kpm config set max_heap_size 16g
```

### **Global Dependencies (Like npm -g)**
```bash
# Add global dependencies (included in every new project)
kpm config add-global timber com.jakewharton.timber:timber:5.0.1
kpm config add-global retrofit com.squareup.retrofit2:retrofit:2.9.0

# Remove global dependencies
kpm config remove-global timber
```

### **Custom Registries (Like npm registry)**
```bash
# Add custom/corporate registries
kpm config registry corporate https://nexus.company.com/repository/maven-public/
kpm config registry jitpack https://jitpack.io

# Set different Maven Central mirror
kpm config set registry.maven_central_url https://repo.maven.apache.org/maven2/
```

## 🧪 **Test Results**

### **1. ✅ Global Configuration Management**
```bash
$ kpm config init
✅ Created global config at: /Users/user/.kpm/config.toml

$ kpm config list
🌍 Global KPM Configuration:
📝 User Settings:
   default_author = "benmorris-rains"
   default_kotlin_version = "1.9.25"
🌍 Global Dependencies:
   timber = "com.jakewharton.timber:timber:5.0.1"
🏢 Custom Registries:
   jitpack = "https://jitpack.io"
```

### **2. ✅ Global Dependencies (npm-like)**
```bash
$ kpm config add-global timber com.jakewharton.timber:timber:5.0.1
✅ Added global dependency: timber = "com.jakewharton.timber:timber:5.0.1"
💡 This dependency will be included in all new projects

$ kpm new MyApp --android
# Generated kpm.toml automatically includes:
[dependencies]
coreKtx = "androidx.core:core-ktx:1.10.1"
appcompat = "androidx.appcompat:appcompat:1.6.1"
material = "com.google.android.material:material:1.11.0"
timber = "com.jakewharton.timber:timber:5.0.1"  # ← Global dependency!
```

### **3. ✅ Custom Registries**
```bash
$ kpm config registry jitpack https://jitpack.io
✅ Added custom registry: jitpack = "https://jitpack.io"

$ kpm config registry corporate https://nexus.company.com/maven-public/
✅ Added custom registry: corporate = "https://nexus.company.com/maven-public/"
```

### **4. ✅ Memory Optimization Integration**
```bash
$ kpm config set max_heap_size 16g
✅ Set max_heap_size = "16g"

$ kpm new MyApp --android
# Generated gradle.properties uses global config:
org.gradle.jvmargs=-Xmx16g -XX:MaxMetaspaceSize=512m
```

## 💡 **Key Features Implemented**

### **1. 🌍 Global Dependencies**
- **Like npm -g**: Dependencies added globally are included in every new project
- **Automatic Integration**: New projects automatically get global dependencies
- **Easy Management**: Add/remove global dependencies with simple commands

### **2. 🏢 Custom Registries**
- **Corporate Support**: Add private/corporate Maven repositories
- **Multiple Registries**: Support for multiple custom registries
- **URL Configuration**: Custom Maven Central mirrors and search APIs

### **3. ⚙️ User Preferences**
- **Author/License**: Default author and license for new projects
- **Kotlin Version**: Default Kotlin version preference
- **Build Optimizations**: Memory settings applied to all projects

### **4. 🔄 Backward Compatibility**
- **Project-Local Priority**: Local `kpm.toml` still takes precedence
- **No Breaking Changes**: Existing projects continue to work unchanged
- **Gradual Adoption**: Users can opt-in to global configuration

## 🎯 **Workflow Examples**

### **One-Time Setup (Like npm/bun/pip)**
```bash
# Initialize global configuration
kpm config init

# Set user preferences
kpm config set default_author "Jane Developer"
kpm config set max_heap_size 16g

# Add commonly used dependencies
kpm config add-global timber com.jakewharton.timber:timber:5.0.1
kpm config add-global retrofit com.squareup.retrofit2:retrofit:2.9.0

# Add corporate registry
kpm config registry corporate https://nexus.company.com/maven-public/
```

### **Every New Project Gets Your Preferences**
```bash
# Create new project
kpm new MyApp --android

# ✅ Automatically includes:
# - Your preferred author name
# - Your memory optimization settings
# - Your global dependencies (timber, retrofit)
# - Your repository preferences
```

## 🎉 **Implementation Complete**

KPM now provides the **exact same global configuration experience** as npm, bun, and pip:

- ✅ **Global config file** (`~/.kpm/config.toml`)
- ✅ **Global dependencies** (like `npm -g`)
- ✅ **Custom registries** (like npm registry config)
- ✅ **User preferences** (author, license, build settings)
- ✅ **Automatic integration** with new projects
- ✅ **Familiar commands** (`config init`, `config set`, `add-global`)

**KPM users now have the same powerful global configuration capabilities they're used to from other package managers!** 🚀
