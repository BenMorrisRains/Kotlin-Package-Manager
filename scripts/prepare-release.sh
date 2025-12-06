#!/bin/bash

# Prepare KPM release
# Usage: ./scripts/prepare-release.sh <version>

set -e

VERSION=${1:-"1.0.0"}
REPO_URL="https://github.com/BenMorrisRains/Kotlin-Package-Manager"

echo "🚀 Preparing KPM v$VERSION for release..."

# 1. Build and test
echo "🔨 Building and testing..."
./gradlew build test

# 2. Create release tarball
echo "📦 Creating release tarball..."
git archive --format=tar.gz --prefix=kpm-$VERSION/ HEAD > kpm-$VERSION.tar.gz

# 3. Calculate SHA256
echo "🔐 Calculating SHA256..."
SHA256=$(shasum -a 256 kpm-$VERSION.tar.gz | cut -d' ' -f1)
echo "SHA256: $SHA256"

echo "✅ Release prepared!"
echo ""
echo "📋 Next steps:"
echo "1. Push tag: git tag v$VERSION && git push origin v$VERSION"
echo "2. Create GitHub release with kpm-$VERSION.tar.gz"
echo "3. Update Homebrew formula in homebrew-kpm repository:"
echo "   - Update version to v$VERSION"
echo "   - Update SHA256 to: $SHA256"
echo ""
echo "📦 Release file: kpm-$VERSION.tar.gz"
echo "🔐 SHA256: $SHA256"
echo "🌐 Repository: $REPO_URL"
