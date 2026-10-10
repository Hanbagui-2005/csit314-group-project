param(
    [string]$MavenExecutable = 'mvn',
    [string]$LocalRepository = '',
    [string]$Test = '',
    [switch]$GenerateDataOnly,
    [ValidateRange(100, 10000)][int]$Count = 100,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$qaExitCode = 1
Push-Location $PSScriptRoot
try {
    if ([string]::IsNullOrWhiteSpace($LocalRepository)) {
        $qaUserProfile = [Environment]::GetFolderPath('UserProfile')
        $LocalRepository = Join-Path $qaUserProfile '.m2\repository'
    }
    $qaRepository = [IO.Path]::GetFullPath($LocalRepository)
    $qaMavenArgs = @('-B', "-Dmaven.repo.local=$qaRepository")
    if ($Offline) { $qaMavenArgs += '-o' }

    & $MavenExecutable @qaMavenArgs test-compile
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed. Review Maven output.' }

    if ($GenerateDataOnly) {
        & $MavenExecutable @qaMavenArgs 'org.codehaus.mojo:exec-maven-plugin:3.5.0:java' `
            '-Dexec.classpathScope=test' '-Dexec.mainClass=com.csit314.platform.qa.MockDataGenerator' `
            "-Dexec.args=$Count qa-data"
        $qaExitCode = $LASTEXITCODE
    } else {
        # Read the version selected by the unchanged Spring Boot dependency BOM.
        [xml]$qaPom = Get-Content -LiteralPath 'pom.xml' -Raw
        $qaBootVersion = $qaPom.SelectSingleNode("/*[local-name()='project']/*[local-name()='parent']/*[local-name()='version']").InnerText
        $qaBomPath = Join-Path $qaRepository "org\springframework\boot\spring-boot-dependencies\$qaBootVersion\spring-boot-dependencies-$qaBootVersion.pom"
        [xml]$qaBom = Get-Content -LiteralPath $qaBomPath -Raw
        $qaMockitoVersion = $qaBom.SelectSingleNode("/*[local-name()='project']/*[local-name()='properties']/*[local-name()='mockito.version']").InnerText
        $qaAgent = Join-Path $qaRepository "org\mockito\mockito-core\$qaMockitoVersion\mockito-core-$qaMockitoVersion.jar"
        if (-not (Test-Path -LiteralPath $qaAgent)) { throw 'The resolved Mockito agent JAR was not found.' }

        # JDK 25 instrumentation is enabled only for the test JVM; the POM stays unchanged.
        $qaAgentArgument = '-DargLine="-javaagent:' + $qaAgent + '"'
        $qaTestArgs = @($qaAgentArgument)
        if (-not [string]::IsNullOrWhiteSpace($Test)) { $qaTestArgs += "-Dtest=$Test" }
        & $MavenExecutable @qaMavenArgs @qaTestArgs test
        $qaExitCode = $LASTEXITCODE
    }
} catch {
    Write-Error $_
} finally {
    Pop-Location
}
exit $qaExitCode
