def call(Map pipelineParams = [:], Closure body) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'
    def withInstall = pipelineParams.containsKey('withInstall') ? pipelineParams.withInstall : false

    // Allow true as well as empty string.
    if (withInstall == true) {
        withInstall = ''
    }

    echo "PHP: ${php}"
    echo "Database: ${db}"

    def installParams = [
        "db-type": null,
        "db-user": "jenkins",
        "db-pass": "jenkins",
        "db-host": "127.0.0.1"
    ]

    // Check that none of the controlled parameters has been passed.
    if (withInstall != false) {
        installParams.each { key, val ->
            if (withInstall.contains('--' + key)) {
                error("The following parameters cannot be passed: db-type, db-user, db-pass, db-host")
            }
        }
    }

    // Validate the database value before building the image.
    switch (db) {
        case 'mysql':
            installParams['db-type'] = 'mysqli'
            break
        case 'postgres':
            installParams['db-type'] = 'pgsql'
            break
        default:
            error("Unknown db type ${db}. Supported types: mysqli, postgres")
    }

    def dockerFileContents = libraryResource 'uk/ac/strath/myplace/Dockerfile'

    // Docker does not like upper case letters in tags.
    def buildTag = "${BUILD_TAG}".toLowerCase()

    // Create Dockerfile in its own directory to prevent unnecessary context being sent.
    def dockerDir = "${BUILD_TAG}-docker"
    def image = null
    dir(dockerDir) {
        writeFile(file: 'Dockerfile', text: dockerFileContents)
        image = docker.build(buildTag)
    }

    // Create composer and npm cache directories if they don't exist.
    sh 'mkdir -p ${WORKSPACE}/.npm && chmod 777 ${WORKSPACE}/.npm \
            && mkdir -p ${WORKSPACE}/.composer && chmod 777 ${WORKSPACE}/.composer'

    // Nasty hack to get around the fact that we can't use withEnv to change the PATH on a container
    // (or any other method as far as I can see)
    // https://issues.jenkins.io/browse/JENKINS-49076
    def phpEnvHome = "/home/jenkins/.phpenv"
    def originalDockerPath = "${phpEnvHome}/shims:${phpEnvHome}/bin:/var/lib/nvm/versions/node/v14.15.0/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    def pathOnDocker = "${WORKSPACE}/${BUILD_NUMBER}/ci/bin:${originalDockerPath}"

    image.inside("-e PATH=${pathOnDocker}") {

        // Start database.
        switch (db) {
            case 'mysql':
                sh 'sudo service mysql start'
                break
            case 'postgres':
                error('postgres not yet supported')
                break
            default:
                error("Unknown db type ${db}. Supported types: mysqli, postgres")
        }


        // Set composer and npm directories to allow caching of downloads between jobs.
        withEnv(["npm_config_cache=${WORKSPACE}/.npm", "COMPOSER_CACHE_DIR=${WORKSPACE}/.composer"]) {

            // Install plugin ci.
            sh 'composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ${BUILD_NUMBER}/ci ^3'
        }

        if (withInstall != false) {
            sh 'moodle-plugin-ci install --db-host 127.0.0.1 --db-type mysqli --db-user jenkins --db-pass jenkins ' + withInstall
        }

        body()

    }


    // TODO: Cleanup stuff should be in a finally block probably.
    sh "docker rmi ${buildTag}"

    cleanWs deleteDirs: true, notFailBuild: true, patterns: [
        [pattern: "${BUILD_NUMBER}", type: 'INCLUDE'],
        [pattern: "${dockerDir}", type: 'INCLUDE']
    ]

}