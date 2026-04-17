This is a shared library for [Jenkins](https://www.jenkins.io/) for running 
[moodle-plugin-ci](https://github.com/moodlehq/moodle-plugin-ci)

# Feature support

## Supported

* multiple PHP versions
* mysql and postgres
* behat

## Not supported

* additional PHP modules

# Requirements

Docker must be available on the Jenkins server.

The following Jenkins plugins must also be installed:

* [Pipeline: Shared Groovy Libraries](https://plugins.jenkins.io/workflow-cps-global-lib/)
* [Docker Pipeline](https://plugins.jenkins.io/docker-workflow/)
* [Workspace cleanup](https://plugins.jenkins.io/ws-cleanup/)

# Usage

The library is intended for use in Jenkins declarative pipelines (although may work in other scenarios).

It should be set up as normal for a shared library in the Jenkins global settings.

It provides some custom steps.

## withMoodlePluginCiContainer

This starts up a docker container with a suitable environment for running moodle-plugin-ci.

### Parameters

* **php**: a PHP version, e.g. `'8.3'` (default: `'8.3'`)
* **db**: `mysql` or `postgres` (default: `mysql`)
* **ciVersion**: a Composer version constraint for moodle-plugin-ci, e.g. `'^4'`, `'^5'`, `'dev-main'` (default: `'^4'`)
* **browser**: `chrome` or `firefox`. Starts the relevant Selenium container and PHP built-in web server. **Required if you intend to run `moodlePluginCi 'behat'`**; omit for builds that do not run Behat.
* **seleniumVersion**: the Selenium image tag to use (default: `'4'`)
* **mysqlVersion**: the MySQL image tag to use when `db` is `mysql` (default: `'8.4'`)
* **postgresVersion**: the PostgreSQL image tag to use when `db` is `postgres` (default: `'16'`)

The step also expects a code block which will be run inside the container.

### Example

    withMoodlePluginCiContainer(php: '8.3', db: 'postgres') {
        sh 'moodle-plugin-ci --help'
    }

### Behat example

    withMoodlePluginCiContainer(php: '8.3', db: 'mysql', browser: 'chrome') {
        moodlePluginCiInstall('--branch MOODLE_405_STABLE --plugin plugin')
        moodlePluginCi 'behat'
    }

The other two steps are intended to be run inside the container step.

## moodlePluginCiInstall

This runs the moodle-plugin-ci install command inside the container.

### Parameters

* command line parameters for the moodle-plugin-ci install command. The following parameters are managed
    by the step and are not permitted: db-type, db-user, db-pass, db-host

### Examples

    withMoodlePluginCiContainer(php: '8.3', db: 'postgres') {
        moodlePluginCiInstall('--branch MOODLE_405_STABLE --plugin plugin')
    }

Using a custom Moodle repo

    withMoodlePluginCiContainer(php: '8.3', db: 'postgres') {
        ssh-agent(['bitbucket-credentials']) {
             moodlePluginCiInstall('--branch MOODLE_405_STABLE --plugin plugin --repo ssh://git@bitbucket.example.com/moodle.git')
        }
    }

## moodlePluginCi

This is a wrapper step for moodle-plugin-ci which simplifies setting the stage and build status when the call
fails. For example, you may wish to run codechecker without failing your build if issues are found, but to mark
the build as unstable.

### Parameters

* **command**: the moodle-plugin-ci command to be run, along with any parameters
* **stageResult (optional)**: the stage result if the command fails (default SUCCESS)
* **buildResult (optional)**: the build result if the command fails (default FAILURE)

The result parameters are strings as supported by the Jenkins [catchError](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#catcherror-catch-error-and-set-build-result-to-failure) step.

For example:

    moodlePluginCi 'codechecker --max-warnings 0', 'SUCCESS', 'SUCCESS'

# Full pipeline example

    pipeline {
        agent any

        options {
            checkoutToSubdirectory('plugin')
            disableConcurrentBuilds()
        }

        stages {

            stage("Plugin CI") {

                agent any

                steps {

                    withMoodlePluginCiContainer(php: '8.3') {

                        moodlePluginCiInstall('--branch MOODLE_405_STABLE --plugin plugin')

                        moodlePluginCi 'phplint'
                        moodlePluginCi 'phpcpd', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'phpmd', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'codechecker --max-warnings 0', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'phpdoc', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'validate', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'savepoints', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'mustache', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'grunt --max-lint-warnings 0', 'SUCCESS', 'SUCCESS'
                        moodlePluginCi 'phpunit'
                    }
                }
            }
        }
    }


# Workspace

`withMoodlePluginCiContainer` does not clean up the workspace. Pipelines are responsible
for their own cleanup, typically in a `post { cleanup { ... } }` block.

The step caches Composer and npm packages under `.cache/` in the workspace. To retain
these between builds and avoid re-downloading dependencies on every run, exclude this
directory from cleanup:

    post {
        cleanup {
            cleanWs(patterns: [[pattern: '.cache/**', type: 'EXCLUDE']])
        }
    }

If you omit this exclusion, packages will be re-downloaded on every build.

# Contributing

Any pull requests for improvements are very welcome. The project uses git flow so
please raise any pull requests against the **develop** branch :)
