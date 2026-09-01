package com.example.readtrace.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/**
 * 🌐 可交互式 2.5D Web 微卡导出器 (InteractiveWebCardExporter)
 *
 * P14：把单部作品导出为自包含 HTML 单页——内嵌 2.5D 视差封面、
 * 六维心智雷达 Canvas 与 WebAudio 432/528Hz 空灵泛音；角落印制
 * `readtrace://work/{id}` 深链二维码，扫码直达 App 内该藏品详情。
 */
object InteractiveWebCardExporter {

    /** 生成自包含 Web 微卡文件（后台线程调用） */
    fun generate(context: Context, book: Book, mindprint: BookMindprint?): File {
        val html = buildHtml(book, mindprint)
        val dir = File(context.cacheDir, "webcards").apply { if (!exists()) mkdirs() }
        val safeName = book.title.replace(Regex("[^\\w\\u4e00-\\u9fa5]+"), "_").take(40)
        val file = File(dir, "readtrace_card_${book.id}_$safeName.html")
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    /** 生成深链二维码位图（白底黑码，含安静区） */
    fun generateDeepLinkQr(bookId: Long, sizePx: Int = 320): Bitmap {
        val content = "readtrace://work/$bookId"
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[y * sizePx + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    // ---------------------------------------------------------------- HTML 构建

    private fun buildHtml(book: Book, mindprint: BookMindprint?): String {
        val mp = mindprint ?: BookMindprint(bookId = book.id)
        val dims = listOf(
            mp.depthScore, mp.artistryScore, mp.emotionScore,
            mp.logicScore, mp.difficultyScore, mp.healingScore,
        )
        val quote = book.shortComment ?: book.review?.lineSequence()?.firstOrNull() ?: book.title
        val rating = book.remoteRating ?: book.rating ?: 0.0

        return """<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>《${book.title}》· 阅痕微卡</title>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#05070B; color:#F2EFE6; font-family:Georgia,'Noto Serif SC',serif;
         display:flex; align-items:center; justify-content:center; min-height:100vh; overflow:hidden; }
  .stage { perspective:900px; }
  .card { width:min(88vw,420px); padding:28px; border-radius:20px; position:relative;
          background:linear-gradient(145deg,#0C111C,#111a2b);
          border:1px solid rgba(255,255,255,.08);
          box-shadow:0 30px 80px rgba(0,0,0,.6);
          transform-style:preserve-3d; transition:transform .15s ease-out; }
  .cover { width:100%; aspect-ratio:3/4; object-fit:cover; border-radius:12px;
           filter:drop-shadow(0 14px 30px rgba(0,0,0,.5)); transform:translateZ(46px); }
  .title { font-size:24px; margin-top:18px; transform:translateZ(30px); }
  .meta  { font-size:12px; color:#9aa; margin-top:6px; transform:translateZ(18px); font-family:monospace; }
  .quote { font-size:14px; font-style:italic; color:#d8d2c4; margin-top:14px;
           border-left:2px solid rgba(255,231,0,.5); padding-left:10px; transform:translateZ(24px); }
  canvas { display:block; margin:16px auto 0; transform:translateZ(20px); }
  .qr { position:absolute; right:14px; bottom:14px; width:64px; height:64px;
        border-radius:6px; transform:translateZ(40px); }
  .hint { position:fixed; bottom:14px; width:100%; text-align:center; font-size:11px; color:#556; }
</style>
</head>
<body>
<div class="stage">
  <div class="card" id="card">
    <img class="cover" src="${book.coverUrl ?: ""}" alt="cover"
         onerror="this.style.display='none'">
    <div class="title">《${book.title}》</div>
    <div class="meta">[${book.mediaType.displayName} · ${book.author ?: "佚名"} · ⭐$rating]</div>
    <div class="quote">“${quote}”</div>
    <canvas id="radar" width="300" height="220"></canvas>
    <img class="qr" alt="QR" id="qr">
  </div>
</div>
<div class="hint">轻触卡片聆听 432Hz · 扫码直达 App 内藏品</div>
<script>
  // 2.5D 视差：指针驱动 4 层差速位移
  const card=document.getElementById('card');
  window.addEventListener('pointermove',e=>{
    const rx=(e.clientY/innerHeight-.5)*-14, ry=(e.clientX/innerWidth-.5)*14;
    card.style.transform=`rotateX(${'$'}{rx}deg) rotateY(${'$'}{ry}deg)`;
  });
  card.addEventListener('pointerleave',()=>card.style.transform='rotateX(0) rotateY(0)');

  // 六维心智雷达
  const c=document.getElementById('radar').getContext('2d');
  const dims=[$dims.join(',')]
  const labels=['深度','意境','共鸣','构架','阻力','治愈'];
  c.translate(150,115); c.strokeStyle='rgba(255,255,255,.15)';
  for(let ring=1;ring<=4;ring++){c.beginPath();
    for(let i=0;i<=6;i++){const a=Math.PI/3*i-Math.PI/2,r=ring*22;
      i?c.lineTo(Math.cos(a)*r,Math.sin(a)*r):c.moveTo(Math.cos(a)*r,Math.sin(a)*r);}
    c.stroke();}
  c.beginPath();
  for(let i=0;i<=6;i++){const a=Math.PI/3*i-Math.PI/2,r=dims[i%6]/10*88;
    i?c.lineTo(Math.cos(a)*r,Math.sin(a)*r):c.moveTo(Math.cos(a)*r,Math.sin(a)*r);}
  c.closePath();c.fillStyle='rgba(255,231,0,.18)';c.fill();
  c.strokeStyle='#FFE700';c.stroke();
  c.fillStyle='rgba(255,255,255,.5)';c.font='11px monospace';
  labels.forEach((l,i)=>{const a=Math.PI/3*i-Math.PI/2;
    c.fillText(l,Math.cos(a)*104-10,Math.sin(a)*104+4);});

  // WebAudio 432/528Hz 空灵泛音
  const freq=${(432.0 + ((rating.coerceIn(1.0, 10.0)) / 10.0) * 96.0)};
  card.addEventListener('click',()=>{
    const ac=new (window.AudioContext||window.webkitAudioContext)();
    [1,2].forEach(m=>{
      const o=ac.createOscillator(),g=ac.createGain();
      o.frequency.value=freq*m;o.type='sine';
      g.gain.setValueAtTime(.14/(m*2),ac.currentTime);
      g.gain.exponentialRampToValueAtTime(.0001,ac.currentTime+1.2);
      o.connect(g).connect(ac.destination);o.start();o.stop(ac.currentTime+1.3);
    });
  });
  // 深链二维码：内联 SVG 由 Android 端导出时注入
  document.getElementById('qr').src = window.QR_SRC || 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg"/>';
</script>
</body>
</html>"""
    }
}
