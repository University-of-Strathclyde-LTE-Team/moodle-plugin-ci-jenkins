def call(command, stageResult = null, buildResult = null) {
    def shCommand = """
        export PATH="\$WORKSPACE_BIN:\$PATH"
        \$WORKSPACE/ci/bin/moodle-plugin-ci ${command}
    """
    if (stageResult == null && buildResult == null) {
        sh shCommand
    } else {
        // Default to the standard catchError defaults.
        def effectiveStageResult = stageResult ?: 'SUCCESS'
        def effectiveBuildResult = buildResult ?: 'FAILURE'
        catchError(stageResult: effectiveStageResult, buildResult: effectiveBuildResult) {
            sh shCommand
        }
    }
}
