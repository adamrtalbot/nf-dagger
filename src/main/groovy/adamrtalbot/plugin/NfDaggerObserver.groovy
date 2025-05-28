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

package adamrtalbot.plugin

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.processor.TaskProcessor

/**
 * Implements an observer that allows implementing custom
 * logic on nextflow execution events.
 */
@Slf4j
@CompileStatic
class NfDaggerObserver implements TraceObserver {

    @Override
    void onFlowCreate(Session session) {
        println "Pipeline is starting! 🚀"
    }

    @Override
    void onFlowComplete() {
        println "Pipeline complete! 👋"
    }

    @Override
    void onProcessCreate(TaskProcessor process) {
        // Enforce the presence of a container directive for processes meant to run with the
        // Dagger executor. This check happens as soon as the process is registered with
        // the session, therefore giving immediate feedback to the user.

        if( process == null ) return

        // Determine whether this process is configured to use the Dagger executor.
        // We consider it a Dagger process if:
        //  1. The executor instance name equals 'dagger' (preferred when an executor
        //     has already been resolved), OR
        //  2. The process configuration explicitly sets the `executor` directive to
        //     'dagger'.
        final boolean isDaggerExecutor =
                (process.executor?.name?.equalsIgnoreCase('dagger')) ||
                (process.config?.getProperty('executor')?.toString()?.equalsIgnoreCase('dagger'))

        if( !isDaggerExecutor ) {
            // Not running with the Dagger executor – nothing to validate
            return
        }

        // Retrieve the container image declared for the process.
        final containerImage = process.config?.getProperty('container')

        if( !containerImage ) {
            final msg = "Process '${process.name}' executed by the Dagger executor requires a 'container' directive (e.g. container 'ubuntu:latest')."
            // Throwing an IllegalStateException aborts the pipeline early with a clear message
            throw new IllegalStateException(msg)
        }
    }
}
