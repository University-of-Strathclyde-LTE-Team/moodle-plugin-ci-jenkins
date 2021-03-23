def call(Map pipelineParams, Closure body) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'
    def install = pipelineParams.install

    if (install == null) {
        install = true
    }

    echo "PHP: ${php}"
    echo "Database: ${db}"

    def dockerFileContents = libraryResource 'uk/ac/strath/myplace/Dockerfile'
    def dockerFile = "${WORKSPACE}/${BUILD_TAG}.Dockerfile";
    sh 'env > ${WORKSPACE}/${BUILD_TAG}.env'

    writeFile(file: dockerFile, text: dockerFileContents)

    def image = docker.build("${BUILD_TAG}", "-f ${dockerFile} .")

    image.inside() {

        withEnv(["npm_config_cache=${WORKSPACE}/.npm", "COMPOSER_CACHE_DIR=${WORKSPACE}/composer-cache"]) {

            sh '''
                sudo service mysql start

                composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ci ^3
                export PATH="$PWD/ci/bin:$PATH"
                '''

            if (install) {
                sh 'moodle-plugin-ci install --db-user jenkins --db-pass jenkins \
                    --plugin ${WORKSPACE}/plugin'
            }

            body()

        }

    }

    new File(dockerFile).delete()
    sh "docker rmi ${BUILD_TAG}"

}