#!/usr/bin/env bash

# Version Bump Script for Aurelay
# Usage: ./scripts/bump_version.sh [major|minor|patch|build]

set -e

GRADLE_FILE="app/build.gradle.kts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if file exists
if [ ! -f "$GRADLE_FILE" ]; then
    print_error "build.gradle.kts not found at $GRADLE_FILE"
    exit 1
fi

# Extract current version
CURRENT_VERSION_CODE=$(grep -oP 'versionCode = \K\d+' "$GRADLE_FILE")
CURRENT_VERSION_NAME=$(grep -oP 'versionName = "\K[^"]+' "$GRADLE_FILE")

print_info "Current version: $CURRENT_VERSION_NAME (code: $CURRENT_VERSION_CODE)"

# Parse version name
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION_NAME"

# Determine what to bump
BUMP_TYPE="${1:-patch}"

case "$BUMP_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    build)
        # Only bump version code, not version name
        NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
        print_info "Bumping version code only: $CURRENT_VERSION_CODE → $NEW_VERSION_CODE"
        
        # Update version code
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" "$GRADLE_FILE"
        else
            sed -i "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" "$GRADLE_FILE"
        fi
        
        print_info "✓ Version code updated successfully!"
        exit 0
        ;;
    *)
        print_error "Invalid bump type: $BUMP_TYPE"
        echo "Usage: $0 [major|minor|patch|build]"
        echo ""
        echo "Examples:"
        echo "  $0 patch  # 1.0.0 → 1.0.1"
        echo "  $0 minor  # 1.0.0 → 1.1.0"
        echo "  $0 major  # 1.0.0 → 2.0.0"
        echo "  $0 build  # Only increment versionCode"
        exit 1
        ;;
esac

NEW_VERSION_NAME="$MAJOR.$MINOR.$PATCH"
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

print_info "New version: $NEW_VERSION_NAME (code: $NEW_VERSION_CODE)"

# Ask for confirmation
read -p "Proceed with version bump? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_warn "Version bump cancelled"
    exit 0
fi

# Update version name and code
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" "$GRADLE_FILE"
    sed -i '' "s/versionName = \"$CURRENT_VERSION_NAME\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"
else
    # Linux
    sed -i "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" "$GRADLE_FILE"
    sed -i "s/versionName = \"$CURRENT_VERSION_NAME\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"
fi

print_info "✓ Version updated successfully!"
print_info "  Version Name: $CURRENT_VERSION_NAME → $NEW_VERSION_NAME"
print_info "  Version Code: $CURRENT_VERSION_CODE → $NEW_VERSION_CODE"

# Check if git is available and we're in a git repo
if command -v git &> /dev/null && git rev-parse --git-dir > /dev/null 2>&1; then
    print_info ""
    print_info "Git commands to commit the version bump:"
    echo ""
    echo "  git add $GRADLE_FILE"
    echo "  git commit -m \"chore: bump version to $NEW_VERSION_NAME\""
    echo ""
fi

print_info "Done!"
