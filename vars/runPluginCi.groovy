def call(Map pipelineParams) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'

    echo "PHP: ${php}"
    echo "Database: ${db}"

}