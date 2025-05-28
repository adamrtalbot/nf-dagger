/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package adamrtalbot.dagger

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.exception.ProcessException
import nextflow.executor.BashWrapperBuilder
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.trace.TraceRecord
import nextflow.extension.FilesEx
import nextflow.fusion.FusionAwareTask
import nextflow.util.ProcessHelper

/**
 * A {@link TaskHandler} implementation that delegates the actual task
 * execution to the Dagger CLI. It follows the same life-cycle as
 * {@code LocalTaskHandler} but starts a `dagger query` process instead of a
 * plain bash shell.
 */
@Slf4j
@CompileStatic
class DaggerTaskHandler extends TaskHandler implements FusionAwareTask {

    @Canonical
    static class TaskResult {
        Integer exitStatus
        String stdout
        String stderr
        Throwable error
    }

    private final Path exitFile
    private final Path outputFile
    private final Path errorFile
    private final Path wrapperFile

    private final Session session
    private final DaggerExecutor executor

    private Process process
    private volatile TaskResult result
    private boolean destroyed

    DaggerTaskHandler(TaskRun task, DaggerExecutor executor) {
        super(task)
        this.session = executor.session
        this.executor = executor

        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.wrapperFile = task.workDir.resolve(TaskRun.CMD_RUN)
    }

    /* ------------------------------------------------------------
     * Submit & run
     * ------------------------------------------------------------ */
    @Override
    void submit() {
        // Create the standard bash wrapper first.
        buildTaskWrapper()

        // Start Dagger CLI in a background thread to execute the container
        final builder = createLaunchProcessBuilder()
        final logFile = builder.redirectOutput().file()

        session.getExecService().submit({
            try {
                process = builder.start()
                int status = process.waitFor()
                // If dagger CLI itself fails (non-zero), treat as error
                if( status != 0 ) {
                    def err = new ProcessException("Dagger CLI returned status $status – check ${logFile}")
                    result = new TaskResult(error: err)
                }
                else {
                    // Parse the output files written by the task
                    result = parseResultFile()
                }
            }
            catch( Throwable e ) {
                result = new TaskResult(error: e)
            }
            finally {
                executor.getTaskMonitor().signal()
            }
        } as Callable<Object>)

        status = TaskStatus.SUBMITTED
    }

    private void buildTaskWrapper() {
        final wrapper = new BashWrapperBuilder(task.toTaskBean())
        wrapper.build()
    }

    private ProcessBuilder createLaunchProcessBuilder() {
        // Use dagger call to execute the container with proper volume mounting
        // This handles host directory references automatically
        final String image = task.getContainer()
        if( !image ) throw new IllegalStateException("Missing container image for Dagger task ${task.name}")
        
        // Get the absolute path of the work directory
        final String workDirPath = task.workDir.toAbsolutePath().toString()
        
        final List<String> daggerCmd = [
            'dagger', 'core',
            'container',
            'from', '--address', image,
            'with-mounted-directory', '--path', workDirPath, '--source', '.',
            'with-workdir', '--path', workDirPath, 
            'with-exec', '--args', 'bash,.command.run',
            'directory', '--path', workDirPath,
            'export', '--path', '.'
        ]
        
        // Execute the container and export the directory to persist changes
        final String cmdString = daggerCmd.join(' ')
        final List<String> cmd = [
                'bash', '-c',
                cmdString
        ]

        log.debug "[Dagger] launch cmd line: ${cmd.join(' ')}"

        final workDirFile = task.workDir.toFile()
        final logFile = new File(workDirFile, TaskRun.CMD_LOG)

        return new ProcessBuilder(cmd)
                .directory(workDirFile)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
    }

    private TaskResult parseResultFile() {
        // Read the standard Nextflow output files
        Integer exitCode = Integer.MAX_VALUE
        String stdout = ''
        String stderr = ''
        
        try {
            // Read exit code from .exitcode file written by bash wrapper
            if( Files.exists(exitFile) ) {
                String exitStr = Files.readString(exitFile).trim()
                exitCode = exitStr.isInteger() ? exitStr.toInteger() : Integer.MAX_VALUE
            }
            
            // Read stdout and stderr files
            if( Files.exists(outputFile) ) {
                stdout = Files.readString(outputFile)
            }
            
            if( Files.exists(errorFile) ) {
                stderr = Files.readString(errorFile)
            }
            
            return new TaskResult(exitStatus: exitCode, stdout: stdout, stderr: stderr)
        }
        catch( Exception e ) {
            return new TaskResult(error: new ProcessException("Unable to read task output files for ${task.name}", e))
        }
    }

    /* ------------------------------------------------------------
     * Polling lifecycle helpers
     * ------------------------------------------------------------ */

    @Override
    boolean checkIfRunning() {
        if( isSubmitted() && (process || result) ) {
            status = TaskStatus.RUNNING
            return true
        }
        return false
    }

    @Override
    boolean checkIfCompleted() {
        if( !isRunning() ) return false

        if( result ) {
            if( result.error ) {
                task.error = result.error
                task.exitStatus = Integer.MAX_VALUE
            }
            else {
                task.exitStatus = result.exitStatus
                // Persist stdout/stderr to files so usual NF machinery can consume.
                Files.writeString(outputFile, result.stdout ?: '', StandardCharsets.UTF_8)
                Files.writeString(errorFile, result.stderr ?: '', StandardCharsets.UTF_8)
            }
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            destroy()
            return true
        }
        return false
    }

    @Override
    void kill() {
        // For now, just call killTask()
        killTask()
    }

    protected void killTask() {
        // TODO: Implement proper process termination
        // For now, just return without doing anything
        return
    }

    private void destroy() {
        if( destroyed ) return
        try {
            process?.getInputStream()?.closeQuietly()
            process?.getOutputStream()?.closeQuietly()
            process?.getErrorStream()?.closeQuietly()
            process?.destroy()
        }
        finally {
            destroyed = true
        }
    }

    /* ------------------------------------------------------------
     * Trace record enrichment – nothing special for now, but we replicate
     * LocalTaskHandler behaviour so that `native_id` is available.
     * ------------------------------------------------------------ */

    @Override
    TraceRecord getTraceRecord() {
        final rec = super.getTraceRecord()
        if( process ) rec.put('native_id', ProcessHelper.pid(process))
        return rec
    }
} 