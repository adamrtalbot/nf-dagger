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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.AbortOperationException
import nextflow.executor.Executor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.LocalPollingMonitor
import nextflow.processor.TaskRun
import nextflow.util.ServiceName
import org.pf4j.ExtensionPoint

/**
 * Simple executor that runs Nextflow tasks by delegating their execution to the
 * Dagger CLI. It relies on {@link DaggerTaskHandler} for the heavy lifting.
 *
 * NOTE: this first version is intentionally very similar to the built-in
 * {@code local} executor – the only difference is how the task is actually
 * launched.
 */
@Slf4j
@CompileStatic
@ServiceName('dagger')
class DaggerExecutor extends Executor implements ExtensionPoint {

    @Override
    protected void register() {
        super.register()
        final String version
        try {
            final proc = new ProcessBuilder('dagger', 'version').redirectErrorStream(true).start()
            version = proc.inputStream.text.trim()
            proc.waitFor()
        }
        catch( IOException e ) {
            throw new AbortOperationException("Dagger CLI not found in PATH - the Dagger executor requires Dagger 1.0 or later", e)
        }
        if( !isSupportedVersion(version) )
            throw new AbortOperationException("The Dagger executor requires Dagger CLI 1.0 or later, found: ${version}")
        log.debug "[Dagger] Using ${version}"
    }

    /**
     * Checks `dagger version` output is 1.0 or later. 0.x prints "dagger v0.21.11 (...)",
     * 1.0 prints a multi-line report starting "version: v1.0.0-beta.21".
     */
    static boolean isSupportedVersion(String versionOutput) {
        final m = versionOutput =~ /\bv(\d+)\./
        return m.find() && m.group(1).toInteger() >= 1
    }

    @Override
    protected TaskMonitor createTaskMonitor() {
        // Reuse the same local polling strategy – task execution still happens
        // on the local node, therefore resource accounting is identical.
        return LocalPollingMonitor.create(session, config, name)
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        assert task
        assert task.workDir
        log.trace "[Dagger] launching process > ${task.name} -- work folder: ${task.workDirStr}"
        new DaggerTaskHandler(task, this)
    }

    /**
     * The container is spawned by Dagger itself, so from the Nextflow
     * perspective the executor is *container-native*.
     */
    @Override
    boolean isContainerNative() {
        return true
    }

    @Override
    String containerConfigEngine() {
        // Dagger currently relies on the local Docker engine under the hood.
        return 'docker'
    }
} 