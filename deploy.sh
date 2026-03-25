#!/usr/bin/env bash
# =============================================================================
# Maven build + GPG import + deploy to Maven Central
# (drop-in replacement for your Travis CI job)
# =============================================================================

set -euo pipefail  # Fail fast on errors

echo "=== Importing GPG signing key ==="

# Import the secret key (base64-decoded from env var)
echo "$GPG_SECRET_KEYS" | base64 --decode | ${GPG_EXECUTABLE:-gpg} --import

# Import ownertrust (base64-decoded from env var)
echo "$GPG_OWNERTRUST" | base64 --decode | ${GPG_EXECUTABLE:-gpg} --import-ownertrust

echo "=== Installing dependencies (skip tests & signing) ==="
mvn --settings .maven.xml \
    install \
    -DskipTests=true \
    -Dgpg.skip=true \
    -Dmaven.javadoc.skip=true \
    -B -V

echo "=== Building and deploying release to Maven Central ==="
mvn clean deploy -X \
    --settings .maven.xml \
    -DskipTests=true \
    -B -U \
    -Possrh

echo "=== Release completed successfully! ==="