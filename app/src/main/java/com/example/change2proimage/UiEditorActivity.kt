package com.example.change2proimage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton

class UiEditorActivity : BaseActivity() {
    private lateinit var store: UiPreferences
    private lateinit var root: LinearLayout
    private var config = UiConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = UiPreferences(this); config = store.load()
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(32)); setBackgroundColor(Color.parseColor("#FBF7FC")) }
        scroll.addView(root); setContentView(scroll); render()
    }

    private fun render() {
        root.removeAllViews()
        val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        header.addView(MaterialButton(this).apply { text="‹"; textSize=26f; minWidth=0; setOnClickListener{finish()} }, LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(TextView(this).apply { text="界面编辑器"; textSize=25f; setTextColor(Color.parseColor("#241B2B")); setTypeface(typeface,1) }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)
        root.addView(TextView(this).apply { text="只改变界面，不修改 API、模型、任务或生成参数。返回生图页即可看到效果。"; textSize=13f; setTextColor(Color.DKGRAY) }, lp(top=4,bottom=16))
        section("布局预设")
        val presets=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("minimal" to "极简", "compact" to "紧凑", "card" to "卡片").forEach { (key,label) ->
            presets.addView(MaterialButton(this).apply { text=label; isAllCaps=false; setOnClickListener { config=store.preset(key); store.save(config); render() } }, LinearLayout.LayoutParams(0,dp(46),1f).apply{marginEnd=dp(6)})
        }
        root.addView(presets)
        section("尺寸")
        slider("页面边距",0,40,config.pageMarginDp){ config=config.copy(preset="custom",pageMarginDp=it) }
        slider("卡片内边距",4,32,config.cardPaddingDp){ config=config.copy(preset="custom",cardPaddingDp=it) }
        slider("卡片圆角",0,40,config.cardRadiusDp){ config=config.copy(preset="custom",cardRadiusDp=it) }
        slider("组件间距",0,24,config.gapDp){ config=config.copy(preset="custom",gapDp=it) }
        slider("参考图尺寸",48,140,config.referenceSizeDp){ config=config.copy(preset="custom",referenceSizeDp=it) }
        slider("提示词高度",56,220,config.promptHeightDp){ config=config.copy(preset="custom",promptHeightDp=it) }
        slider("生成按钮尺寸",44,96,config.generateSizeDp){ config=config.copy(preset="custom",generateSizeDp=it) }
        slider("字体比例",80,130,config.textScalePercent,"%") { config=config.copy(preset="custom",textScalePercent=it) }
        section("显示内容")
        toggle("显示顶部副标题",config.showSubtitle){config=config.copy(showSubtitle=it)}
        toggle("显示模型读取状态",config.showModelStatus){config=config.copy(showModelStatus=it)}
        toggle("显示尺寸说明",config.showSizeHint){config=config.copy(showSizeHint=it)}
        section("颜色")
        colorField("页面背景",config.backgroundColor){config=config.copy(backgroundColor=it)}
        colorField("卡片颜色",config.cardColor){config=config.copy(cardColor=it)}
        colorField("强调色",config.accentColor){config=config.copy(accentColor=it)}
        section("配置")
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        actions.addView(button("复制配置"){ copyConfig() }, LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(button("粘贴配置"){ pasteConfig() }, LinearLayout.LayoutParams(0,dp(46),1f).apply{marginStart=dp(8)})
        root.addView(actions)
        root.addView(button("恢复极简默认"){ config=store.preset("minimal"); store.save(config); render() }, lp(top=10,height=48))
    }

    private fun section(title:String){ root.addView(TextView(this).apply{text=title;textSize=17f;setTypeface(typeface,1);setTextColor(Color.parseColor("#5F4A72"))},lp(top=18,bottom=8)) }
    private fun slider(label:String,min:Int,max:Int,value:Int,suffix:String="dp",change:(Int)->Unit){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.WHITE)}
        val t=TextView(this).apply{text="$label  $value$suffix";textSize=14f}
        val s=SeekBar(this).apply{this.max=max-min;progress=value-min}
        s.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(b:SeekBar?,p:Int,u:Boolean){val v=p+min;t.text="$label  $v$suffix";change(v);store.save(config)};override fun onStartTrackingTouch(b:SeekBar?){};override fun onStopTrackingTouch(b:SeekBar?){}})
        box.addView(t);box.addView(s);root.addView(box,lp(bottom=6))
    }
    private fun toggle(label:String,checked:Boolean,change:(Boolean)->Unit){root.addView(Switch(this).apply{text=label;isChecked=checked;setPadding(dp(10),0,dp(10),0);setOnCheckedChangeListener{_,v->change(v);store.save(config)}},lp(height=50,bottom=4))}
    private fun colorField(label:String,value:String,change:(String)->Unit){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        row.addView(TextView(this).apply{text=label;textSize=14f},LinearLayout.LayoutParams(0,-2,1f))
        val edit=EditText(this).apply{setText(value);inputType=InputType.TYPE_CLASS_TEXT;setSingleLine(true)}
        row.addView(edit,LinearLayout.LayoutParams(dp(120),dp(48)))
        row.addView(button("应用"){val v=edit.text.toString().trim();runCatching{Color.parseColor(v)}.onSuccess{change(v);store.save(config);Toast.makeText(this,"已应用",Toast.LENGTH_SHORT).show()}.onFailure{edit.error="例如 #7053D6"}},LinearLayout.LayoutParams(dp(72),dp(44)))
        root.addView(row,lp(bottom=6))
    }
    private fun button(label:String,click:()->Unit)=MaterialButton(this).apply{text=label;isAllCaps=false;setOnClickListener{click()}}
    private fun copyConfig(){val cm=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText("酸奶喵 UI",store.toJson(config)));Toast.makeText(this,"UI 配置已复制",Toast.LENGTH_SHORT).show()}
    private fun pasteConfig(){val cm=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;val text=cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty();runCatching{store.fromJson(text)}.onSuccess{config=it;store.save(it);render();Toast.makeText(this,"UI 配置已导入",Toast.LENGTH_SHORT).show()}.onFailure{AlertDialog.Builder(this).setMessage("剪贴板不是有效的酸奶喵 UI 配置。\n${it.message.orEmpty().take(120)}").setPositiveButton("知道了",null).show()}}
    private fun lp(top:Int=0,bottom:Int=0,height:Int=-2)=LinearLayout.LayoutParams(-1,height).apply{topMargin=dp(top);bottomMargin=dp(bottom)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
