# nf-dagger plugin

A Nextflow executor plugin that enables running Nextflow pipelines using [Dagger](https://dagger.io/) containers.

> [!WARNING]
> This plugin is a POC and is not ready for any serious use.

## Overview

The nf-dagger plugin provides a custom Nextflow executor that leverages Dagger's container runtime to execute pipeline tasks.

## Prerequisites

- [Nextflow](https://nextflow.io/) 26.04.0 or later
- [Dagger CLI](https://docs.dagger.io/install) 1.0 (currently in beta) or later, installed and available in PATH
- Docker or compatible container runtime
- Container images for your pipeline processes

## Installation

### Local Development

1. Install to your local Nextflow installation:
   ```bash
   make install
   ```

2. Add a Nextflow configuration file:

```groovy
process.executor = 'dagger'

plugins {
    id 'nf-dagger@0.1.0'
}
```

2. Run a pipeline with the plugin:
   ```bash
   nextflow run -plugins seqeralabs/nf-canary -c nextflow.config -plugins nf-dagger@0.1.0
   ```

> [!WARNING]
> You will need the Dagger CLI installed and available in your PATH.

## Usage

### Basic Configuration

Configure your pipeline to use the Dagger executor:

```groovy
// nextflow.config
process.executor = 'dagger'

plugins {
    id 'nf-dagger@0.1.0'
}
```

### Container Requirements

Ensure your processes specify container images:

```groovy
process example {
    container 'ubuntu:20.04'
    
    input:
    path input_file
    
    output:
    path "output.txt"
    
    script:
    """
    echo "Processing ${input_file}" > output.txt
    """
}
```

They must have `bash` installed.

### Running Pipelines

Run your pipeline as usual:

```bash
nextflow run your-pipeline.nf -plugins nf-dagger@0.1.0
```

## Technical Implementation

### How It Works

1. **Task Submission**: Each Nextflow task is submitted to a Dagger container using the Dagger 1.0 CLI (`dagger -m core api call container from ...`)
2. **Volume Mounting**: The plugin automatically mounts:
   - Task work directory
   - Input file directories (following symlinks)
   - Pipeline `bin/` directory (if present)
3. **Execution**: Tasks run using Dagger's container runtime
4. **Result Collection**: Standard Nextflow output files are collected after execution

### Architecture

The plugin extends Nextflow's `TaskHandler` interface and implements:

- `DaggerExecutor`: Main executor class managing task lifecycle
- `DaggerTaskHandler`: Individual task execution handler
- Volume mounting logic with symlink resolution
- Standard Nextflow bash wrapper integration

## Limitations

### Dagger CLI version

The plugin uses the Dagger 1.0 command layout (`dagger -m core api call`), which replaces the deprecated `dagger core` command. The executor checks `dagger version` at startup and stops the run if the CLI is older than 1.0. See the [Dagger 1.0 migration guide](https://docs.dagger.io/migrate-to-v1/) for details.

To use a remote or cloud engine, set `DAGGER_ENGINE` (for example `DAGGER_ENGINE=cloud`). This replaces `_EXPERIMENTAL_DAGGER_RUNNER_HOST` and `DAGGER_CLOUD_ENGINE` from Dagger 0.x.

### Cancelling tasks

Cancelling tasks is not supported, be careful before launching long running tasks.

### Resource directives

The `cpus`, `memory` and `time` directives are ignored. Tasks run with whatever resources the Dagger engine provides.

## Troubleshooting

### Enable Debug Logging

Add to your `nextflow.config`:

```groovy
trace {
    enabled = true
    file = 'trace.txt'
}
```

Set Nextflow log level via environment variable:
```bash
export NXF_LOG_LEVEL=DEBUG
nextflow run your-pipeline.nf
```

### Common Issues

#### "Missing container image"

Ensure all processes specify a `container` directive:

```groovy
process {
    container = 'ubuntu:20.04'
}
```

#### "Dagger CLI not found"
Install Dagger CLI and ensure it's in your PATH:

```bash
which dagger
```

Once found you can add it to your PATH at runtime:

```bash
PATH="/opt/homebrew/bin:$PATH" nextflow run your-pipeline.nf -plugins nf-dagger@0.1.0
```

#### "Permission denied" errors
Ensure Docker daemon is running and your user has appropriate permissions.

### Log Files

Check these files for debugging:
- `.command.log`: Dagger CLI output
- `.command.out`: Process stdout
- `.command.err`: Process stderr
- `trace.txt`: Nextflow execution trace

## Development

### Building from Source

```bash
git clone <repository-url>
cd nf-dagger
make assemble
```

### Testing

Test with a simple pipeline:

```bash
# Build and install
make install

# Test with hello world
nextflow run hello -plugins nf-dagger@0.1.0

# Test with custom pipeline
nextflow run test-pipeline.nf -plugins nf-dagger@0.1.0
```

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## Publishing

### Plugin Registry

To publish to the Nextflow Plugin Registry:

1. Create `$HOME/.gradle/gradle.properties`:

   ```properties
   pluginRegistry.accessToken=<your-token>
   ```

2. Create a release:

   ```bash
   make release
   ```

> **Note**: The Nextflow Plugin Registry is currently in private beta. Contact info@nextflow.io for access.

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Support

- **Issues**: Report bugs and feature requests via GitHub Issues
- **Documentation**: See [Nextflow documentation](https://nextflow.io/docs/latest/)
- **Dagger**: See [Dagger documentation](https://docs.dagger.io/)

## Version History

- **0.1.0**: Initial release with basic Dagger container execution
  - Container-based task execution
  - Automatic volume mounting
  - Standard Nextflow integration
  - Known limitation: Parallel task sync conflicts