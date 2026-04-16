# withMoodlePluginCiContainer — Issues To Resolve

## Bugs / Robustness (all resolved)

- [x] **DB wait loop silently succeeds on timeout** (`withMoodlePluginCiContainer.groovy`)
- [x] **NVM install: `PROFILE=/dev/null` scoped to wrong process**
- [x] **`set +x` not restored in NVM install block**
- [x] **`def db = "${DB}"` produces the string `"null"`** (`moodlePluginCiInstall.groovy`)

## Correctness / Design (all resolved)

- [x] **Behat `.env` file writing removed**
- [x] **PHP built-in server accounts for Moodle 5.1+ `public/` directory**
- [x] **`cleanWs()` moved to `finally` block**

## Code Quality (current sprint)

- [ ] **`withBehatServers` parameter renamed to `browser`** (`withMoodlePluginCiContainer.groovy`)
  The parameter controls which browser Selenium uses; `browser` is more descriptive.
  Rename in `withMoodlePluginCiContainer`; `strathStandardPluginPipeline` already uses
  `browser` so no change needed there.

- [ ] **Composer install not verified** (`withMoodlePluginCiContainer.groovy`)
  `curl ... | php` can silently fail if curl errors (no `-o pipefail`). Add a simple
  `./composer --version` after the install step as a quick sanity check.

- [ ] **`ciVersion` parameter forces `^` prefix** (`withMoodlePluginCiContainer.groovy`)
  Version is assembled as `^${ciVersion}` so callers can only pass bare numbers (e.g. `'4'`).
  Move `^` into the default value (`'^4'`) and interpolate the value directly, so any valid
  Composer version constraint or branch (`'^5'`, `'dev-main'`, `'4.5.*'`) works.

- [ ] **`MOODLE_CI_TOOL_PATHS` / PATH manipulation can be simplified**
  `MOODLE_CI_TOOL_PATHS` exists to work around JENKINS-49076. Since `sh` runs in the
  workspace, `composer` can be called as `./composer` in setup. The env var can then be
  dropped and each step can use the full path `${env.WORKSPACE}/ci/bin/moodle-plugin-ci`.

- [ ] **DB image versions are hard-coded** (`withMoodlePluginCiContainer.groovy`)
  `mysql:8.4` and `postgres:16` are hard-coded. Add `mysqlVersion` / `postgresVersion`
  parameters. Defaults should be a major version tag (e.g. `'8.4'`, `'16'`) — not `'latest'`
  as that breaks reproducibility, but not a specific patch either.

- [ ] **NVM version is hard-coded** (`withMoodlePluginCiContainer.groovy`)
  `v0.39.7` in the install URL will go stale. Extract as a clearly visible constant at the
  top of the file so it is easy to bump.

- [ ] **Selenium uses `:latest` tag** (`withMoodlePluginCiContainer.groovy`)
  `selenium/standalone-${browser}:latest` makes builds non-reproducible. Add a
  `seleniumVersion` parameter with a pinned default.

- [ ] **`nvm install --lts` may download an unused Node version** (`withMoodlePluginCiContainer.groovy`)
  Verify whether moodle-plugin-ci's composer post-install script runs npm before
  VendorInstaller switches to the .nvmrc version. If not, replace with `nvm --version`.

- [ ] **`moodlePluginCiInstall` error message always lists all params** (`moodlePluginCiInstall.groovy`)
  When a forbidden param is found the error always says all four param names. Report just
  the offending one.

## Performance

- [ ] **No caching for Composer or npm packages**
  Packages are downloaded fresh every build. Consider a shared host directory or named
  Docker volume for `COMPOSER_CACHE_DIR` and `npm_config_cache`.

- [ ] **Behaviour when `browser` is not specified** (`withMoodlePluginCiContainer.groovy`)
  When `browser` is omitted, no Selenium container is started and no Behat env vars are set,
  but `MOODLE_START_BEHAT_SERVERS=false` is still passed. If the caller then runs
  `moodlePluginCi 'behat'` it will fail trying to reach a WebDriver server that doesn't
  exist. Should we: (a) simply not set `MOODLE_START_BEHAT_SERVERS=false` when `browser` is
  absent and let moodle-plugin-ci manage its own servers, (b) error early if behat is called
  without a browser configured, or (c) document that behat requires `browser` to be set?

## Deferred

- [ ] **Composer installer integrity check** — Low priority; HTTPS + official domain is
  reasonable in practice.
- [ ] **Groovy lint** — Address once all functional issues are resolved.
