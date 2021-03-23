def call(Map pipelineParams) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'
    def commands = pipelineParams.commands ?: [:]

    echo "PHP: ${php}"
    echo "Database: ${db}"
    echo "Commands: ${commands}"

    def dockerFileContents = libraryResource 'uk/ac/strath/myplace/Dockerfile'
    def dockerFile = "${WORKSPACE}/${BUILD_TAG}.Dockerfile";
    writeFile(file: dockerFile, text: dockerFileContents)

    def image = docker.build("${BUILD_TAG}", "-f ${dockerFile} .")

    image.inside() {
        sh '''
            sudo service mysql start

            mkdir ${BUILD_NUMBER} && cd ${BUILD_NUMBER}

            composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ci ^3
            PATH="$PWD/ci/bin:$PATH"
            '''
    }

}