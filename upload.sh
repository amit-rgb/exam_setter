# Parameters define karein
$PdfPath = "D:\exam-paper-generator\pdfs\Physics_Chapter_RayOptics.pdf"
$Subject = "Physics"
$Chapter = "Optics"
$SourceType = "TEXTBOOK"
$Endpoint = "http://localhost:8080/api/ingest/pdf"

# File existence check
if (-not (Test-Path $PdfPath)) {
    Write-Error "File not found at path: $PdfPath"
    exit 1
}

Write-Host "Uploading PDF to Ingestion Pipeline..." -ForegroundColor Cyan

# Multipart Form Data payload post karein
$Form = @{
    file = Get-Item -Path $PdfPath
    subject = $Subject
    chapter = $Chapter
    sourceType = $SourceType
}

$Response = Invoke-RestMethod -Uri $Endpoint -Method Post -Form $Form

# Server Response print karein
Write-Host "Upload Status:" -ForegroundColor Green
$Response | ConvertTo-Json -Depth 3