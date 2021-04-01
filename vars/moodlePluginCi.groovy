def call(command, stageResult = null, buildResult = null) {
    def fullCommand = "moodle-plugin-ci ${command}"
    if (stageResult == null && buildResult == null) {
        sh fullCommand
    } else {
        // Default to the standard catchError defaults.
        stageResult = stageResult ?: 'SUCCESS'
        buildResult = buildResult ?: 'FAILURE'
        catchError (stageResult: stageResult, buildResult: buildResult) {
            sh fullCommand
        }
    }
}