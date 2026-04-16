def call(command, stageResult = null, buildResult = null) {
    def shCommand = """
        export PATH="\$WORKSPACE_BIN:\$PATH"
        \$WORKSPACE/ci/bin/moodle-plugin-ci ${command}
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
