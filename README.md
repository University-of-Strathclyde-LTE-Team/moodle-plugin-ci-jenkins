This is a shared library for [Jenkins](https://www.jenkins.io/) for running 
[moodle-plugin-ci](https://github.com/moodlehq/moodle-plugin-ci)

It is currently a work in progress and contributions are very welcome!

#Feature support

##Supported

* multiple PHP versions
* mysql and postgres

##Not supported

* behat
* additional PHP modules

#Requirements

Docker must be available on the Jenkins server.

#Usage

The library is intended for use in Jenkins declarative pipelines (although may work in other scenarios).

It provides two custom steps.

## withMoodlePluginCiContainer

This starts up a docker container with a suitable environment for running moodle-plugin-ci.

###Parameters

* **php**: a PHP version, e.g. 7.4
* **db**: mysql or postgres
* **withInstall**: command line parameters for the moodle-plugin-ci install command. If this is 
not passed the install command will not run, but it may be empty. The following parameters are managed
  by the step and are not permitted: db-type, db-user, db-pass, db-host
  
The step also expects a code block which will be run inside the container, e.g.

    withMoodlePluginCiContainer(php: 7.4, withInstall: '--branch MOODLE_38_STABLE --plugin plugin') {
        sh 'moodle-plugin-ci phplint'
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

# Workspace

Please note that withMoodlePluginCiContainer runs in the root of the workspace
**and cleans it up after running**. If this is not acceptable please consider running it in
a custom workspace for the job. However, npm and composer packages are cached at the workspace
level (and are not cleaned up) in order to prevent re-downloading all dependencies for each run.

# Contributing

Any pull requests for improvements are very welcome. The project uses git flow so
please raise any pull requests against the **develop** branch :)