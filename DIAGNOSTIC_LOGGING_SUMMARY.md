# Diagnostic Logging Summary

## Overview
This document describes the comprehensive diagnostic logging added to help identify and diagnose the `StackOverflowError` that occurs during Android aggregate SBOM generation.

## Problem Statement
When running `:shared:generateAndroidAggregateSbom`, the task fails with:
```
> Task :shared:generateAndroidAggregateSbom FAILED
Generating aggregate SBOM for android
Found 35 modules to include in aggregate SBOM
Collecting dependencies from 541 configurations

FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':shared:generateAndroidAggregateSbom'.
> java.lang.StackOverflowError (no error message)
```

The error occurs after processing 35 modules and 541 configurations, but provides no detailed information about where the stack overflow occurs.

## Added Diagnostic Logging

### 1. Project Dependency Collection (`collectAllProjectDependencies`)

**Purpose:** Track which projects are being collected and identify circular project dependencies.

**New Logging:**
- Start message with project path
- Per-project processing with counter: `"Processing project [N]: <project-path>"`
- Debug logging for each project dependency found
- Debug logging for skipped already-visited projects
- Summary of all collected projects at the end

**Example Output:**
```
Starting to collect project dependencies from: :shared
Processing project [1]: :shared
  Found project dependency: :core (via config: androidReleaseRuntimeClasspath)
  Added new project to queue: :core
Processing project [2]: :core
  Checked 45 resolvable configurations in :core
Finished collecting project dependencies. Total projects found: 2
  Project [1]: :shared
  Project [2]: :core
```

**Benefits:**
- Identifies which project is being processed when the failure occurs
- Reveals if there are circular project references
- Shows progress through the project tree

### 2. Configuration Selection (`findConfigurationsForTarget`)

**Purpose:** Understand which configurations are being selected for each project.

**New Logging:**
- Per-project summary showing: total configs, resolvable configs, matched configs, included configs
- Debug logging for each configuration decision (matched/skipped/included)
- Lifecycle logging for each included configuration

**Example Output:**
```
Finding configurations for target 'android' in project :shared
  Config 'androidDebugCompileClasspath' doesn't match target 'android'
  Config 'androidReleaseRuntimeClasspath' is release - include: true
  Including configuration: androidReleaseRuntimeClasspath from :shared
Config summary for :shared: 120 total, 85 resolvable, 42 matched target, 15 included
```

**Benefits:**
- Shows exactly which configurations are being processed
- Helps identify if too many configurations are being selected
- Reveals configuration filtering logic in action

### 3. Dependency Collection (`DependencyCollector.collectDependencies`)

**Purpose:** Track dependency tree traversal and detect excessive recursion.

**New Logging:**
- Per-configuration progress: `"Processing configuration [N/Total]: <config-name>"`
- Node count and max depth for each completed configuration
- Progress updates every 100 nodes traversed
- **Safety check:** Warning if depth exceeds 1000 (indicating potential circular dependency)
- Detailed dependency processing at shallow depths (< 5)
- Final summary with total unique dependencies and graph entries

**Example Output:**
```
Starting dependency collection from 541 configurations
Processing configuration [1/541]: androidReleaseCompileClasspath
  Collected 234 artifact files from androidReleaseCompileClasspath
  Starting traversal for androidReleaseCompileClasspath
  Traversed 100 nodes so far (current depth: 3)
  Traversed 200 nodes so far (current depth: 4)
  Completed androidReleaseCompileClasspath: 256 nodes, max depth: 6
Processing configuration [2/541]: androidReleaseRuntimeClasspath
  ...
Dependency collection complete: 1523 unique dependencies, 1523 graph entries
```

**Benefits:**
- Identifies which configuration is being processed when failure occurs
- Shows traversal depth to detect abnormally deep dependency trees
- Provides early warning if recursion depth exceeds safe limits
- Tracks progress through large numbers of configurations

### 4. Main Task Execution (`generateAggregateSbom`)

**Purpose:** Provide clear step-by-step progress and catch exceptions with helpful error messages.

**New Logging:**
- Step headers with visual separators (80 equals signs)
- Progress through 8 distinct steps
- Per-project configuration finding progress
- Component creation progress (every 50 components)
- **Exception handling:**
  - Specific `StackOverflowError` handler with diagnostic advice
  - General exception handler with error details

**Example Output:**
```
================================================================================
Step 1: Collecting project dependencies
================================================================================
Starting to collect project dependencies from: :shared
...
================================================================================
Step 2: Collecting configurations from all projects
================================================================================
Finding configs for project [1/35]: :shared
  -> Found 15 configurations
...
================================================================================
Step 3: Collecting dependencies from configurations
================================================================================
Starting dependency collection from 541 configurations
...
```

**Error Handling Output (on StackOverflow):**
```
================================================================================
STACK OVERFLOW ERROR DETECTED!
================================================================================
This typically indicates a circular dependency or excessive recursion.
Please check the log above for the last processed items before failure.
Consider:
  1. Checking for circular project dependencies in your build
  2. Reducing the number of configurations being processed
  3. Breaking circular dependencies between modules
================================================================================
```

**Benefits:**
- Clear indication of which step failed
- Specific guidance for StackOverflowError
- Easy to identify last successfully processed item

## How to Use This Logging

### Running with Diagnostic Output

Run the task with default logging (lifecycle messages visible):
```bash
./gradlew :shared:generateAndroidAggregateSbom
```

For even more detail, run with info or debug logging:
```bash
./gradlew :shared:generateAndroidAggregateSbom --info
./gradlew :shared:generateAndroidAggregateSbom --debug
```

### Analyzing Failures

1. **Look for the last step header** - This tells you which phase failed
2. **Find the last "Processing..." message** - This identifies the specific item being processed
3. **Check for depth warnings** - Look for "WARNING: Excessive traversal depth"
4. **Review configuration counts** - Are there too many configurations? (541 is quite high)
5. **Check project list** - Are there unexpected circular dependencies?

### Common Issues to Look For

1. **Circular project dependencies:**
   - Look for the same project appearing multiple times in the processing log
   - Check if Project A depends on Project B which depends on Project A

2. **Excessive configurations:**
   - 541 configurations is unusually high
   - May indicate too many build variants or flavors
   - Consider filtering more aggressively (e.g., exclude debug configurations)

3. **Deep dependency trees:**
   - Look for "max depth" values > 20
   - May indicate dependency management issues

4. **Specific configuration causing issues:**
   - If failure occurs on a specific configuration, it will be the last one logged
   - Try excluding that configuration type

## Expected Output Format

When the task runs successfully, you should see output like:
```
Generating aggregate SBOM for android
================================================================================
Step 1: Collecting project dependencies
================================================================================
Starting to collect project dependencies from: :shared
...
Found 35 modules to include in aggregate SBOM

================================================================================
Step 2: Collecting configurations from all projects
================================================================================
Finding configs for project [1/35]: :shared
  -> Found 15 configurations
...
Config summary for :shared: 120 total, 85 resolvable, 42 matched, 15 included

Collecting dependencies from 541 configurations

================================================================================
Step 3: Collecting dependencies from configurations
================================================================================
Starting dependency collection from 541 configurations
Processing configuration [1/541]: androidReleaseCompileClasspath
...
[Configuration processing continues...]
...
Dependency collection complete: 1523 unique dependencies, 1523 graph entries

================================================================================
Step 4: Adding platform-specific dependencies
================================================================================
No additional platform-specific dependencies

Total dependencies found: 1523

================================================================================
Step 5: Creating SBOM components
================================================================================
Creating component [1/1523]...
Creating component [50/1523]...
...
Created 1523 components

================================================================================
Step 6: Creating BOM structure
================================================================================
Adding dependency graph with 1523 entries

================================================================================
Step 7: Scanning for vulnerabilities
================================================================================
Scanning for vulnerabilities...
No vulnerabilities found

================================================================================
Step 8: Writing SBOM files
================================================================================
Generated aggregate SBOM: .../build/sbom/aggregate/sbom-android-aggregate.json
Generated aggregate SBOM: .../build/sbom/aggregate/sbom-android-aggregate.xml
Generated aggregate SBOM: .../build/sbom/aggregate/sbom-android-aggregate.md

================================================================================
SBOM generation completed successfully!
================================================================================
```

## Next Steps

After running with this diagnostic logging:

1. **Share the full log output** - Include everything from the start of the task to the failure
2. **Identify the failure point** - Note which step and which specific item was being processed
3. **Look for patterns** - Are there any unusual configurations, projects, or dependencies?
4. **Consider workarounds:**
   - Exclude debug configurations: `includeDebugDependencies = false`
   - Exclude test configurations: `includeTestDependencies = false`
   - Process fewer projects by targeting a more specific module
   - Increase JVM stack size: `org.gradle.jvmargs=-Xss4m` in gradle.properties

## Technical Details

### Safety Mechanisms

1. **Depth limit check:** If traversal depth exceeds 1000, a warning is logged and traversal stops
2. **Visited tracking:** Global visited set prevents infinite loops in circular dependencies
3. **Exception handling:** Catches StackOverflowError and provides actionable advice

### Performance Impact

The logging adds minimal overhead:
- Lifecycle messages: negligible impact
- Debug messages: only visible with --debug flag
- Progress counters: simple increments
- The depth check (every node) adds one comparison per node

### Log Levels Used

- `logger.lifecycle()` - Important progress milestones (always visible)
- `logger.debug()` - Detailed per-item information (only with --debug)
- `logger.warn()` - Warnings about potential issues
- `logger.error()` - Error conditions and failure analysis

## Conclusion

This diagnostic logging transforms the unhelpful "StackOverflowError (no error message)" into a detailed trace that shows:
- Exactly where the failure occurred
- What was being processed at the time
- Progress through each step
- Potential causes and solutions

With this information, we can identify whether the issue is:
- Circular project dependencies
- Excessive configuration count
- Deep dependency trees
- A specific problematic configuration
- Or something else entirely

The next step is to run the task with this logging and analyze the output to determine the root cause and implement an appropriate fix.
