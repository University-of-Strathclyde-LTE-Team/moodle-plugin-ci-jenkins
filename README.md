This is a shared library for [Jenkins](https://www.jenkins.io/) for running 
[moodle-plugin-ci](https://github.com/moodlehq/moodle-plugin-ci)

It is currently a work in progress and contributions are very welcome!

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

* **php**: a PHP version, e.g. 7.4
* **db**: mysql or postgres
* **withBehatServers**: chrome or firefox. This will start the relevant selenium container and the PHP
  built-in web server to allow the behat command to be run.
* **ciVersion** the version of moodle-plugin-ci to use
* **clean** boolean that will flag if we should clean the entire workspace after this step (default: true)
  If you do not clean the workspace you should call moodlePluginCleanWorkspace() manually later.
* **tag** a string that will help uniquely identify the build image if this step is being run n parallel (optional)
  
The step also expects a code block which will be run inside the container

### Example

    withMoodlePluginCiContainer(php: 7.4, db: postgres) {
        sh 'moodle-plugin-ci --help'
    }

The other two steps are intended to be run inside the container step.

## moodlePluginCiInstall

This runs the moodle-plugin-ci install command inside the container.

### Parameters

* command line parameters for the moodle-plugin-ci install command. The following parameters are managed
    by the step and are not permitted: db-type, db-user, db-pass, db-host

### Examples

    withMoodlePluginCiContainer(php: 7.4, db: postgres) {
        moodlePluginCiInstall('--branch MOODLE_310_STABLE --plugin plugin')
    }

Using a custom Moodle repo

    withMoodlePluginCiContainer(php: 7.4, db: postgres) {
        ssh-agent(['bitbucket-credentials']) {
             moodlePluginCiInstall('--branch MOODLE_310_STABLE --plugin plugin --repo ssh://git@bitbucket.example.com/moodle.git')
        }
    }

## moodlePluginCi

This is a wrapper step for moodle-plugin-ci which simplifies setting the stage and build status when the call
fails. For example, you may wish to run codechecker without failing your build if issues are found, but to mark
the build as unstable.

## Parameters

* **command**: the moodle-plugin-ci command to be run, along with any parameters
* **stageResult (optional)**: the stage result if the command fails (default SUCCESS)
* **buildResult (optional)**: the build result if the command fails (default FAILURE)

The result parameters are strings as supported by the Jenkins [catchError](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#catcherror-catch-error-and-set-build-result-to-failure) step.

For example:

    moodlePluginCi 'codechecker --max-warnings 0', 'SUCCESS', 'SUCCESS'

# Full pipeline example

## Single combination test

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

                    withMoodlePluginCiContainer(php: '7.4', ciVersion: '4') {

                        moodlePluginCiInstall("--branch MOODLE_310_STABLE --plugin plugin")

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

## Multiple combination test

If you wish to run multiple combinations of tests you must disable cleaning of the workspace in individual
withMoodlePluginCiContainer container steps, they also require that you define a unique tag for each of them.

This requires that you then add a post step to clean the workspace.

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
                    withMoodlePluginCiContainer(php: '7.4', tag: 'm311-php74', clean: false) {
                        moodlePluginCiInstall("--branch MOODLE_311_STABLE --plugin plugin")

                        moodlePluginCi 'phplint'
                        moodlePluginCi 'phpcpd', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'phpmd', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'codechecker --max-warnings 0', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'phpdoc', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'validate', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'savepoints', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'mustache', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'grunt --max-lint-warnings 0', 'UNSTABLE', 'UNSTABLE'
                        moodlePluginCi 'phpunit'
                    }
                    withMoodlePluginCiContainer(php: '8.1', tag: 'm401-php81', clean: false) {
                        moodlePluginCiInstall("--branch MOODLE_401_STABLE --plugin plugin")

                        moodlePluginCi 'phpunit'
                    }
                }
            }
        }

        post {
            always {
                moodlePluginCleanWorkspace()
            }
        }
    }

## Matrix combination example

If you want to make many combinations of environment be tested you can also use a matrix, each combination
will run in parallel.

It is important that in this setup that you ensure that each tag will be unique, to prevent multiple docker
images from having the same identifier. You should also ensure that you allow the workspace to be cleaned up.

    pipeline {
    agent any
    
        options {
            checkoutToSubdirectory('plugin')
        }
    
        stages {
            stage("Plugin CI") {
                matrix {
                    axes {
                        axis {
                            name 'MOODLE'
                            values 'MOODLE_311_STABLE', 'MOODLE_401_STABLE'
                        }
                        axis {
                            name 'PHP'
                            values '7.4', '8.1'
                        }
                        axis {
                            name 'DATABASE'
                            values 'mysql'
                        }
                    }
    
                    excludes {
                        exclude {
                            axis {
                                name 'MOODLE'
                                notValues 'MOODLE_401_STABLE'
                            }
                            axis {
                                name 'PHP'
                                values '8.1'
                            }
                        }
                        exclude {
                            axis {
                                name 'MOODLE'
                                notValues 'MOODLE_311_STABLE'
                            }
                            axis {
                                name 'PHP'
                                values '7.4'
                            }
                        }
                    }
                    stages {
                        stage ('Tests') {
                            agent {
                                node{
                                    label any
                                }
                            }
                            steps {
                                withMoodlePluginCiContainer(
                                    php: "${PHP}",
                                    db: "${DATABASE}",
                                    ciVersion: '4',
                                    tag: "${MOODLE}-${PHP}-${DATABASE}"
                                ) {
                                    moodlePluginCiInstall("--branch ${MOODLE} --plugin ./plugin")
    
                                    moodlePluginCi 'phplint'
                                    moodlePluginCi 'phpcpd', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'phpmd', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'codechecker --max-warnings 0', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'phpdoc', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'validate', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'savepoints', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'mustache', 'UNSTABLE', 'UNSTABLE'
                                    moodlePluginCi 'grunt --max-lint-warnings 0', 'UNSTABLE', 'UNSTABLE'
    
                                    moodlePluginCi 'phpunit'
                                }
                            }
                        }
                    }
                }
            }
        }
    }

# Workspace

Please note that withMoodlePluginCiContainer runs in the root of the workspace
**and cleans it up after running**. If this is not acceptable please consider running it in
a custom workspace for the job. However, npm and composer packages are cached at the workspace
level (and are not cleaned up) in order to prevent re-downloading all dependencies for each run.

# Contributing

Any pull requests for improvements are very welcome. The project uses git flow so
please raise any pull requests against the **develop** branch :)
