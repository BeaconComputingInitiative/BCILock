// BEACON COMPUTING INITIATIVE (BCI)
// BCILock Android — Metallic Dashboard v0.3.0
// Developer Seal: CJB-DEV-2026
// Design: Oxanium / Rajdhani / JetBrains Mono; onyx / crimson / cyan
// Security: UI DEMONSTRATION ONLY; no unlock capability is represented as verified.
package org.beaconcomputing.bcilock

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.*
import kotlin.math.min

private object Palette {
    val onyx = Color.rgb(7,10,15)
    val panel = Color.rgb(19,26,34)
    val cyan = Color.rgb(34,224,245)
    val crimson = Color.rgb(242,42,59)
    val silver = Color.rgb(204,225,233)
    val muted = Color.rgb(137,159,171)
    val line = Color.rgb(54,78,91)
}

private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

/** Custom-drawn anodized metal surface, layered bevel, rivets and optical highlights. */
private class MetalDrawable(
    private val context: Context,
    private val accent: Int,
    private val strong: Boolean = false
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Path()
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val left = b.left.toFloat(); val top = b.top.toFloat()
        val right = b.right.toFloat(); val bottom = b.bottom.toFloat()
        val corner = context.dp(11f)
        fun shape(inset: Float): Path {
            val x = left + inset; val y = top + inset
            val xr = right-inset; val yr = bottom-inset
            return Path().apply {
                moveTo(x+corner,y); lineTo(xr-corner,y); lineTo(xr,y+corner)
                lineTo(xr,yr-corner); lineTo(xr-corner,yr); lineTo(x+corner,yr)
                lineTo(x,yr-corner); lineTo(x,y+corner); close()
            }
        }
        paint.shader = LinearGradient(left,top,right,bottom,
            intArrayOf(Color.rgb(55,73,85), Palette.panel, Palette.onyx, Color.rgb(42,52,65)),
            floatArrayOf(0f,.22f,.72f,1f),Shader.TileMode.CLAMP)
        canvas.drawPath(shape(0f),paint); paint.shader = null
        val stroke = context.dp(if(strong) 2f else 1.25f)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=stroke
        paint.color=accent; paint.alpha=if (strong) 230 else 160
        canvas.drawPath(shape(context.dp(1f)),paint)
        paint.style=Paint.Style.FILL; paint.alpha=255
        paint.shader=LinearGradient(left,top,left,bottom,
            intArrayOf(Color.rgb(37,52,66),Color.rgb(13,20,29),Color.rgb(8,12,20)),
            null,Shader.TileMode.CLAMP)
        canvas.drawPath(shape(context.dp(4f)),paint);paint.shader=null
        paint.strokeWidth=context.dp(.8f);paint.color=Color.argb(120,205,236,246)
        paint.style=Paint.Style.STROKE
        canvas.drawLine(left+context.dp(18f),top+context.dp(4f),right-context.dp(18f),top+context.dp(4f),paint)
        paint.color=accent;paint.alpha=180
        canvas.drawLine(left+context.dp(17f),bottom-context.dp(5f),right-context.dp(17f),bottom-context.dp(5f),paint)
        paint.style=Paint.Style.FILL;paint.alpha=255
        for (px in arrayOf(left+context.dp(11f),right-context.dp(11f))) {
            paint.color=Color.rgb(79,105,115)
            canvas.drawCircle(px,top+context.dp(12f),context.dp(1.65f),paint)
            paint.color=Palette.onyx
            canvas.drawCircle(px,top+context.dp(12f),context.dp(.7f),paint)
        }
    }
    override fun setAlpha(alpha: Int) { invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { invalidateSelf() }
    @Deprecated("Android drawable API") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Thin vector overlays — scans, horizon guides, metallic tick marks. */
private class GridOverlay(context: Context): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        p.style=Paint.Style.STROKE;p.strokeWidth=context.dp(.6f)
        p.color=Color.argb(32,38,205,227)
        val step=context.dp(26f)
        var x=0f
        while(x < width){ c.drawLine(x,0f,x,height.toFloat(),p);x+=step }
        var y=0f
        while(y < height){ c.drawLine(0f,y,width.toFloat(),y,p);y+=step }
        p.color=Color.argb(100,240,48,71)
        c.drawLine(context.dp(16f),0f,context.dp(16f),height.toFloat(),p)
        c.drawLine(width-context.dp(16f),0f,width-context.dp(16f),height.toFloat(),p)
    }
}

class MainActivity : Activity() {
    private lateinit var display: Typeface
    private lateinit var ui: Typeface
    private lateinit var mono: Typeface
    private fun font(filename:String,fallback:Typeface):Typeface =
        try { Typeface.createFromAsset(assets,"fonts/$filename") } catch (_:Exception) { fallback }
    private fun label(text:String, sp:Float, color:Int=Palette.silver,face:Typeface=ui):TextView =
        TextView(this).apply {
            this.text=text; textSize=sp;setTextColor(color);typeface=face
            includeFontPadding=false; letterSpacing=.035f
        }
    private fun LinearLayout.addBlock(view:View,height:Int=-2,space:Float=0f) {
        val lp=LinearLayout.LayoutParams(-1,height)
        lp.topMargin=dp(space).toInt(); addView(view,lp)
    }
    private fun info(title:String, body:String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("ACKNOWLEDGE",null)
            .show()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Palette.onyx
        window.navigationBarColor=Palette.onyx
        window.decorView.systemUiVisibility=0
        display=font("oxanium.ttf",Typeface.DEFAULT_BOLD)
        ui=font("rajdhani.ttf",Typeface.DEFAULT)
        mono=font("jetbrainsmono.ttf",Typeface.MONOSPACE)

        val frame=FrameLayout(this).apply {setBackgroundColor(Palette.onyx)}
        frame.addView(GridOverlay(this),FrameLayout.LayoutParams(-1,-1))
        val scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false;overScrollMode=View.OVER_SCROLL_NEVER}
        frame.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18f).toInt(),dp(15f).toInt(),dp(18f).toInt(),dp(25f).toInt())
        }
        scroll.addView(body)

        val eyebrow=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;orientation=LinearLayout.HORIZONTAL}
        eyebrow.addView(label("BEACON COMPUTING INITIATIVE",11f,Palette.cyan,display),LinearLayout.LayoutParams(0,-2,1f))
        val settings=label("⚙",25f,Palette.cyan,display).apply {
            gravity=Gravity.CENTER; setPadding(dp(9f).toInt(),0,dp(9f).toInt(),0)
            contentDescription="Security information"
            setOnClickListener { info("BCILock security", "BCI UI prototype v0.3.0. No trusted challenge-response, unlock endpoint, or live device telemetry has been configured.") }
        }
        eyebrow.addView(settings)
        body.addBlock(eyebrow)

        val hero=FrameLayout(this).apply {background=MetalDrawable(this@MainActivity,Palette.crimson,true)}
        val emblem=ImageView(this).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            try { assets.open("branding/bcilock_eye_lighthouse.png").use { setImageBitmap(BitmapFactory.decodeStream(it)) } } catch (_:Exception) {}
            contentDescription="Red metallic eye with cyan lighthouse and keyhole in the backdrop"
        }
        hero.addView(emblem,FrameLayout.LayoutParams(-1,-1))
        body.addBlock(hero,dp(220f).toInt(),18f)

        val title=label("BCI LOCK",36f,Palette.silver,display).apply {gravity=Gravity.CENTER;letterSpacing=.12f}
        body.addBlock(title,space=13f)
        val subtitle=label("ENDPOINT SECURITY · MOBILE AUTHORIZATION",11f,Palette.cyan,mono).apply {gravity=Gravity.CENTER;letterSpacing=.05f}
        body.addBlock(subtitle,space=5f)
        val divider=View(this).apply {background=android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Palette.onyx,Palette.crimson,Palette.cyan,Palette.crimson,Palette.onyx))}
        body.addBlock(divider,dp(2f).toInt(),18f)

        val endpoint=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(18f).toInt(),dp(18f).toInt(),dp(18f).toInt(),dp(18f).toInt())
            background=MetalDrawable(this@MainActivity,Palette.cyan)
        }
        endpoint.addView(label("⌁",22f,Palette.cyan,display))
        val epDetail=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12f).toInt(),0,0,0)}
        epDetail.addView(label("REGISTERED ENDPOINT",10f,Palette.muted,mono))
        epDetail.addView(label("Enterprise · Kali Linux",19f,Palette.silver,ui))
        endpoint.addView(epDetail,LinearLayout.LayoutParams(0,-2,1f))
        endpoint.addView(label("◈",20f,Palette.cyan,display))
        body.addBlock(endpoint,space=18f)

        fun statusCard(symbol:String,heading:String,state:String,description:String,accent:Int=Palette.cyan) {
            val line=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(17f).toInt(),dp(20f).toInt(),dp(15f).toInt(),dp(20f).toInt())
                background=MetalDrawable(this@MainActivity,accent)
                isClickable=true;isFocusable=true
                setOnClickListener { info(heading,description) }
            }
            val glyph=label(symbol,27f,accent,display).apply {gravity=Gravity.CENTER}
            line.addView(glyph,LinearLayout.LayoutParams(dp(48f).toInt(),-2))
            val details=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(9f).toInt(),0,0,0)}
            details.addView(label(heading,15f,Palette.silver,display))
            details.addView(label("●  $state",12f,accent,mono),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(7f).toInt()})
            line.addView(details,LinearLayout.LayoutParams(0,-2,1f))
            line.addView(label("›",28f,Palette.muted,ui))
            body.addBlock(line,space=11f)
        }
        statusCard("▣","PHONE AUTHENTICATOR","NOT ENROLLED",
            "The previous Bluetooth pairing does not constitute cryptographic application enrollment. Android Keystore enrollment and a local verifier are still required.",Palette.crimson)
        statusCard("⬡","NETWORK GUARD","NOT VERIFIED BY APP",
            "The Kali nftables service has been tested independently. This Android UI has no secure channel to query its live state.")
        statusCard("▱","DESKTOP LOCK","INTEGRATION PENDING",
            "XSecureLock is installed on Kali, but phone-signed authentication and desktop unlock are not yet integrated.",Palette.crimson)

        val action=LinearLayout(this).apply {
            gravity=Gravity.CENTER;orientation=LinearLayout.HORIZONTAL
            background=MetalDrawable(this@MainActivity,Palette.crimson,true)
            setPadding(dp(12f).toInt(),dp(23f).toInt(),dp(12f).toInt(),dp(23f).toInt())
            isClickable=true;isFocusable=true
            setOnClickListener {
                info("AUTHORIZATION NOT AVAILABLE",
                    "This is a design preview. No unlock signal will be sent. Phone-side cryptographic signing and Kali-side verification must be implemented before activation.")
            }
        }
        action.addView(label("⛨",24f,Palette.crimson,display))
        action.addView(label("  AUTHORIZE UNLOCK  ›",19f,Palette.silver,display))
        body.addBlock(action,space=20f)
        body.addBlock(label("DESIGN PREVIEW · AUTHORIZATION DISABLED",10f,Palette.muted,mono).apply {
            gravity=Gravity.CENTER
        },space=14f)
        body.addBlock(label("CJB-DEV-2026   //   v0.3.0",11f,Palette.cyan,mono).apply {
            gravity=Gravity.CENTER
        },space=17f)
        setContentView(frame)
    }
}
