# Android Build Optimization

KPM automatically configures Android projects with memory and build optimizations to prevent common issues like OutOfMemoryError during builds.

## Automatic Optimizations Applied

### Memory Settings
```properties
# Increase heap size to 4GB and optimize garbage collection
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
```

### Build Performance
```properties
# Enable parallel builds and caching
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
```

### Android-Specific Optimizations
```properties
# Enable R8 full mode for better optimization
android.enableR8.fullMode=true
android.useAndroidX=true
android.enableJetifier=true
```

## Common Issues Resolved

1. **OutOfMemoryError during packaging** - Fixed with increased heap size
2. **Slow builds** - Fixed with parallel execution and caching
3. **Large APK sizes** - Fixed with R8 full mode optimization
4. **Dependency conflicts** - Fixed with AndroidX and Jetifier

## Manual Tuning

If you need to adjust memory settings for your specific machine:

```properties
# For machines with less RAM (adjust to 2GB)
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=256m

# For machines with more RAM (increase to 8GB)
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g
```

These settings are automatically applied to all Android projects created with:
- `kpm new MyApp --android`
- `kpm new MyApp --android --compose`
- `kpm init --type android-app`
