@echo off
rem Windows counterpart of run.sh: the full benchmark matrix — Kormium Native (mingwX64),
rem Kormium JVM, Exposed and Hibernate — against one tuned PostgreSQL container.
rem
rem Prerequisites: Docker Desktop; for the native column, a Windows libpq
rem (MSYS2: pacman -S mingw-w64-x86_64-postgresql, or an EDB PostgreSQL install —
rem see kormium-postgres/src/nativeInterop/cinterop/libpq.def for the searched paths).
rem
rem Usage: benchmarks\run.bat [--quick] [--skip-native] [--skip-jvm]
rem   --quick        fast indicative run — for checking the setup, not for quoting
rem   --skip-native  JVM ORMs only (no libpq needed)
rem   --skip-jvm     native harness only, then re-render the merged summary

setlocal
cd /d "%~dp0.."

set "QUICK="
set "SKIP_NATIVE="
set "SKIP_JVM="
:parse
if "%~1"=="" goto parsed
if "%~1"=="--quick" (set "QUICK=1" & shift & goto parse)
if "%~1"=="--skip-native" (set "SKIP_NATIVE=1" & shift & goto parse)
if "%~1"=="--skip-jvm" (set "SKIP_JVM=1" & shift & goto parse)
echo unknown option: %~1 1>&2
exit /b 1
:parsed

set "PORT=54329"
if defined KORM_BENCH_PORT set "PORT=%KORM_BENCH_PORT%"
set "CONTAINER=kormium-bench-pg"
set "RESULTS_DIR=benchmarks\build\results\jmh"
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

echo ==^> Starting PostgreSQL (tmpfs, durability off) on port %PORT%
docker rm -f %CONTAINER% >nul 2>&1
docker run -d --name %CONTAINER% -e POSTGRES_PASSWORD=password -p %PORT%:5432 --tmpfs /var/lib/postgresql/data postgres:16-alpine postgres -c fsync=off -c synchronous_commit=off -c full_page_writes=off >nul
if errorlevel 1 (
  echo ERROR: failed to start the PostgreSQL container - is Docker Desktop running? 1>&2
  exit /b 1
)

:waitpg
docker exec %CONTAINER% pg_isready -U postgres >nul 2>&1
if errorlevel 1 (
  timeout /t 1 /nobreak >nul
  goto waitpg
)

rem Used by the native harness; the JVM side gets the same values via -Pbench.db.*.
set "KORMIUM_DB_HOST=127.0.0.1"
set "KORMIUM_DB_PORT=%PORT%"
set "KORMIUM_DB_NAME=postgres"
set "KORMIUM_DB_USER=postgres"
set "KORMIUM_DB_PASSWORD=password"

if defined SKIP_NATIVE goto jvm

echo ==^> Building native benchmark binary (mingwX64, release)
call gradlew.bat -q :kormium-postgres:linkBenchReleaseTestMingwX64
if errorlevel 1 (
  echo WARNING: native binary failed to link - is a Windows libpq installed? Continuing JVM-only. 1>&2
  goto jvm
)
set "KEXE=kormium-postgres\build\bin\mingwX64\benchReleaseTest\test.exe"
if not exist "%KEXE%" (
  echo WARNING: %KEXE% not found, skipping the native benchmark 1>&2
  goto jvm
)

echo ==^> Running native benchmark
set "KORM_BENCH=1"
if defined QUICK (set "KORM_BENCH_OPS=500") else (set "KORM_BENCH_OPS=")
set "NATIVE_LOG=%TEMP%\kormium-native-bench.log"
"%KEXE%" --ktest_filter=NativeBenchmark.* > "%NATIVE_LOG%" 2>&1
type "%NATIVE_LOG%"
set "KORM_BENCH="

rem "KORM_NATIVE_RESULT findById=123 ..." -> {"findById": 123, ...}
powershell -NoProfile -Command ^
  "$m = Select-String -Path '%NATIVE_LOG%' -Pattern 'KORM_NATIVE_RESULT' | Select-Object -Last 1;" ^
  "if (-not $m) { exit 1 };" ^
  "$h = [ordered]@{};" ^
  "foreach ($p in (($m.Line -replace '.*KORM_NATIVE_RESULT\s*', '') -split '\s+')) { $kv = $p -split '='; if ($kv.Length -eq 2) { $h[$kv[0]] = [long]$kv[1] } };" ^
  "$h | ConvertTo-Json -Compress | Set-Content -Encoding ascii '%RESULTS_DIR%\native.json'"
if errorlevel 1 (
  echo WARNING: native benchmark produced no KORM_NATIVE_RESULT line 1>&2
) else (
  echo ==^> Native results written to %RESULTS_DIR%\native.json
)

:jvm
if defined SKIP_JVM goto summaryonly
echo ==^> Running JVM benchmarks (kormium / Exposed / Hibernate)
set "JMH_ARGS=:benchmarks:jmh -Pbench.db.host=127.0.0.1 -Pbench.db.port=%PORT% -Pbench.db.name=postgres -Pbench.db.user=postgres -Pbench.db.password=password"
if defined QUICK set "JMH_ARGS=%JMH_ARGS% -Pbench.quick"
call gradlew.bat %JMH_ARGS%
set "EXITCODE=%ERRORLEVEL%"
goto cleanup

:summaryonly
call gradlew.bat :benchmarks:benchmarkSummary
set "EXITCODE=%ERRORLEVEL%"

:cleanup
docker rm -f %CONTAINER% >nul 2>&1
exit /b %EXITCODE%
