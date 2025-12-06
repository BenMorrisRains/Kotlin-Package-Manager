#!/bin/bash

# Update Homebrew formula with new release information
# Usage: ./scripts/update-homebrew-formula.sh <version> <sha256>

set -e

VERSION=$1
SHA256=$2
HOMEBREW_DIR="../homebrew-kpm"

if [ -z "$VERSION" ] || [ -z "$SHA256" ]; then
    echo "❌ Usage: $0 <version> <sha256>"
    echo "Example: $0 1.0.0 abc123def456..."
    exit 1
fi

if [ ! -d "$HOMEBREW_DIR" ]; then
    echo "❌ Homebrew tap directory not found: $HOMEBREW_DIR"
    echo "Make sure you have cloned https://github.com/BenMorrisRains/homebrew-kpm"
    exit 1
fi

echo "🍺 Updating Homebrew formula for KPM v$VERSION..."

# Update the formula
sed -i.bak "s|archive/v.*\.tar\.gz|archive/v$VERSION.tar.gz|g" "$HOMEBREW_DIR/kpm.rb"
sed -i.bak "s/sha256 \".*\"/sha256 \"$SHA256\"/g" "$HOMEBREW_DIR/kpm.rb"

# Remove backup file
rm -f "$HOMEBREW_DIR/kpm.rb.bak"

echo "✅ Formula updated!"
echo ""
echo "📋 Next steps:"
echo "1. Review the changes:"
echo "   cd $HOMEBREW_DIR && git diff"
echo ""
echo "2. Commit and push:"
echo "   cd $HOMEBREW_DIR"
echo "   git add kpm.rb"
echo "   git commit -m \"Update KPM to v$VERSION\""
echo "   git push origin main"
echo ""
echo "3. Test the updated formula:"
echo "   brew uninstall kpm || true"
echo "   brew install --build-from-source BenMorrisRains/kpm/kpm"
