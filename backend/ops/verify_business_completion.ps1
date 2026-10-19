param([ValidateSet('Core', 'Regression', 'Integration', 'Contracts', 'Settings', 'SpaPackages', 'All')][string]$Suite = 'Core')
$ErrorActionPreference = 'Stop'
$taskBackend = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskPom = Join-Path $taskBackend '.business-validation-pom.xml'
$taskBuild = Join-Path $taskBackend 'target-business-validation'
$taskOriginalPom = [System.IO.File]::ReadAllText((Join-Path $taskBackend 'pom.xml'))
$taskContent = $taskOriginalPom.Replace('<build>', "<build>`n<directory>`${project.basedir}/target-business-validation</directory>")
$taskContent = $taskContent.Replace('<excludes>', '<forkCount>0</forkCount><excludes>')
[System.IO.File]::WriteAllText($taskPom, $taskContent, [System.Text.UTF8Encoding]::new($false))
$taskGroups = @{
    Core = 'InventoryFefoStockTest,ProductReturnAmountsTest,SpaFinancialPolicyTest,SpaBusinessCalendarTest,SpaVisitAdvanceTest,AppointmentOperationalRulesTest,AppointmentServiceImplTest,SpaPreparationServiceTest,TreatmentAccessRulesTest,IdentityOrderEventListenerTest,OrderServiceImplTest,OrderFacadeImplTest,ProcurementServiceTest,PaymentSubscriptionTokenTest,StaffPermissionInterceptorTest,CustomerCarePaginationTest,ProfitabilityJdbcTest,SpaPerformanceJdbcTest,BatchRecallTraceJdbcTest'
    Integration = 'CustomerAccountIntegrityIntegrationTest,CrmLoyaltyIntegrityIntegrationTest,CheckoutIntegrityIntegrationTest,SpaVisitExecutionIntegrationTest,SpaVisitCheckoutIntegrityIntegrationTest,SpaConcurrencyIntegrityIntegrationTest,TreatmentCourseIntegrationTest,SpaPackageCompletionIntegrationTest,SpaCatalogIntegrityIntegrationTest,SystemConfigIntegrationTest'
    Contracts = 'OpenApiContractIntegrationTest,ModularArchitectureTest'
    Settings = 'SystemConfigIntegrationTest,OpenApiContractIntegrationTest,ModularArchitectureTest'
    SpaPackages = 'SpaPackageCompletionIntegrationTest,SpaCatalogIntegrityIntegrationTest,SpaAdministrationRulesTest,SpaTicketServiceImplTest,PaidSpaTicketIssuerTest,SpaVisitCheckoutIntegrityIntegrationTest,SpaVisitExecutionIntegrationTest,SpaConcurrencyIntegrityIntegrationTest,TreatmentCourseIntegrationTest,CrmLoyaltyIntegrityIntegrationTest,OpenApiContractIntegrationTest,ModularArchitectureTest'
    Regression = $null # The POM's standard suite excludes live-DB/Docker integration tests.
}
$taskSuites = if ($Suite -eq 'All') { @('Regression', 'Integration', 'Contracts') } else { @($Suite) }
$taskOldMavenOptions = $env:MAVEN_OPTS
Push-Location $taskBackend
try {
    $env:MAVEN_OPTS = '-Xmx512m -XX:MaxMetaspaceSize=256m'
    foreach ($taskName in $taskSuites) {
        Write-Host "Running isolated $taskName checks; output: $taskBuild"
        # Windows PowerShell wraps ordinary JVM stderr warnings as ErrorRecords.
        # Let the native command finish and use Maven's exit code to decide failure.
        $taskPreviousErrorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $taskTestArguments = @()
            if ($taskGroups[$taskName]) { $taskTestArguments += "-Dtest=$($taskGroups[$taskName])" }
            & (Join-Path $taskBackend 'mvnw.cmd') -f $taskPom @taskTestArguments '-Dapi.docs.output=target-business-validation/api-docs' '-Dspring.test.context.cache.maxSize=1' test -q
            $taskMavenExit = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $taskPreviousErrorPreference
        }
        if ($taskMavenExit -ne 0) { throw "$taskName checks failed (exit $taskMavenExit)" }
    }
} finally {
    $env:MAVEN_OPTS = $taskOldMavenOptions
    Pop-Location
}
