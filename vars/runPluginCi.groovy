def call(Map pipelineParams = [:]) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'

    echo "PHP: ${php}"
    echo "Database: ${db}"

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

    dir("${BUILD_NUMBER}") {
        image.inside() {
            // Start database.
            sh 'sudo service mysql start'

            // Set composer and npm directories to allow caching of downloads between jobs.
            withEnv(["npm_config_cache=${WORKSPACE}/.npm", "COMPOSER_CACHE_DIR=${WORKSPACE}/.composer"]) {
                // Install plugin ci.
                sh 'composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ci ^3'
            }

            withEnv(["PATH+MOODLEPLUGINCI=$PWD/ci"]) {
                sh '''
                    moodle-plugin-ci install --db-type mysqli --db-user jenkins --db-pass jenkins \
                                               --branch MOODLE_38_STABLE --plugin ${WORKSPACE}/plugin

                    moodle-plugin-ci phplint
                    moodle-plugin-ci phpcpd
                    moodle-plugin-ci phpmd
                    moodle-plugin-ci codechecker --max-warnings 0
                    moodle-plugin-ci phpdoc || true
                    moodle-plugin-ci validate || true
                    moodle-plugin-ci savepoints
                    moodle-plugin-ci mustache || true
                    # moodle-plugin-ci grunt --max-lint-warnings 0 || true
                    moodle-plugin-ci phpunit
                 '''
            }


        }
    }


    // TODO: Cleanup stuff should be in a finally block probably.
    sh "docker rmi ${buildTag}"

    cleanWs deleteDirs: true, notFailBuild: true, patterns: [
        [pattern: "${BUILD_NUMBER}", type: 'INCLUDE'],
        [pattern: "${dockerDir}", type: 'INCLUDE']
    ]

}