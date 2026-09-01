# Installing KExtractor

This guide covers everything needed to build and run KExtractor locally.

## Prerequisites

| Requirement | Details |
|-------------|---------|
| Java Runtime Environment | JDK 21 or later |
| Build Automation | Apache Maven (required for compilation) |
| Memory Allocation | Minimum 4 GB RAM recommended |
| curl | Required by launch scripts to check server status |

## 1. Clone the Repository

```bash
git clone https://github.com/espositopasquale/KExtractor.git
cd KExtractor
```

## 2. Compile the Executable

Use Maven to compile the source code and generate the executable archive (Fat JAR) encompassing all dependencies:

```bash
mvn clean package
```

Upon successful compilation, the following file will be generated:

```
target/KExtractor-0.1.0.jar
```

## 3. Prepare the Project Root

Copy the JAR into the project root, alongside the launch scripts, and rename it to `kextractor.jar` — this is the filename the launch scripts (`run-mac-linux.sh` / `run-windows.bat`) expect to find in the project root.

```bash
cp target/KExtractor-0.1.0.jar ./kextractor.jar
```

## 4. Verify the Installation

Run a quick direct-string test to confirm everything is working:

```bash
java -Xmx8g -jar target/KExtractor-0.1.0.jar \
  "Linus Torvalds created the Linux kernel in 1991." \
  output.json
```

If the command completes successfully and `output.json` is generated in the current directory, the installation is complete.

### Recommended Heap Settings

| Scenario | Heap Size |
|----------|----------|
| Minimum reliable execution | `-Xmx6g` |
| Default / recommended | `-Xmx8g` |
| Large documents or batch processing | `-Xmx10g` – `-Xmx12g` |

> Tip: Reducing coreference resolution complexity can save 1–2 GB of memory at the cost of some accuracy.

## Next Steps

For full usage syntax, launch script flags, output format examples, and troubleshooting, see the [README](./README.md).