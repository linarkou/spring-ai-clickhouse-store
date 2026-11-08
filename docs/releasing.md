# Releasing

The **Release** workflow automates a stable release from the repository's default
branch or a matching `release/<major>.<minor>.x` branch. Start it in Actions → Release
→ Run workflow, select the source branch, and enter an unused version such as
`2.0.3` (no `v`, `-SNAPSHOT`, or prerelease suffix).
A push to the default branch does not publish a release automatically.

From the default branch, version `2.0.3` creates or updates `release/2.0.x`, while
`2.1.0` uses `release/2.1.x`. An existing release branch is merged with the selected
default-branch commit so its maintenance changes are preserved. Merge conflicts
stop the workflow before any branch, tag, or artifact is published.

When starting from `release/2.0.x`, the version must start with `2.0.`; for example,
`2.0.3` is accepted and `2.1.0` is rejected. The workflow uses the selected release
branch commit directly and does not create or update a release branch in this case.
Other source branches and tag refs are rejected.

## One-time setup

Allow GitHub Actions to write repository contents and ensure branch and tag rules
permit `github-actions[bot]` to update `release/*` branches and create `v*` tags.
The workflow requests `contents: write`; it does not need a personal access token
or GitHub Packages permission.

Configure these repository secrets (the existing names are retained):

| Secret | Value |
| --- | --- |
| `OSSRH_USERNAME` | Central Publisher Portal **user token username** |
| `OSSRH_TOKEN` | Central Publisher Portal **user token password** |
| `GPG_PRIVATE_KEY` | ASCII-armored private signing key |
| `GPG_PASSPHRASE` | Passphrase for that signing key |

Despite their historical names, the OSSRH secrets must contain a current
[Central Portal token](https://central.sonatype.org/publish/generate-portal-token/),
not a legacy OSSRH account password. The account must own the `io.github.linarkou`
namespace, and the signing public key must be available from a supported public
keyserver. See Sonatype's [signing requirements](https://central.sonatype.org/publish/requirements/gpg/).

## What the workflow does

1. Validates a stable `major.minor.patch` version, the source branch, and the
   presence of publishing secrets. A release branch must be named exactly
   `release/<major>.<minor>.x` for the requested version.
2. Uses the source commit selected when the workflow was dispatched. For a new
   release from the default branch, it merges an existing matching release branch
   with that commit, or starts a new release branch from that commit.
   Sets `<revision>` in the root `pom.xml` with Maven `versions:set-property`;
   the parent and all modules use `${revision}`. The flatten plugin resolves these
   placeholders in installed and deployed POMs, including the parent POM.
3. Runs `mvn clean verify -P integration-tests` on that exact revision, including
   the ClickHouse tests. The Ubuntu runner provides Docker; the first run also
   downloads the embedding model used by the tests.
4. After successful verification, pushes the prepared source commit to the
   matching release branch when running from the default branch. Branch updates
   never force-push; retries leave a branch that already contains the source commit
   in place. When running from a release branch, this step is skipped.
   Creates a release commit that changes only the root POM, an annotated tag
   `v<version>`, and a draft GitHub Release with generated release notes.
   The release commit is pushed only as a tag, while the release branch contains
   the source commit before the version change. The default branch retains its
   development revision. The released POM contains the selected stable revision.
5. Builds again from that same commit with the `release` profile, attaches
   sources and Javadoc, signs the artifacts, and publishes all four modules
   together through Sonatype's Central Publishing Maven plugin. The plugin waits
   for the deployment to reach `published` before the workflow continues.
6. Publishes the GitHub Release only after Maven Central succeeds. Repository
   permissions and Central credentials are not needed by ordinary PR builds.

The source plugin uses `jar-no-fork` to avoid a second lifecycle invocation during
release packaging. The workflow invokes publication directly: a release created
with `GITHUB_TOKEN` does not trigger a second release-event workflow.

## Retrying a failed release

Re-run the original workflow, or dispatch the same version from the default branch
or its matching release branch.
The workflow never moves or replaces an existing tag. Before reusing it, it checks
that the release commit's parent belongs to the selected source branch or the
matching release branch. It applies `versions:set-property` to that parent and uses
`git diff` to confirm the resulting source tree exactly matches the tag. It then
checks out and tests that exact
commit, even if the default branch has advanced.

If all four artifact POMs are already visible on Maven Central, publication is
skipped and the GitHub Release can be completed. A version already present on
Central without a matching release tag is rejected. A partial publication also
fails so the existing deployment can be investigated.

If a run fails or times out **after uploading to Central**, first inspect its
[Central Portal deployment](https://central.sonatype.com/publishing/deployments).
Wait for an in-progress deployment to finish and for the four POMs to become
visible on `repo.maven.apache.org` before retrying. Do not submit another upload
while that deployment is pending: the public repository can lag Portal state.
For a failed validation, inspect the logs and deployment errors; changes to source
or build configuration require a new version/tag. Published versions are immutable.

All release versions share a concurrency group, and an active deployment is never
cancelled by a newer run. GitHub may replace an older **pending** run with a newly
queued one; dispatch a displaced version again if it is still needed. A failure
leaves the tag and draft available for investigation; public release publication
remains conditional on Central success.
