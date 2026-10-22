param(
    [ValidateSet("Emulator","Image","All")]
    [string] $Package = "All",
    [int] $Parallel = 8
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$downloadDir = Join-Path $repo ".local\android-test\downloads"
New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
# Some networks block large un-ranged SDK-manager requests. Pull official
# archives with verified byte ranges; keep all downloads inside this repo.
Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

public static class PowerCalAndroidArchive
{
    private const int Chunk = 16 * 1024 * 1024;
    public static void Download(string url, string dest, long length, string expectedSha1, int parallel)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(dest));
        ServicePointManager.DefaultConnectionLimit = 32;
        ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
        if (File.Exists(dest) && new FileInfo(dest).Length == length && ValidHash(dest,expectedSha1)) {
            Console.WriteLine("Already verified: " + Path.GetFileName(dest));
            return;
        }
        var parts = dest + ".parts";
        Directory.CreateDirectory(parts);
        int count = checked((int)((length + Chunk - 1) / Chunk));
        int completed = 0;
        var options = new ParallelOptions { MaxDegreeOfParallelism = Math.Max(1,Math.Min(12,parallel)) };
        Parallel.For(0,count,options,i => {
            long begin = (long)i*Chunk;
            long end = Math.Min(length-1,begin+Chunk-1);
            long size = end-begin+1;
            var path = Path.Combine(parts,i.ToString("D4")+".bin");
            if (!(File.Exists(path) && new FileInfo(path).Length == size)) {
                var stage = path + ".tmp";
                Exception last = null;
                bool good = false;
                for(int attempt=1;attempt<=5 && !good;attempt++) {
                    try {
                        var req = (HttpWebRequest)WebRequest.Create(url);
                        req.AddRange(begin,end);
                        req.Timeout=120000; req.ReadWriteTimeout=120000;
                        req.AutomaticDecompression=DecompressionMethods.None;
                        using(var resp = (HttpWebResponse)req.GetResponse()) {
                            if(resp.StatusCode != HttpStatusCode.PartialContent ||
                               resp.ContentLength != size) {
                                throw new IOException("Unexpected HTTP response/size for part "+i);
                            }
                            using(var input = resp.GetResponseStream())
                            using(var output = new FileStream(stage,FileMode.Create,FileAccess.Write,FileShare.None)) {
                                var buffer=new byte[128*1024];
                                int n;
                                while((n=input.Read(buffer,0,buffer.Length))>0)output.Write(buffer,0,n);
                            }
                        }
                        if(new FileInfo(stage).Length != size)throw new IOException("Incomplete part "+i);
                        if(File.Exists(path))File.Delete(path);
                        File.Move(stage,path);
                        good=true;
                    } catch(Exception e) {
                        last=e;
                        try { if(File.Exists(stage))File.Delete(stage); } catch {}
                        Thread.Sleep(Math.Min(12000,1000*attempt*attempt));
                    }
                }
                if(!good) throw new IOException("Part "+i+" download failed after retries",last);
            }
            int nDone = Interlocked.Increment(ref completed);
            if(nDone % 8 == 0 || nDone == count)
                Console.WriteLine(Path.GetFileName(dest)+": "+nDone+"/"+count+" chunks");
        });
        string merged = dest+".assembling";
        using(var output = new FileStream(merged,FileMode.Create,FileAccess.Write,FileShare.None)) {
            for(int i=0;i<count;i++) {
                using(var input = new FileStream(Path.Combine(parts,i.ToString("D4")+".bin"),FileMode.Open,FileAccess.Read,FileShare.Read))
                    input.CopyTo(output);
            }
        }
        if(new FileInfo(merged).Length != length || !ValidHash(merged,expectedSha1))
            throw new IOException("Archive failed size/SHA-1 verification: "+dest);
        if(File.Exists(dest))File.Delete(dest);
        File.Move(merged,dest);
        Directory.Delete(parts,true);
        Console.WriteLine("Verified SHA-1: "+Path.GetFileName(dest));
    }
    private static bool ValidHash(string filename,string expected) {
        using(var sha=SHA1.Create())
        using(var file=File.OpenRead(filename)) {
            var bytes=sha.ComputeHash(file);
            var actual=BitConverter.ToString(bytes).Replace("-","").ToLowerInvariant();
            return actual==expected.ToLowerInvariant();
        }
    }
}
'@
$archives = @(
    @{ ID="Emulator"; Name="emulator-windows_x64-16428233.zip"; Uri="https://dl.google.com/android/repository/emulator-windows_x64-16428233.zip"; Length=[long]455342868; SHA1="488ed747e82de7e9bb5247becd1ac043c7e5e85d" },
    @{ ID="Image"; Name="x86_64-35_r09.zip"; Uri="https://dl.google.com/android/repository/sys-img/google_apis/x86_64-35_r09.zip"; Length=[long]1738815903; SHA1="0103e6dab21290c4b9d16550a3ce99476f884eef" }
)
foreach($p in $archives) {
    if ($Package -ne "All" -and $Package -ne $p.ID) { continue }
    $path = Join-Path $downloadDir $p.Name
    [PowerCalAndroidArchive]::Download($p.Uri,$path,$p.Length,$p.SHA1,$Parallel)
}
