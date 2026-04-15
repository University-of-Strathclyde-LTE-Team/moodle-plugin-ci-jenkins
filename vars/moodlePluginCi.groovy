def call(command, stageResult = null, buildResult = null) {
    // Prepend tool paths here rather than via withEnv — see JENKINS-49076.
    def shCommand = """
        export PATH="\$MOODLE_CI_TOOL_PATHS:\$PATH"
        moodle-plugin-ci ${command}
    """
    if (stageResult == null && buildResult == null) {
        sh shCommand
    } else {
        // Default to the standard catchError defaults.
        stageResult = stageResult ?: 'SUCCESS'
        buildResult = buildResult ?: 'FAILURE'
        catchError (stageResult: stageResult, buildResult: buildResult) {
            sh shCommand
        }
    }
}