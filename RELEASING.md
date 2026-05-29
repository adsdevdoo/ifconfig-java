# Releasing to Maven Central

End-to-end instructions for publishing `rs.adsdev:ifconfig-java` to
[Maven Central](https://central.sonatype.com/) via the Central Portal.
The runtime flow is **push a `v*` git tag**; everything else is one-time
setup.

---

## One-time setup

### 1. Verify the `rs.adsdev` namespace

Central Portal needs proof you control `adsdev.rs`.

1. Sign in to [central.sonatype.com](https://central.sonatype.com/) with
   GitHub or Google.
2. **View Namespaces → Add Namespace** → enter `rs.adsdev`.
3. Central shows a TXT record value (random string). Add it to the
   `adsdev.rs` zone:

   ```
   Type: TXT
   Host: @
   Value: <token from Central>
   TTL:  300
   ```

4. Wait for DNS propagation (usually < 5 min) and hit **Verify** in the
   portal. Once green, the namespace is permanently yours and no further
   verification is needed for future artifacts under `rs.adsdev.*`.

### 2. Generate a release-only GPG key

Maven Central rejects unsigned artifacts. Don't reuse a personal key —
make a dedicated one with no expiry traps:

```bash
gpg --quick-generate-key "ADSDEV Release <release@adsdev.rs>" rsa4096 sign 2y
```

You'll be asked for a passphrase — pick a strong one, save it in a
password manager. Then publish the public half so Central can verify
signatures:

```bash
KEY_ID=$(gpg --list-secret-keys --keyid-format LONG | awk '/^sec/ {split($2,a,"/"); print a[2]; exit}')

# Upload to the keyserver Central trusts:
gpg --keyserver keys.openpgp.org   --send-keys "$KEY_ID"
gpg --keyserver keyserver.ubuntu.com --send-keys "$KEY_ID"
```

Export the private key for GitHub Actions (this is the secret you'll
paste into the workflow):

```bash
gpg --armor --export-secret-keys "$KEY_ID" > release-key.asc
# paste the contents into the GPG_PRIVATE_KEY GitHub secret, then:
shred -u release-key.asc
```

### 3. Generate Central Portal API credentials

In the portal: **profile menu → View Account → Generate User Token**.
You get a username and password pair (these are not your login password —
they're token credentials). Save both.

### 4. Add GitHub secrets

In `https://github.com/adsdevdoo/ifconfig-java/settings/secrets/actions`
add four secrets:

| Name               | Value                                                         |
|--------------------|---------------------------------------------------------------|
| `CENTRAL_USERNAME` | Token username from step 3                                    |
| `CENTRAL_PASSWORD` | Token password from step 3                                    |
| `GPG_PRIVATE_KEY`  | Full content of `release-key.asc` (including BEGIN/END lines) |
| `GPG_PASSPHRASE`   | The passphrase you set in step 2                              |

---

## Cutting a release

1. Update `<version>` in `pom.xml` to a clean semver (`1.0.1`, `1.1.0`,
   `2.0.0` — no `-SNAPSHOT`).
2. Commit and push to `master`.
3. Tag and push:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

4. The `Release` workflow on GitHub Actions runs automatically. It:
   - checks the tag matches `pom.xml`,
   - builds + tests,
   - generates sources + javadoc jars,
   - signs everything with the release GPG key,
   - uploads to Central Portal and waits for the artifact to be
     **published** (not just staged) — fails the job if Central rejects
     it.
5. New version appears under `rs.adsdev:ifconfig-java` on
   [search.maven.org](https://search.maven.org/) within ~15 min.

If the workflow fails after upload, the staged bundle is visible in
Central Portal's **Deployments** tab — fix the issue, **drop** the
broken bundle, bump the version (Central won't accept a re-upload at
the same coordinates), and tag again.

## Bumping to the next development version

Maven Central is immutable: once `1.0.1` is published it cannot be
overwritten. After a release, bump `pom.xml` to the next `-SNAPSHOT`
(e.g. `1.0.2-SNAPSHOT`) on `master` so day-to-day development doesn't
collide with the released coordinates.
