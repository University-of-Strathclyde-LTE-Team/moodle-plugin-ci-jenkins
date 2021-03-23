def call(Map pipelineParams = [:]) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'

    echo "PHP: ${php}"
    echo "Database: ${db}"

    def dockerFileContents = libraryResource 'uk/ac/strath/myplace/Dockerfile'
    def dockerFile = "${WORKSPACE}/${BUILD_TAG}.Dockerfile";
    writeFile(file: dockerFile, text: dockerFileContents)

    // Docker does not like upper case letters in tags.
    def buildTag = "${BUILD_TAG}".toLowerCase()

    def image = docker.build(buildTag, "-f ${dockerFile} .")

    image.inside() {
        sh '''
            sudo service mysql start

            mkdir ${BUILD_NUMBER} && cd ${BUILD_NUMBER}

            composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ci ^3
            PATH="$PWD/ci/bin:$PATH"

            moodle-plugin-ci install --db-user jenkins --db-pass jenkins \
                                        --plugin ${WORKSPACE}/plugin

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

    new File(dockerFile).delete()
    sh "docker rmi ${buildTag}"

    cleanWs deleteDirs: true, notFailBuild: true, patterns: [
        [pattern: "${BUILD_NUMBER}", type: 'INCLUDE']
    ]

}