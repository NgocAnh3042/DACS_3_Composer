param([string]$BaseUrl='https://chinh.alwaysdata.net/food-delivery-api/api/', [string]$ServiceAccountPath=$env:FOODHUB_FIREBASE_SERVICE_ACCOUNT)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$google=Get-Content -LiteralPath (Join-Path $root 'app\google-services.json') -Raw|ConvertFrom-Json
$apiKey=$google.client[0].api_key[0].current_key
function Api($path,$headers,$method='GET',$body=$null){
    $p=@{Uri=($BaseUrl+$path);Headers=$headers;Method=$method;TimeoutSec=40}
    if($null -ne $body){$p.ContentType='application/json; charset=utf-8';$p.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 8 -Compress))}
    Invoke-RestMethod @p
}
function Expect-Status($path,$status,$method='GET',$body=$null){
    try{Api $path @{} $method $body|Out-Null;throw 'Unexpected successful response'}catch{if([int]$_.Exception.Response.StatusCode -ne $status){throw}}
}
function B64([byte[]]$v){[Convert]::ToBase64String($v).TrimEnd('=').Replace('+','-').Replace('/','_')}
function Json-B64($v){B64 ([Text.Encoding]::UTF8.GetBytes(($v|ConvertTo-Json -Depth 8 -Compress)))}
$health=Api '' @{}
if(-not $health.ok -or $health.database -ne 'connected'){throw 'Health check failed'}
Expect-Status 'users/me' 401
Expect-Status 'payos/webhook' 401 'POST' @{success=$true;data=@{code='00';paymentLinkId='hosting-invalid-signature'};signature='invalid'}
if([string]::IsNullOrEmpty($env:FOODHUB_ADMIN_TEST_PASSWORD)){throw 'Supply the existing test account password in the local process environment'}
$session=Invoke-RestMethod -Method Post -Uri ('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key='+$apiKey) -ContentType 'application/json' -Body (@{email='admin@gmail.com';password=$env:FOODHUB_ADMIN_TEST_PASSWORD;returnSecureToken=$true}|ConvertTo-Json -Compress) -TimeoutSec 30
$headers=@{Authorization='Bearer '+$session.idToken}
$me=(Api 'users/me' $headers).data
if($me.role -ne 'admin'){throw 'Admin role mismatch'}
$users=(Api 'users' $headers).data
$orders=(Api 'orders' $headers).data
$stores=(Api 'stores' $headers).data
$dishes=(Api 'dishes' $headers).data
$null=Api 'vouchers' $headers
$null=Api 'notifications' $headers
$null=Api 'conversations' $headers
try{
    $temporary=$me.fullName+' [hosting check]'
    $changed=(Api 'users/me' $headers 'PATCH' @{name=$temporary}).data
    $readBack=(Api 'users/me' $headers).data
    if($changed.fullName -ne $temporary -or $readBack.fullName -ne $temporary){throw 'Profile write/read failed'}
}finally{
    $restored=(Api 'users/me' $headers 'PATCH' @{name=$me.fullName}).data
    if($restored.fullName -ne $me.fullName){throw 'Profile restore failed'}
}
Write-Output ('PASS admin: health, Firebase sign-in, role, profile write/read/restore; users='+$users.Count+', stores='+$stores.Count+', dishes='+$dishes.Count+', orders='+$orders.Count)
if (-not $ServiceAccountPath) { throw 'Provide ServiceAccountPath for the role checks.' }
$service=Get-Content -LiteralPath $ServiceAccountPath -Raw|ConvertFrom-Json
$rsa=[Security.Cryptography.RSA]::Create();$rsa.ImportFromPem($service.private_key)
try{
    foreach($role in @('user','restaurant','shipper')){
        $account=$users|Where-Object role -eq $role|Select-Object -First 1
        if(-not $account){throw ('No existing identity for '+$role)}
        $now=[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $unsigned=(Json-B64 @{alg='RS256';typ='JWT'})+'.'+(Json-B64 @{iss=$service.client_email;sub=$service.client_email;aud='https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit';iat=$now;exp=($now+600);uid=$account.uid})
        $sig=$rsa.SignData([Text.Encoding]::UTF8.GetBytes($unsigned),[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $login=Invoke-RestMethod -Method Post -Uri ('https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key='+$apiKey) -ContentType 'application/json' -Body (@{token=($unsigned+'.'+(B64 $sig));returnSecureToken=$true}|ConvertTo-Json -Compress) -TimeoutSec 30
        if($login.isNewUser -eq $true){throw 'Unexpected new Firebase identity'}
        $h=@{Authorization='Bearer '+$login.idToken}
        $profile=(Api 'users/me' $h).data
        if($profile.role -ne $role){throw 'Role mapping mismatch'}
        $null=Api 'orders' $h;$null=Api 'stores' $h;$null=Api 'dishes' $h
        if($role -eq 'user'){
            $null=Api 'addresses' $h
            $order=$orders|Where-Object userId -eq $account.uid|Select-Object -First 1
            if($order){$null=Api ('payments/'+$order.id) $h}
        }
        if($role -eq 'restaurant'){$null=Api 'stores/mine' $h}
        if($role -eq 'shipper'){$null=Api 'drivers/me' $h;$null=Api 'orders/available' $h}
        Write-Output ('PASS '+$role+': existing Firebase identity, role, related API reads')
    }
}finally{$rsa.Dispose()}
Write-Output 'PASS access controls: anonymous profile and invalid PayOS signature rejected'
