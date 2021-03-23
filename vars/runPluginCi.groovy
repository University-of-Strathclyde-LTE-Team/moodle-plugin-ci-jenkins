def call(Map pipelineParams) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'
    def commands = pipelineParams.commands ?: [:]

    echo "PHP: ${php}"
    echo "Database: ${db}"
    echo "Commands: ${commands}"

    def dockerfileContents = libraryResource 'uk/ac/strath/myplace/Dockerfile'

    writeFile(file: "${WORKSPACE}/test.docker", text: dockerfileContents)

}