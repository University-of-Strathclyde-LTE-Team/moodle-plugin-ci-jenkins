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

    docker.build("${BUILD_TAG}", "-f ${dockerFile} .")

}