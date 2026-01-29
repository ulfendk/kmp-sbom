# Example Kotlin Multiplatform Project

This is an example project demonstrating the use of the kmp-sbom plugin.

## Building the Project

```bash
./gradlew build
```

## Generating SBOMs

Generate SBOM for all targets:

```bash
./gradlew generateSbom
```

The SBOM files will be generated in `build/sbom/`.

## Viewing SBOM Output

Check the generated SBOM files:

```bash
cat build/sbom/all/sbom-all.json
```

## Project Dependencies

This example project uses:
- kotlinx-coroutines-core
- kotlinx-serialization-json

The generated SBOM will include all transitive dependencies with their licenses and hashes.
