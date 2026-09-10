# Co-lending Control Tower

## 1. What this project is (in one paragraph)

This is a **co-lending reconciliation "control tower"** — a program that checks whether money movements
recorded by three different parties actually agree with each other. In co-lending, a single loan
disbursement shows up in three places: the **originator's** instruction to pay, the **loan management
system (LMS)** booking, and the **bank's** settlement feed. Those three records should tell the same
story, but in the real world they drift apart (wrong amounts, missing entries, reversals, timing gaps,
typos in reference numbers). This program generates realistic synthetic data for all three sources,
lines the records up, finds every mismatch, and produces a single **CLOSE or HOLD** decision that says
whether the day's book can be safely closed or must be held for human review. It keeps full evidence of
every decision so nothing is silently dropped. You do not need a database, Docker, or any cloud account
to run it — everything runs locally and writes plain JSON files to a folder.

---

## 2. Prerequisites

You need exactly two tools installed. The versions below are the ones this project is built and tested with.

| Tool | Version | Why |
|------|---------|-----|
| **JDK (Java Development Kit)** | **17** | The project targets Java 17 (`<java.version>17</java.version>` in `pom.xml`). |
| **Apache Maven** | **3.9.x** (tested with **3.9.9**) | Maven builds the project, runs the tests, and launches the app. There is **no** Maven wrapper (`mvnw`) in this repo, so Maven must be installed separately. |

This project is built on **Spring Boot 3.3.3** (declared in `pom.xml`), which requires Java 17 — an older
Java such as Java 8 or Java 11 **will not work**.

### How to check what you have

Open a terminal (on Windows: press the **Windows key**, type `powershell`, press **Enter**) and run these
two commands. If either one prints an error like *"'java' is not recognized"*, that tool is not installed
yet — go to Section 3.

```powershell
java -version
```

Expected output (the important part is that it says version **17**):

```
openjdk version "17.0.3" 2022-04-19 LTS
OpenJDK Runtime Environment 21.9 (build 17.0.3+6-LTS)
OpenJDK 64-Bit Server VM 21.9 (build 17.0.3+6-LTS, mixed mode, sharing)
```

```powershell
mvn -version
```

Expected output (the important parts are **Apache Maven 3.9.x** and **Java version: 17**):

```
Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)
Maven home: C:\Tools\apache-maven-3.9.9
Java version: 17.0.3, vendor: ojdkbuild, runtime: C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1
Default locale: en_US, platform encoding: Cp1252
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

> The exact vendor string and paths will differ on your machine — that is fine. Only the **version
> numbers** (Java **17**, Maven **3.9.x**) matter.

---

## 3. Installing Java 17 and Maven on Windows from scratch

This section assumes you have **never** installed Java, used a terminal, or edited the `PATH` before.
Follow it top to bottom.

### 3a. Install JDK 17

1. Open a web browser and go to **https://adoptium.net/temurin/releases/?version=17**.
2. Choose **Operating System: Windows**, **Architecture: x64**, **Package Type: JDK**, **Version: 17**.
3. Download the **`.msi`** installer (the file name ends in `.msi`).
4. Double-click the downloaded `.msi` file and click **Next** through the installer.
5. On the **"Custom Setup"** screen, click the drop-down next to **"Set JAVA_HOME variable"** and
   **"Add to PATH"** and choose **"Will be installed on local hard drive"** for both. This lets the
   installer set everything up for you automatically.
6. Click **Install**, approve the Windows permission prompt, and click **Finish**.

That's it — the `.msi` installer edits `PATH` and `JAVA_HOME` for you, so you don't have to.

### 3b. Install Maven 3.9.9

Maven does not have an installer; it comes as a `.zip` folder that you extract and then tell Windows
where to find.

1. Go to **https://maven.apache.org/download.cgi**.
2. Under **"Files"**, download the **Binary zip archive** (file name looks like
   `apache-maven-3.9.9-bin.zip`).
3. In File Explorer, go to your **Downloads** folder, **right-click** the downloaded zip, and choose
   **"Extract All…"**.
4. When asked where to extract, type or paste **`C:\Tools`** and click **Extract**. You should now have a
   folder called **`C:\Tools\apache-maven-3.9.9`** that contains a `bin` folder inside it.

Now add Maven's `bin` folder to your `PATH` so the `mvn` command works from any terminal:

1. Press the **Windows key**, type **`environment variables`**, and click
   **"Edit the system environment variables"**.
2. In the window that opens, click the **"Environment Variables…"** button near the bottom.
3. In the **lower** box titled **"System variables"**, scroll to find the row named **`Path`**, click it
   once to select it, then click **"Edit…"**.
4. Click **"New"** and type exactly:
   ```
   C:\Tools\apache-maven-3.9.9\bin
   ```
5. Click **OK** on all three open windows to save.

### 3c. Confirm it worked

**Close every terminal window you have open**, then open a **new** PowerShell window (PATH changes only
take effect in terminals opened *after* the change — see Troubleshooting). Run the two checks from
Section 2 again:

```powershell
java -version
mvn -version
```

If both print version **17** and **3.9.x** respectively, you're ready.

---

## 4. Getting the project onto your computer

This project is delivered as a folder. There are two ways you might receive it:

**A. You were given the project folder directly (USB drive, zip file, shared drive):**
Copy the whole `co-lending-control-tower` folder somewhere easy to reach, such as your `Downloads`
folder. Then open PowerShell and move into it:

```powershell
cd C:\Users\<your-username>\Downloads\co-lending-control-tower
```

(Replace `<your-username>` with your actual Windows username.)

**B. You have a Git repository URL for it:**

```powershell
git clone <repository-url>
cd co-lending-control-tower
```

> Note: this repository currently has **no remote configured**, so the exact `git clone` URL depends on
> where your team hosts it. If you were just handed the folder, use method **A** — you do not need Git at
> all to build or run this project.

Every command in the rest of this README assumes your terminal is **inside** the
`co-lending-control-tower` folder (the one that contains `pom.xml`).

---

## 5. Running the test suite

The tests prove the reconciliation logic behaves correctly. This is a single command:

```powershell
mvn clean test
```

The first time you run it, Maven downloads its dependencies from the internet, so it may take a couple of
minutes. When it finishes you should see a summary like this (**113 tests, 0 failures, `BUILD SUCCESS`**):

```
[INFO] Tests run: 113, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  24.353 s
```

If you see `BUILD SUCCESS`, everything is working.

---

## 6. Running the full pipeline (one command)

The pipeline is **not** a series of separate steps you run one after another. It is a **single
orchestrated flow** — generate data → ingest → normalize → reconcile → materialize exceptions →
close/hold decision → persist to disk — that runs end to end from **one command**:

```powershell
mvn -q spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=generate --seed 12345"
```

This runs the entire flow, prints the close/hold decision for the featured seed plus a Seed A vs Seed B
comparison, writes durable output files (see Section 8), and then **stops on its own** — you do not need
to press Ctrl+C. Expected tail of the output:

```
Featured seed: 12345
Full-pipeline close/hold decision (generate -> ingest -> normalize -> reconcile -> materialize -> close/hold -> persist):
decision            = HOLD
thresholdInr        = 10000
blockingInr         = 6051643
blockingRefs        = 136
exceptionsPersisted = 158
=== Demonstration complete ===
```

### Using a different seed

The seed is just a number that makes the generated data reproducible. It defaults to `12345` if you
leave `--seed` off. To run the exact same pipeline against a different synthetic dataset, change the
number:

```powershell
mvn -q spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=generate --seed 55555"
```

This produces a different but equally valid run (for example, `decision = HOLD`, `blockingInr = 5447373`,
`exceptionsPersisted = 136` for seed `55555`).

---

## 7. Running the evaluation

The evaluation measures how good the probabilistic matcher is (its precision and recall).

**There is no standalone evaluation command.** The evaluation only runs **inside a test class**. To run
it on its own, run just that one test:

```powershell
mvn test "-Dtest=Phase2EvaluationTest"
```

Expected output:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

This writes the scorecard to **`reports/phase2/phase2-evaluation.txt`** (and a copy under
`target/phase2-evaluation.txt`), which you can open in any text editor.

---

## 8. Where the output goes

After a pipeline run (Section 6), everything is written under the **`data/`** folder. This folder is
created at runtime and is ignored by Git, so on a fresh copy of the project it won't exist until you run
the pipeline once.

```
data/
├── runs/
│   └── <batchFingerprint>/            a long hex folder name, one per unique run
│       ├── canonical-records.json     every reconciled record with its lineage
│       ├── exception-records.json     every mismatch that was found
│       ├── audit-entries.json         the audit trail of decisions
│       └── close-hold-decision.json   the final CLOSE/HOLD verdict for the run
└── demo/
    ├── seed-a/                        the generated feeds for Seed A (12345)
    ├── seed-b/                        the generated feeds for Seed B (67890)
    └── demo-summary.txt               a plain-language human-readable summary
```

The JSON files are already saved in a nicely formatted ("pretty-printed") layout, so you can open them
directly in any text editor. To pretty-print one from the terminal (this also confirms the file is valid
JSON), run — replacing `<batchFingerprint>` with the actual folder name you see under `data\runs\`:

```powershell
Get-Content "data\runs\<batchFingerprint>\close-hold-decision.json" -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 20
```

---

## 9. Troubleshooting

**`'java'` / `'mvn'` is not recognized as ... command**, even though you just installed it.
PATH changes only apply to terminals opened **after** the change. **Close every terminal window and open
a brand-new one**, then try again. If it still fails, re-check the PATH steps in Section 3b.

**`mvn -version` prints a Java version other than 17** (for example Java 8 or Java 11).
You have more than one Java installed and the wrong one is first on the PATH. Reinstall JDK 17 using the
Adoptium `.msi` (Section 3a) with the **"Set JAVA_HOME"** and **"Add to PATH"** options enabled, then
open a new terminal.

**Build fails with a message about an unsupported class file version / wrong Java release.**
This is the same root cause as above — Maven is using a Java older than 17. Confirm `mvn -version` reports
**Java version: 17** and fix the PATH if not.

**`Port 8080 was already in use` / `Web server failed to start`.**
The pipeline briefly starts an embedded web server (Tomcat on port 8080) before it self-terminates. If
another program is already using port 8080, close that program and run the command again.

**The very first `mvn clean test` takes a long time or looks stuck.**
The first run downloads all dependencies from the internet. Make sure you're connected and let it finish;
subsequent runs are much faster.

**PowerShell mangles the command or says a command "is not recognized" right after you paste it.**
Occasionally PowerShell drops the first character of a pasted command. Just press the **Up arrow** to
recall it and run it again, or retype the first word.

---

## 10. Project structure

```
co-lending-control-tower/
├── pom.xml                       Maven build file (Java 17, Spring Boot 3.3.3)
├── README.md                     this file
├── config/
│   └── reconciliation.yml        thresholds, weights, and tolerances for matching/close-hold
├── src/
│   ├── main/java/com/vivriti/controltower/
│   │   ├── close/                the CLOSE vs HOLD decision logic
│   │   ├── demo/                 the one-command pipeline entry point
│   │   ├── domain/               the canonical data model (records, states, enums)
│   │   ├── evaluation/           precision/recall scoring
│   │   ├── exceptions/           mismatch classification + human confirmation workflow
│   │   ├── generator/            the deterministic synthetic-data generator
│   │   ├── hardening/            restart-survivable runners
│   │   ├── ingestion/            reading the raw source feeds
│   │   ├── matching/             exact / composite / timing matching
│   │   ├── normalization/        turning raw feeds into canonical records
│   │   └── probabilistic/        fuzzy (Level 4) matching with a candidacy filter
│   ├── main/resources/           application configuration
│   └── test/java/...             the 113-test suite mirroring the packages above
├── docs/                         design docs (architecture, decisions, phase-2 design, etc.)
└── data/                         created at runtime; durable JSON output (ignored by Git)
```

For deeper design detail, see the `docs/` folder — start with `docs/architecture.md` and
`docs/phase2-design.md`.
